package com.aitoyz.mapplock.security

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import javax.crypto.Cipher

/**
 * A custom Media3 DataSource that decrypts AES-GCM encrypted files on-the-fly.
 * It uses Cipher.update for true streaming support.
 */
@UnstableApi
class EncryptedDataSource(
    private val cryptoManager: CryptoManager
) : BaseDataSource(false) { // Changed to false (not network)

    private var fileInputStream: FileInputStream? = null
    private var cipher: Cipher? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        try {
            Log.e("EncryptedDataSource", "Opening: ${dataSpec.uri}, position: ${dataSpec.position}, length: ${dataSpec.length}")
            transferInitializing(dataSpec)
            uri = dataSpec.uri
            val file = File(dataSpec.uri.path ?: throw IllegalArgumentException("URI path is null"))
            
            if (!file.exists()) {
                Log.e("EncryptedDataSource", "File does not exist: ${file.absolutePath}")
                throw EOFException("File not found")
            }

            val fis = FileInputStream(file)
            
            // Read IV from the beginning of the file
            val ivSize = fis.read()
            if (ivSize == -1) {
                Log.e("EncryptedDataSource", "Could not read IV size")
                throw EOFException("Could not read IV size")
            }
            
            val iv = ByteArray(ivSize)
            var totalIvRead = 0
            while (totalIvRead < ivSize) {
                val read = fis.read(iv, totalIvRead, ivSize - totalIvRead)
                if (read == -1) {
                    Log.e("EncryptedDataSource", "Could not read full IV")
                    throw EOFException("Could not read full IV")
                }
                totalIvRead += read
            }

            val decryptCipher = cryptoManager.getDecryptCipherForIv(iv)
            this.cipher = decryptCipher
            this.fileInputStream = fis
            
            val headerSize = 1 + ivSize
            val fileSize = file.length()
            // Estimated decrypted content size (fileSize - header - GCM tag 16 bytes)
            val totalContentSize = (fileSize - headerSize - 16).coerceAtLeast(0)

            // Handle seeking
            if (dataSpec.position > 0) {
                Log.e("EncryptedDataSource", "Seeking to: ${dataSpec.position}")
                var skipped: Long = 0
                val skipBuffer = ByteArray(8192)
                val tempEncrypted = ByteArray(8192)
                
                while (skipped < dataSpec.position) {
                    val toRead = minOf(dataSpec.position - skipped, skipBuffer.size.toLong()).toInt()
                    val readEncrypted = fis.read(tempEncrypted, 0, toRead)
                    if (readEncrypted == -1) break
                    
                    val decrypted = decryptCipher.update(tempEncrypted, 0, readEncrypted)
                    if (decrypted != null) {
                        skipped += decrypted.size
                    }
                }
                Log.e("EncryptedDataSource", "Actually skipped $skipped bytes")
            }

            opened = true
            transferStarted(dataSpec)

            bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
                dataSpec.length
            } else {
                totalContentSize - dataSpec.position
            }
            
            Log.e("EncryptedDataSource", "Opened. bytesRemaining: $bytesRemaining")
            return bytesRemaining
        } catch (e: Exception) {
            Log.e("EncryptedDataSource", "Failed to open", e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        try {
            val toRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
                length
            } else {
                minOf(bytesRemaining, length.toLong()).toInt()
            }

            val encryptedBuffer = ByteArray(toRead)
            val readCount = fileInputStream?.read(encryptedBuffer) ?: -1
            
            if (readCount == -1) {
                // End of file, try to finalize cipher
                val finalBytes = try { cipher?.doFinal() } catch (e: Exception) { null }
                if (finalBytes != null && finalBytes.isNotEmpty()) {
                    val bytesToCopy = minOf(finalBytes.size, length)
                    System.arraycopy(finalBytes, 0, buffer, offset, bytesToCopy)
                    bytesRemaining = 0
                    bytesTransferred(bytesToCopy)
                    return bytesToCopy
                }
                return C.RESULT_END_OF_INPUT
            }

            val decrypted = cipher?.update(encryptedBuffer, 0, readCount)
            if (decrypted == null || decrypted.isEmpty()) {
                // Might happen if it's buffering, but for AES GCM it shouldn't
                return 0 
            }

            val bytesToReturn = minOf(decrypted.size, length)
            System.arraycopy(decrypted, 0, buffer, offset, bytesToReturn)
            
            if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
                bytesRemaining -= bytesToReturn
            }
            
            bytesTransferred(bytesToReturn)
            return bytesToReturn
        } catch (e: Exception) {
            Log.e("EncryptedDataSource", "Read error", e)
            return C.RESULT_END_OF_INPUT
        }
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        uri = null
        try {
            fileInputStream?.close()
        } finally {
            fileInputStream = null
            cipher = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }
}
