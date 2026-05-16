package com.geovault.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Professional-grade Encryption Manager using AES-256 GCM.
 * This implementation provides both confidentiality and integrity.
 */
class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getKey(): SecretKey {
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun getEncryptCipher(): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getKey())
        }
    }

    private fun getDecryptCipherForIv(iv: ByteArray): Cipher {
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, getKey(), GCMParameterSpec(128, iv))
        }
    }

    fun encrypt(bytes: ByteArray, outputStream: OutputStream) {
        val cipher = getEncryptCipher()
        val iv = cipher.iv
        outputStream.write(iv.size)
        outputStream.write(iv)
        
        val encryptedBytes = cipher.doFinal(bytes)
        outputStream.write(encryptedBytes)
        outputStream.flush()
        outputStream.close()
    }

    fun encryptStream(inputStream: InputStream, outputStream: OutputStream): Long {
        val cipher = getEncryptCipher()
        val iv = cipher.iv
        outputStream.write(iv.size)
        outputStream.write(iv)

        val cipherOutputStream = javax.crypto.CipherOutputStream(outputStream, cipher)
        val buffer = ByteArray(16384)
        var bytesRead: Int
        var totalBytes: Long = 0
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            cipherOutputStream.write(buffer, 0, bytesRead)
            totalBytes += bytesRead
        }
        cipherOutputStream.close()
        return totalBytes
    }

    fun decrypt(inputStream: InputStream): ByteArray {
        val ivSize = inputStream.read()
        val iv = ByteArray(ivSize)
        inputStream.read(iv)

        val cipher = getDecryptCipherForIv(iv)
        val encryptedBytes = inputStream.readBytes()
        inputStream.close()
        
        return cipher.doFinal(encryptedBytes)
    }

    fun decryptToStream(inputStream: InputStream, outputStream: OutputStream) {
        val ivSize = inputStream.read()
        val iv = ByteArray(ivSize)
        inputStream.read(iv)

        val cipher = getDecryptCipherForIv(iv)
        val cipherInputStream = javax.crypto.CipherInputStream(inputStream, cipher)
        val buffer = ByteArray(16384)
        var bytesRead: Int
        
        while (cipherInputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
        }
        cipherInputStream.close()
        outputStream.flush()
        outputStream.close()
    }

    companion object {
        private const val ALIAS = "geovault_professional_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }
}
