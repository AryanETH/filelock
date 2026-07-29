package com.aitoyz.mapplock.security

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource

/**
 * Factory for [EncryptedDataSource].
 */
@UnstableApi
class EncryptedDataSourceFactory(
    private val cryptoManager: CryptoManager
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return EncryptedDataSource(cryptoManager)
    }
}
