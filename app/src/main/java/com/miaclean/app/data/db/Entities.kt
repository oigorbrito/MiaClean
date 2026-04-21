package com.miaclean.app.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "media_hash",
    indices = [
        Index(value = ["media_id"], unique = true),
        Index(value = ["md5"]),
        Index(value = ["p_hash"]),
    ],
)
data class MediaHashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "media_id") val mediaId: Long,
    @ColumnInfo(name = "uri") val uri: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "mime_type") val mimeType: String,
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long,
    @ColumnInfo(name = "date_taken_ms") val dateTakenMs: Long,
    @ColumnInfo(name = "relative_path") val relativePath: String,
    @ColumnInfo(name = "is_whatsapp") val isWhatsApp: Boolean,
    @ColumnInfo(name = "md5") val md5: String,
    @ColumnInfo(name = "p_hash") val pHash: String?,
    @ColumnInfo(name = "embedding_hash") val embeddingHash: String?,
    @ColumnInfo(name = "last_scanned_ms") val lastScannedMs: Long,
    @ColumnInfo(name = "category", defaultValue = "Other") val category: String = "Other",
)
