package com.miaclean.app.domain.util

class IntDisjointSet(size: Int) {
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

class HammingBkTree {
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
        val distance = hammingDistance(current.hash, candidate.hash, Int.MAX_VALUE)
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
        val distance = hammingDistance(node.hash, hash, Int.MAX_VALUE)
        if (distance <= maxDistance) result += node.indices
        val lower = (distance - maxDistance).coerceAtLeast(0)
        val upper = distance + maxDistance
        for ((edge, child) in node.children) {
            if (edge in lower..upper) {
                searchRecursive(child, hash, maxDistance, result)
            }
        }
    }

    private data class Node(
        val hash: String,
        val indices: MutableList<Int>,
        val children: MutableMap<Int, Node> = mutableMapOf(),
    )
}

fun hammingDistance(left: String, right: String, stopAfter: Int): Int {
    if (left.length != right.length) return Int.MAX_VALUE
    var distance = 0
    for (i in left.indices) {
        if (left[i] != right[i]) distance++
        if (distance > stopAfter) return distance
    }
    return distance
}
