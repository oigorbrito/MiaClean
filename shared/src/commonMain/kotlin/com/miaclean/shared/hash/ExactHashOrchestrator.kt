package com.miaclean.shared.hash

/**
 * Orchestrates exact file hashing for media sources.
 * Encapsulates the execution logic and translates results into domain-friendly types.
 */
class ExactHashOrchestrator(private val hasher: Hasher) {

    sealed class Result {
        data class Success(val hash: String) : Result()
        data class Failure(val message: String, val exception: Throwable? = null) : Result()
    }

    /**
     * Calculates the exact hash of the given [source].
     * Returns [Result.Success] on success, and [Result.Failure] on failure or error.
     */
    fun calculateHash(source: MediaSource): Result {
        return try {
            val hash = hasher.hash(source)
            if (hash != null) {
                Result.Success(hash)
            } else {
                Result.Failure("Hasher returned null digest for source: ${source.identifier}")
            }
        } catch (e: Exception) {
            Result.Failure(e.message ?: "Unknown error occurred during hashing", e)
        }
    }
}
