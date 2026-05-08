package com.geovault.model

enum class FileCategory {
    PHOTO, VIDEO, AUDIO, DOCUMENT, INTRUDER, OTHER
}

data class VaultFile(
    val id: String,
    val originalName: String,
    val encryptedPath: String,
    val category: FileCategory,
    val size: Long,
    val addedTimestamp: Long,
    val thumbnailPath: String? = null
)
