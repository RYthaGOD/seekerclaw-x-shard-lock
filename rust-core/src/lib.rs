use jni::objects::JClass;
use jni::sys::{jbyteArray, jint, jobjectArray};
use jni::JNIEnv;

/// JNI Function to Erasure Encode file bytes into Shards
/// Kotlin: `external fun encode(data: ByteArray, dataShards: Int, parityShards: Int): Array<ByteArray>`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_seekerclaw_app_storage_RustCore_encode<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    data: jbyteArray,
    data_shards: jint,
    parity_shards: jint,
) -> jobjectArray {
    let data_array = unsafe { jni::objects::JByteArray::from_raw(data) };
    let data_bytes = env.convert_byte_array(&data_array).unwrap_or_default();
    let total_shards = (data_shards + parity_shards) as usize;

    // 1. Setup Reed-Solomon (Standard stable API)
    let rs = reed_solomon_erasure::galois_8::ReedSolomon::new(data_shards as usize, parity_shards as usize).unwrap();
    
    // 2. Pad data to be divisible by shard sizes
    let shard_size = (data_bytes.len() + (data_shards as usize - 1)) / (data_shards as usize);
    let mut padded_data = vec![0u8; shard_size * (data_shards as usize)];
    padded_data[..data_bytes.len()].copy_from_slice(&data_bytes);

    // 3. Construct Shards buffer
    let mut shard_buffers = vec![vec![0u8; shard_size]; total_shards];
    for (i, chunk) in padded_data.chunks(shard_size).enumerate() {
        if i < data_shards as usize {
            shard_buffers[i].copy_from_slice(chunk);
        }
    }

    // 4. Encode Parity
    let mut shard_refs: Vec<&mut [u8]> = shard_buffers.iter_mut().map(|v| v.as_mut_slice()).collect();
    rs.encode(&mut shard_refs).unwrap();

    // 5. Build Return Object Array (Array<ByteArray>)
    let byte_array_class = env.find_class("[B").unwrap();
    let result_array = env.new_object_array(total_shards as jint, &byte_array_class, env.new_byte_array(0).unwrap()).unwrap();

    for (i, shard) in shard_buffers.iter().enumerate() {
        let j_array = env.new_byte_array(shard.len() as jint).unwrap();
        env.set_byte_array_region(&j_array, 0, bytemuck::cast_slice(shard)).unwrap();
        env.set_object_array_element(&result_array, i as jint, &j_array).unwrap();
    }

    result_array.into_raw()
}

/// JNI Function to Compute Merkle Root from Shards
/// Kotlin: `external fun computeMerkleRoot(shards: Array<ByteArray>): ByteArray`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_seekerclaw_app_storage_RustCore_computeMerkleRoot<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    shards: jobjectArray,
) -> jbyteArray {
    let shards_array = unsafe { jni::objects::JObjectArray::from_raw(shards) };
    let total_shards = env.get_array_length(&shards_array).unwrap();
    let mut leaves: Vec<[u8; 32]> = Vec::with_capacity(total_shards as usize);

    for i in 0..total_shards {
        let shard_obj = env.get_object_array_element(&shards_array, i).unwrap();
        // Cast jobject to jbyteArray
        let shard_array: jni::objects::JByteArray = shard_obj.into();
        let shard_bytes = env.convert_byte_array(&shard_array).unwrap_or_default();

        // Hash shard using Sha256
        use sha2::Digest;
        let mut hasher = sha2::Sha256::new();
        hasher.update(&shard_bytes);
        let hash = hasher.finalize();
        
        let mut leaf = [0u8; 32];
        leaf.copy_from_slice(&hash);
        leaves.push(leaf);
    }

    // Compute Merkle Tree
    let tree = rs_merkle::MerkleTree::<rs_merkle::algorithms::Sha256>::from_leaves(&leaves);
    let root = tree.root().unwrap_or_default();

    let j_root = env.new_byte_array(32).unwrap();
    env.set_byte_array_region(&j_root, 0, bytemuck::cast_slice(&root)).unwrap();

    j_root.into_raw()
}

/// JNI Function to Sign Heartbeat with Ed25519 Device Key
/// Kotlin: `external fun generateHeartbeat(merkleRoot: ByteArray, shardCount: Int, privateKey: ByteArray): ByteArray`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_seekerclaw_app_storage_RustCore_generateHeartbeat<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    merkle_root: jbyteArray,
    shard_count: jint,
    private_key: jbyteArray,
) -> jbyteArray {
    let m_array = unsafe { jni::objects::JByteArray::from_raw(merkle_root) };
    let root_bytes = env.convert_byte_array(&m_array).unwrap_or_default();

    let k_array = unsafe { jni::objects::JByteArray::from_raw(private_key) };
    let key_bytes = env.convert_byte_array(&k_array).unwrap_or_default();

    if root_bytes.len() != 32 || key_bytes.len() < 32 {
        return env.new_byte_array(0).unwrap().into_raw(); // invalid inputs
    }

    // Ed25519 Signing
    use ed25519_dalek::{Signer, SigningKey};
    let mut seed = [0u8; 32];
    seed.copy_from_slice(&key_bytes[..32]);
    let signing_key = SigningKey::from_bytes(&seed);

    // Pack message: [MerkleRoot (32)] || [ShardCount (4 - Little Endian)]
    let mut message = Vec::with_capacity(36);
    message.extend_from_slice(&root_bytes);
    message.extend_from_slice(&(shard_count as u32).to_le_bytes());

    let signature = signing_key.sign(&message);
    let sig_bytes = signature.to_bytes();

    let j_sig = env.new_byte_array(64).unwrap();
    env.set_byte_array_region(&j_sig, 0, bytemuck::cast_slice(&sig_bytes)).unwrap();

    j_sig.into_raw()
}

/// JNI Function for Ambient-Aware Thermal Throttling
/// Delta Threshold: 15°C above ambient.
/// Returns: 0 (Safe), 1 (Throttle/Pause), 2 (Critical Shutdown)
/// Kotlin: `external fun getThermalStatus(chipTemp: Int, ambientTemp: Int): Int`
#[unsafe(no_mangle)]
pub extern "system" fn Java_com_seekerclaw_app_storage_RustCore_getThermalStatus<'local>(
    _env: JNIEnv<'local>,
    _class: JClass<'local>,
    chip_temp: jint,
    ambient_temp: jint,
) -> jint {
    let delta = chip_temp - ambient_temp;

    if delta > 25 {
        2 // Critical: Shutdown immediately to protect battery/silicon
    } else if delta > 15 {
        1 // Throttle: Pause high-intensity compute
    } else {
        0 // Safe: Continue execution
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use sha2::Digest;
    use ed25519_dalek::Signer;

    #[test]
    fn test_merkle_and_sign() {
        // 1. Basic Merkle Tree verification
        let shard = b"hello shard";
        let mut hasher = sha2::Sha256::new();
        hasher.update(shard);
        let hash = hasher.finalize();
        let mut leaf = [0u8; 32];
        leaf.copy_from_slice(&hash);
        
        let leaves = vec![leaf];
        let tree = rs_merkle::MerkleTree::<rs_merkle::algorithms::Sha256>::from_leaves(&leaves);
        let root = tree.root().unwrap();
        assert_eq!(root.len(), 32);

        // 2. Ed25519 Signing verification
        let signing_key = ed25519_dalek::SigningKey::from_bytes(&[1u8; 32]);
        let mut message = Vec::new();
        message.extend_from_slice(&root);
        message.extend_from_slice(&5u32.to_le_bytes());
        
        let signature = signing_key.sign(&message);
        assert_eq!(signature.to_bytes().len(), 64);
    }
}
