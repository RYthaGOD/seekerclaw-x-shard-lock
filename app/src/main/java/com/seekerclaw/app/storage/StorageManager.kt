package com.seekerclaw.app.storage

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

/**
 * Handles saving and retrieving erasure-encoded Shards locally on-device.
 * Each shard file is prefixed by its Merkle root hex for retrieval.
 *
 * Ported from seeker-storage (Shard-Lock) project.
 */
class StorageManager(private val context: Context) {

    companion object {
        private const val TAG = "StorageManager"
        private const val SHARD_DIR_NAME = "shardlock"
    }

    private val shardDir: File
        get() {
            val dir = File(context.filesDir, SHARD_DIR_NAME)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Saves a 2D array of encoded shards to internal storage.
     * Also saves a small .meta JSON file to allow registry re-indexing.
     */
    fun saveShards(merkleRootHex: String, shards: Array<ByteArray>, dataShards: Int, parityShards: Int, originalSize: Int): Boolean {
        return try {
            for (i in shards.indices) {
                val shardFile = File(shardDir, "${merkleRootHex}_shard_$i")
                shardFile.writeBytes(shards[i])
            }
            
            // Save metadata for re-indexing
            val metaFile = File(shardDir, "${merkleRootHex}.meta")
            val meta = org.json.JSONObject()
            meta.put("dataShards", dataShards)
            meta.put("parityShards", parityShards)
            meta.put("totalShards", shards.size)
            meta.put("originalSize", originalSize)
            meta.put("createdAt", System.currentTimeMillis())
            metaFile.writeText(meta.toString())

            Log.d(TAG, "Saved ${shards.size} shards + meta for root $merkleRootHex")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save shards: ${e.message}")
            false
        }
    }

    /**
     * Retrieves all locally stored shards for a specific Merkle Root prefix.
     *
     * @param merkleRootHex The hex string prefix.
     * @param totalShards How many shards to look for.
     * @return 2D Array of Shard data, or null if any shard is missing.
     */
    fun retrieveShards(merkleRootHex: String, totalShards: Int): Array<ByteArray>? {
        val result = Array(totalShards) { ByteArray(0) }
        return try {
            for (i in 0 until totalShards) {
                val shardFile = File(shardDir, "${merkleRootHex}_shard_$i")
                if (!shardFile.exists()) {
                    Log.w(TAG, "Missing shard $i for root $merkleRootHex")
                    return null
                }
                result[i] = shardFile.readBytes()
            }
            result
        } catch (e: IOException) {
            Log.e(TAG, "Failed to retrieve shards: ${e.message}")
            null
        }
    }

    /**
     * Clears local shards for a given Merkle root prefix.
     */
    fun clearShards(merkleRootHex: String): Int {
        val files = shardDir.listFiles { _, name -> name.startsWith(merkleRootHex) }
        var deleted = 0
        files?.forEach {
            if (it.delete()) deleted++
        }
        Log.d(TAG, "Cleared $deleted shards for root $merkleRootHex")
        return deleted
    }

    /**
     * Returns storage statistics: total shard count, total bytes, and list of unique Merkle roots.
     */
    fun getStats(): Map<String, Any> {
        val files = shardDir.listFiles() ?: emptyArray()
        val totalBytes = files.sumOf { it.length() }
        val roots = files.filter { !it.name.endsWith(".meta") }
            .map { it.name.substringBefore("_shard_") }.distinct()
        return mapOf(
            "shardCount" to files.size,
            "totalBytes" to totalBytes,
            "totalFormatted" to formatBytes(totalBytes),
            "merkleRoots" to roots,
            "rootCount" to roots.size,
        )
    }

    /**
     * Returns the number of shards stored for a specific Merkle root.
     */
    fun getShardCount(merkleRootHex: String): Int {
        val files = shardDir.listFiles { _, name -> name.startsWith("${merkleRootHex}_shard_") }
        return files?.size ?: 0
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
