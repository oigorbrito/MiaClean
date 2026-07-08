package com.miaclean.app.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaHashDaoTest {

    private lateinit var db: MiaCleanDatabase
    private lateinit var dao: MediaHashDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MiaCleanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.mediaHashDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun upsert_and_findByMediaId_works_correctly() = runBlocking {
        val entity = createEntity(1L, "hash1")
        dao.upsert(entity)

        val retrieved = dao.findByMediaId(1L)
        assertNotNull(retrieved)
        assertEquals("hash1", retrieved?.md5)
    }

    @Test
    fun deleteByMediaId_removes_entity() = runBlocking {
        val entity = createEntity(1L, "hash1")
        dao.upsert(entity)
        dao.deleteByMediaId(1L)

        val retrieved = dao.findByMediaId(1L)
        assertNull(retrieved)
    }

    @Test
    fun findExactDuplicates_returns_only_duplicates() = runBlocking {
        // Given 3 entities: 2 have the same MD5, 1 is unique
        dao.upsert(createEntity(1L, "duplicate_hash"))
        dao.upsert(createEntity(2L, "duplicate_hash"))
        dao.upsert(createEntity(3L, "unique_hash"))

        // When
        val duplicates = dao.findExactDuplicates()

        // Then it should only return the 2 entities with the duplicate hash
        assertEquals(2, duplicates.size)
        assertTrue(duplicates.all { it.md5 == "duplicate_hash" })
    }

    @Test
    fun findByMediaIds_works_with_chunking_logic() = runBlocking {
        // Insert a few items
        val entities = (1..10L).map { createEntity(it, "hash_$it") }
        dao.upsertAll(entities)

        // Find some subset
        val retrieved = dao.findByMediaIds(listOf(2L, 5L, 7L))
        assertEquals(3, retrieved.size)
        assertTrue(retrieved.any { it.mediaId == 2L })
        assertTrue(retrieved.any { it.mediaId == 5L })
        assertTrue(retrieved.any { it.mediaId == 7L })
    }

    @Test
    fun deleteByMediaIds_works_with_chunking_logic() = runBlocking {
        // Insert a few items
        val entities = (1..5L).map { createEntity(it, "hash_$it") }
        dao.upsertAll(entities)

        // Delete subset
        dao.deleteByMediaIds(listOf(1L, 3L, 5L))

        // Find remaining
        val remaining = dao.findByMediaIds(listOf(1L, 2L, 3L, 4L, 5L))
        assertEquals(2, remaining.size)
        assertTrue(remaining.any { it.mediaId == 2L })
        assertTrue(remaining.any { it.mediaId == 4L })
    }

    private fun createEntity(mediaId: Long, md5: String): MediaHashEntity {
        return MediaHashEntity(
            mediaId = mediaId,
            uri = "content://media/external/file/$mediaId",
            displayName = "file_$mediaId.jpg",
            mimeType = "image/jpeg",
            sizeBytes = 1024L,
            dateTakenMs = 123456789L,
            relativePath = "DCIM/Camera/",
            isWhatsApp = false,
            md5 = md5,
            pHash = null,
            embeddingHash = null,
            lastScannedMs = 123456789L,
            category = "Photo"
        )
    }
}
