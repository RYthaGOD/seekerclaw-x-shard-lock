package com.seekerclaw.app.storage

/**
 * JNI Bridge to the Rust-Core cdylib (`librust_core.so`).
 * Exposes high-performance Erasure Coding, Merkle Tree hashing,
 * Ed25519 signing, and Thermal Delta Engine to the Kotlin/Android layer.
 *
 * Ported from seeker-storage (Shard-Lock) project.
 */
class RustCore {

    companion object {
        private const val TAG = "RustCore"
        private var isLoaded = false

        init {
            try {
                System.loadLibrary("rust_core")
                isLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                android.util.Log.e(TAG, "Failed to load librust_core.so: ${e.message}")
            }
        }

        fun isAvailable(): Boolean = isLoaded
    }

    /**
     * Splits input data into data and parity shards via Reed-Solomon erasure coding.
     *
     * @param data The raw data to encode.
     * @param dataShards Number of data pieces.
     * @param parityShards Number of parity (recovery) pieces.
     * @return A 2D array of shards.
     */
    external fun encode(data: ByteArray, dataShards: Int, parityShards: Int): Array<ByteArray>

    /**
     * Computes the SHA-256 Merkle Root of the provided shards.
     *
     * @param shards The 2D array of shards to hash into a tree.
     * @return 32-byte Merkle Root.
     */
    external fun computeMerkleRoot(shards: Array<ByteArray>): ByteArray

    /**
     * Signs the Merkle Root and Shard Count payload using an Ed25519 private key.
     *
     * @param merkleRoot The 32-byte Merkle Root.
     * @param shardCount Total number of shards (data + parity).
     * @param privateKey 32-byte Ed25519 seed/private key.
     * @return 64-byte Ed25519 signature.
     */
    external fun generateHeartbeat(merkleRoot: ByteArray, shardCount: Int, privateKey: ByteArray): ByteArray

    /**
     * Ambient-Aware Thermal Throttling.
     * Delta Threshold: 15°C above ambient.
     *
     * @param chipTemp Current chip temperature in °C.
     * @param ambientTemp Current ambient temperature in °C.
     * @return 0 (Safe), 1 (Throttle/Pause), 2 (Critical Shutdown).
     */
    external fun getThermalStatus(chipTemp: Int, ambientTemp: Int): Int
}
