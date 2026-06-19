package com.miaclean.app.data
import com.miaclean.app.data.adapter.*; import com.miaclean.app.data.classify.MediaClassifier; import com.miaclean.app.domain.*; import kotlinx.coroutines.flow.Flow; import javax.inject.Inject; import javax.inject.Singleton
@Singleton
class ScanRepository @Inject constructor(private val scanner: AndroidMediaScanner, private val md5Hasher: AndroidMd5Hasher, private val perceptualHasher: AndroidPerceptualHasher, private val imageEmbedder: AndroidImageEmbedder, private val classifier: MediaClassifier, private val repository: RoomMediaHashRepository) {
    private val shared = com.miaclean.app.data.ScanRepository(scanner, md5Hasher, perceptualHasher, imageEmbedder, classifier, repository)
    fun scan(additionalSafTreeUris: List<android.net.Uri> = emptyList()): Flow<ScanProgress> = shared.scan()
    suspend fun loadGroups(): List<DuplicateGroup> {
        val exact = repository.findExactDuplicates()
        return exact.groupBy { it.md5 }.values.filter { it.size > 1 }.mapIndexed { index, list -> DuplicateGroup(index.toLong(), DuplicateGroup.Strategy.EXACT_MD5, list.map { it.item }, list.sumOf { it.item.sizeBytes }) }
    }
}
