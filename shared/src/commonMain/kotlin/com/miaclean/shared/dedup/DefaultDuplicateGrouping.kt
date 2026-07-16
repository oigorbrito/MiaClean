package com.miaclean.shared.dedup

import com.miaclean.app.domain.DuplicateGroup
import kotlin.math.sqrt

class DefaultDuplicateGrouping(
    private val semanticSimilarityThreshold: Float = 0.92f,
    private val phashDistanceThreshold: Int = 5
) : DuplicateGrouping {

    override fun group(candidates: List<DedupCandidate>): List<DuplicateGroup> {
        val exactByMd5 = candidates.groupBy { it.md5 }
        val exactGroups = exactByMd5.values
            .filter { it.size > 1 }
            .mapIndexed { index, list ->
                DuplicateGroup(
                    groupId = index.toLong(),
                    strategy = DuplicateGroup.Strategy.EXACT_MD5,
                    items = list.map { it.item },
                    totalBytes = list.sumOf { it.item.sizeBytes }
                )
            }

        val exactIds = exactGroups.flatMap { group -> group.items.map { it.id } }.toSet()
        val perceptualCandidates = candidates.filter { it.item.id !in exactIds && it.pHash != null }

        val perceptualGroups = buildPerceptualGroups(perceptualCandidates, offset = exactGroups.size.toLong())
        val groupedByPerceptual = perceptualGroups.flatMapTo(mutableSetOf()) { group ->
            group.items.map { it.id }
        }

        val semanticCandidates = candidates.filter { candidate ->
            candidate.item.id !in exactIds && candidate.item.id !in groupedByPerceptual && candidate.embeddingHash != null
        }
        val semanticGroups = buildSemanticGroups(
            candidates = semanticCandidates,
            offset = exactGroups.size.toLong() + perceptualGroups.size.toLong()
        )

        return exactGroups + perceptualGroups + semanticGroups
    }

    private fun buildPerceptualGroups(
        candidates: List<DedupCandidate>,
        offset: Long,
    ): List<DuplicateGroup> {
        if (candidates.isEmpty()) return emptyList()

        val disjointSet = IntDisjointSet(candidates.size)
        val tree = HammingBkTree().apply {
            candidates.forEachIndexed { index, candidate -> add(candidate.pHash!!, index) }
        }

        candidates.forEachIndexed { index, candidate ->
            val neighbors = tree.search(candidate.pHash!!, phashDistanceThreshold)
            neighbors.forEach { neighbor ->
                if (neighbor != index) disjointSet.union(index, neighbor)
            }
        }

        val components = linkedMapOf<Int, MutableList<DedupCandidate>>()
        candidates.forEachIndexed { index, candidate ->
            val root = disjointSet.find(index)
            components.getOrPut(root) { mutableListOf() } += candidate
        }

        var nextId = offset
        return components.values
            .asSequence()
            .filter { it.size > 1 }
            .map { bucket ->
                DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.PERCEPTUAL_PHASH,
                    items = bucket.map { it.item },
                    totalBytes = bucket.sumOf { it.item.sizeBytes },
                )
            }
            .toList()
    }

    private fun buildSemanticGroups(
        candidates: List<DedupCandidate>,
        offset: Long,
    ): List<DuplicateGroup> {
        val visited = BooleanArray(candidates.size)
        val groups = mutableListOf<DuplicateGroup>()
        var nextId = offset

        for (i in candidates.indices) {
            if (visited[i]) continue
            val baseCandidate = candidates[i]
            val baseEmbedding = baseCandidate.embeddingHash!!
            val bucket = mutableListOf(baseCandidate)
            visited[i] = true

            for (j in (i + 1) until candidates.size) {
                if (visited[j]) continue
                val candidate = candidates[j]
                val candidateEmbedding = candidate.embeddingHash!!
                if (cosineSimilarity(baseEmbedding, candidateEmbedding) >= semanticSimilarityThreshold) {
                    bucket += candidate
                    visited[j] = true
                }
            }

            if (bucket.size > 1) {
                groups += DuplicateGroup(
                    groupId = nextId++,
                    strategy = DuplicateGroup.Strategy.SEMANTIC_EMBED,
                    items = bucket.map { it.item },
                    totalBytes = bucket.sumOf { it.item.sizeBytes },
                )
            }
        }
        return groups
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}

private class IntDisjointSet(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size)

    fun find(x: Int): Int {
        if (parent[x] != x) parent[x] = find(parent[x])
        return parent[x]
    }

    fun union(a: Int, b: Int) {
        var rootA = find(a)
        var rootB = find(b)
        if (rootA == rootB) return
        if (rank[rootA] < rank[rootB]) {
            val tmp = rootA
            rootA = rootB
            rootB = tmp
        }
        parent[rootB] = rootA
        if (rank[rootA] == rank[rootB]) rank[rootA]++
    }
}

private class HammingBkTree {
    private var root: Node? = null

    fun add(hash: String, index: Int) {
        val node = Node(hash = hash, indices = mutableListOf(index))
        val currentRoot = root
        if (currentRoot == null) {
            root = node
            return
        }
        insert(currentRoot, node)
    }

    fun search(hash: String, maxDistance: Int): List<Int> {
        val result = mutableListOf<Int>()
        searchRecursive(root = root, hash = hash, maxDistance = maxDistance, result = result)
        return result
    }

    private fun insert(current: Node, candidate: Node) {
        val distance = hammingDistance(current.hash, candidate.hash, stopAfter = Int.MAX_VALUE)
        if (distance == 0) {
            current.indices += candidate.indices
            return
        }
        val next = current.children[distance]
        if (next == null) {
            current.children[distance] = candidate
        } else {
            insert(next, candidate)
        }
    }

    private fun searchRecursive(
        root: Node?,
        hash: String,
        maxDistance: Int,
        result: MutableList<Int>,
    ) {
        val node = root ?: return
        val distance = hammingDistance(node.hash, hash, stopAfter = Int.MAX_VALUE)
        if (distance <= maxDistance) result += node.indices
        val lower = (distance - maxDistance).coerceAtLeast(0)
        val upper = distance + maxDistance
        for ((edge, child) in node.children) {
            if (edge in lower..upper) {
                searchRecursive(root = child, hash = hash, maxDistance = maxDistance, result = result)
            }
        }
    }

    private data class Node(
        val hash: String,
        val indices: MutableList<Int>,
        val children: MutableMap<Int, Node> = mutableMapOf(),
    )
}

private fun hammingDistance(left: String, right: String, stopAfter: Int): Int {
    if (left.length != right.length) return Int.MAX_VALUE
    var distance = 0
    for (i in left.indices) {
        if (left[i] != right[i]) distance++
        if (distance > stopAfter) return distance
    }
    return distance
}
