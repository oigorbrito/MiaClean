package com.miaclean.app.data.dedupe

/**
 * Union-Find (Disjoint Set Union) with path compression and union by rank.
 * Used by [PerceptualGrouperImpl] to cluster near-duplicate pHashes.
 *
 * Pure Kotlin — no Android or platform dependencies.
 */
internal class IntDisjointSet(size: Int) {
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
            val tmp = rootA; rootA = rootB; rootB = tmp
        }
        parent[rootB] = rootA
        if (rank[rootA] == rank[rootB]) rank[rootA]++
    }
}

/**
 * BK-Tree indexing pHash strings by Hamming distance.
 * Allows sub-linear nearest-neighbor search for perceptual duplicate detection.
 *
 * Pure Kotlin — no Android or platform dependencies.
 */
internal class HammingBkTree {
    private var root: Node? = null

    fun add(hash: String, index: Int) {
        val node = Node(hash = hash, indices = mutableListOf(index))
        val currentRoot = root
        if (currentRoot == null) { root = node; return }
        insert(currentRoot, node)
    }

    fun search(hash: String, maxDistance: Int): List<Int> {
        val result = mutableListOf<Int>()
        searchRecursive(root = root, hash = hash, maxDistance = maxDistance, result = result)
        return result
    }

    private fun insert(current: Node, candidate: Node) {
        val distance = hammingDistance(current.hash, candidate.hash)
        if (distance == 0) { current.indices += candidate.indices; return }
        val next = current.children[distance]
        if (next == null) current.children[distance] = candidate else insert(next, candidate)
    }

    private fun searchRecursive(root: Node?, hash: String, maxDistance: Int, result: MutableList<Int>) {
        val node = root ?: return
        val distance = hammingDistance(node.hash, hash)
        if (distance <= maxDistance) result += node.indices
        val lower = (distance - maxDistance).coerceAtLeast(0)
        val upper = distance + maxDistance
        for ((edge, child) in node.children) {
            if (edge in lower..upper) searchRecursive(child, hash, maxDistance, result)
        }
    }

    private data class Node(
        val hash: String,
        val indices: MutableList<Int>,
        val children: MutableMap<Int, Node> = mutableMapOf(),
    )
}

/**
 * Character-by-character Hamming distance between two equal-length strings.
 * Returns [Int.MAX_VALUE] when lengths differ (callers treat this as "not similar").
 */
internal fun hammingDistance(left: String, right: String): Int {
    if (left.length != right.length) return Int.MAX_VALUE
    var distance = 0
    for (i in left.indices) { if (left[i] != right[i]) distance++ }
    return distance
}

/**
 * Encodes a float embedding as a compact comma-separated string.
 * Format: 5 decimal places per value, '.' decimal separator (locale-invariant).
 */
internal fun encodeEmbedding(values: FloatArray): String =
    buildString {
        values.forEachIndexed { index, v ->
            if (index > 0) append(',')
            // Locale-invariant: manual formatting avoids java.util.Locale (not in commonMain)
            val truncated = (v * 100000).toLong()
            val intPart = truncated / 100000
            val fracPart = kotlin.math.abs(truncated % 100000)
            append(intPart)
            append('.')
            val fracStr = fracPart.toString().padStart(5, '0')
            append(fracStr)
        }
    }

/**
 * Decodes a comma-separated embedding string back to [FloatArray].
 * Invalid tokens are treated as 0f (defensive; should not occur in practice).
 */
internal fun decodeEmbedding(serialized: String): FloatArray {
    val parts = serialized.split(',')
    return FloatArray(parts.size) { index -> parts[index].toFloatOrNull() ?: 0f }
}
