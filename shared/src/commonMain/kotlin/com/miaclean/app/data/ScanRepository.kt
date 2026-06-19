package com.miaclean.app.data

import com.miaclean.app.data.classify.MediaClassifier
import com.miaclean.app.data.hash.Md5Hasher
import com.miaclean.app.data.hash.PerceptualHasher
import com.miaclean.app.data.ml.ImageEmbedder
import com.miaclean.app.data.repository.MediaHashRepository
import com.miaclean.app.data.scan.MediaScanner
import com.miaclean.app.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

class ScanRepository(
    private val scanner: MediaScanner,
    private val md5Hasher: Md5Hasher,
    private val perceptualHasher: PerceptualHasher,
    private val imageEmbedder: ImageEmbedder,
    private val classifier: MediaClassifier,
    private val repository: MediaHashRepository,
) {
    fun scan(): Flow<ScanProgress> = channelFlow {
        send(ScanProgress.Running(0, 0))
        val items = try {
            scanner.scanAll()
        } catch (e: Exception) {
            send(ScanProgress.Failed(ScanErrorCode.PERMISSION_REVOKED))
            return@channelFlow
        }
        if (items.isEmpty()) {
            send(ScanProgress.Done(0, 0))
            return@channelFlow
        }
        items.forEachIndexed { index, item ->
            try {
                processItem(item)
            } catch (e: Exception) {
                if (e is ScanException) {
                    send(ScanProgress.Failed(e.errorCode))
                    return@channelFlow
                }
            }
            send(ScanProgress.Running(index + 1, items.size))
        }
        val groups = buildGroups()
        send(ScanProgress.Done(groups.sumOf { it.items.size }, groups.size, null))
    }

    private suspend fun processItem(item: MediaItem) {
        if (repository.findByMediaId(item.id) != null) return
        val md5 = md5Hasher.hash(item.uri) ?: throw ScanException(ScanErrorCode.MEDIA_UNAVAILABLE)
        repository.upsert(
            item.copy(category = classifier.classify(item)),
            md5,
            perceptualHasher.hash(item.uri),
            imageEmbedder.embed(item.uri)?.joinToString(",")
        )
    }

    private suspend fun buildGroups(): List<DuplicateGroup> {
        val exact = repository.findExactDuplicates()
        val exactGroups = exact.groupBy { it.md5 }.values.filter { it.size > 1 }.mapIndexed { i, list ->
            DuplicateGroup(i.toLong(), DuplicateGroup.Strategy.EXACT_MD5, list.map { it.item }, list.sumOf { it.item.sizeBytes })
        }
        val pHashRows = repository.findAllWithPHash()
        val exactIds = exact.map { it.item.id }.toSet()
        val pGroups = buildPerceptualGroups(pHashRows.filter { it.item.id !in exactIds }, exactGroups.size)
        val pIds = pGroups.flatMap { g -> g.items.map { it.id } }.toSet()
        val sRows = repository.findAllWithEmbedding()
        val sGroups = buildSemanticGroups(sRows.filter { it.item.id !in exactIds && it.item.id !in pIds }, exactGroups.size + pGroups.size)
        return exactGroups + pGroups + sGroups
    }

    private fun buildPerceptualGroups(rows: List<MediaHash>, offset: Int): List<DuplicateGroup> {
        if (rows.isEmpty()) return emptyList()
        val hashes = rows.mapNotNull { row -> row.pHash?.let { h -> row to h } }
        if (hashes.isEmpty()) return emptyList()
        val ds = IntDisjointSet(hashes.size)
        val tree = HammingBkTree().apply { hashes.forEachIndexed { i, h -> add(h.second, i) } }
        hashes.forEachIndexed { i, h -> tree.search(h.second, 5).forEach { n -> if (n != i) ds.union(i, n) } }
        val components = mutableMapOf<Int, MutableList<MediaHash>>()
        hashes.forEachIndexed { i, h -> components.getOrPut(ds.find(i)) { mutableListOf() } += h.first }
        var nextId = offset.toLong()
        return components.values.filter { it.size > 1 }.map { b ->
            DuplicateGroup(nextId++, DuplicateGroup.Strategy.PERCEPTUAL_PHASH, b.map { it.item }, b.sumOf { it.item.sizeBytes })
        }
    }

    private fun buildSemanticGroups(rows: List<MediaHash>, offset: Int): List<DuplicateGroup> {
        val decoded = rows.mapNotNull { row ->
            row.embeddingHash?.split(",")?.map { s -> s.toFloatOrNull() ?: 0f }?.toFloatArray()?.let { e -> row to e }
        }
        val visited = BooleanArray(decoded.size)
        val groups = mutableListOf<DuplicateGroup>()
        var nextId = offset.toLong()
        for (i in decoded.indices) {
            if (visited[i]) continue
            val bucket = mutableListOf(decoded[i].first)
            visited[i] = true
            for (j in i + 1 until decoded.size) {
                if (!visited[j] && imageEmbedder.cosine(decoded[i].second, decoded[j].second) >= 0.92f) {
                    bucket += decoded[j].first
                    visited[j] = true
                }
            }
            if (bucket.size > 1) {
                groups += DuplicateGroup(nextId++, DuplicateGroup.Strategy.SEMANTIC_EMBED, bucket.map { it.item }, bucket.sumOf { it.item.sizeBytes })
            }
        }
        return groups
    }
}

class ScanException(val errorCode: ScanErrorCode) : Exception()

private class IntDisjointSet(size: Int) {
    private val p = IntArray(size) { it }
    private val r = IntArray(size)
    fun find(x: Int): Int { if (p[x] != x) p[x] = find(p[x]); return p[x] }
    fun union(a: Int, b: Int) {
        var ra = find(a); var rb = find(b); if (ra == rb) return
        if (r[ra] < r[rb]) { val t = ra; ra = rb; rb = t }; p[rb] = ra
        if (r[ra] == r[rb]) r[ra]++
    }
}

private class HammingBkTree {
    private var root: Node? = null
    fun add(h: String, i: Int) { val n = Node(h, mutableListOf(i)); val r = root; if (r == null) root = n else insert(r, n) }
    fun search(h: String, d: Int): List<Int> { val res = mutableListOf<Int>(); searchRecursive(root, h, d, res); return res }
    private fun insert(c: Node, n: Node) { val d = dist(c.h, n.h); if (d == 0) c.i += n.i else { val next = c.c[d]; if (next == null) c.c[d] = n else insert(next, n) } }
    private fun searchRecursive(r: Node?, h: String, d: Int, res: MutableList<Int>) {
        val n = r ?: return; val dist = dist(n.h, h); if (dist <= d) res += n.i
        for ((e, child) in n.c) if (e in (dist - d)..(dist + d)) searchRecursive(child, h, d, res)
    }
    private data class Node(val h: String, val i: MutableList<Int>, val c: MutableMap<Int, Node> = mutableMapOf())
    private fun dist(l: String, r: String): Int { if (l.length != r.length) return 1000; var d = 0; for (i in l.indices) if (l[i] != r[i]) d++; return d }
}
