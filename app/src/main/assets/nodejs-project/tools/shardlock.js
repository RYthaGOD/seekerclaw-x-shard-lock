// tools/shardlock.js — Shard-Lock decentralized storage tools
// Erasure coding, Merkle proofs, Ed25519 heartbeats via Android Bridge → Rust-Core JNI.
// Depends on: config.js, bridge.js

const { log } = require('../config');
const { androidBridgeCall } = require('../bridge');

// ============================================================================
// TOOL DEFINITIONS
// ============================================================================

const tools = [
    {
        name: 'shardlock_store',
        description: 'Erasure-encode data and store it as shards on-device using Shard-Lock. Returns the Merkle root (storage proof) and shard count. Use this to securely store data with redundancy.',
        input_schema: {
            type: 'object',
            properties: {
                data: {
                    type: 'string',
                    description: 'The data to encode and store (text or base64-encoded binary).',
                },
                dataShards: {
                    type: 'integer',
                    description: 'Number of data shards (default: 4). More shards = smaller pieces.',
                    default: 4,
                },
                parityShards: {
                    type: 'integer',
                    description: 'Number of parity/recovery shards (default: 2). More parity = better fault tolerance.',
                    default: 2,
                },
            },
            required: ['data'],
        },
    },
    {
        name: 'shardlock_heartbeat',
        description: 'Generate a signed Ed25519 heartbeat (storage proof) for the current on-device shard state. This proves the device is actively storing data. Returns a 64-byte signature.',
        input_schema: {
            type: 'object',
            properties: {
                merkleRoot: {
                    type: 'string',
                    description: 'Optional specific Merkle root hex to sign. If omitted, signs the latest stored root.',
                },
            },
            required: [],
        },
    },
    {
        name: 'shardlock_status',
        description: 'Get the current Shard-Lock storage status: shard count, total bytes stored, Merkle root list, and thermal status.',
        input_schema: {
            type: 'object',
            properties: {},
            required: [],
        },
    },
    {
        name: 'shardlock_clear',
        description: 'Clear stored shards for a specific Merkle root. Use after data has been replicated or is no longer needed.',
        input_schema: {
            type: 'object',
            properties: {
                merkleRoot: {
                    type: 'string',
                    description: 'The Merkle root hex of the shard set to clear.',
                },
            },
            required: ['merkleRoot'],
        },
    },
];

// ============================================================================
// TOOL HANDLERS
// ============================================================================

async function handleShardlockStore(input) {
    const data = input.data;
    if (!data || typeof data !== 'string' || data.trim().length === 0) {
        return { error: 'data is required and must be a non-empty string.' };
    }

    const dataShards = input.dataShards || 4;
    const parityShards = input.parityShards || 2;

    if (dataShards < 1 || dataShards > 255) {
        return { error: 'dataShards must be between 1 and 255.' };
    }
    if (parityShards < 1 || parityShards > 255) {
        return { error: 'parityShards must be between 1 and 255.' };
    }

    log(`[ShardLock] Encoding ${data.length} bytes into ${dataShards}+${parityShards} shards...`, 'INFO');

    try {
        const result = await androidBridgeCall('/shardlock/encode', {
            data,
            dataShards,
            parityShards,
        });

        if (result.error) {
            return { error: `Shard-Lock encode failed: ${result.error}` };
        }

        log(`[ShardLock] Stored ${result.shardCount} shards, root: ${result.merkleRoot}`, 'INFO');
        return {
            success: true,
            merkleRoot: result.merkleRoot,
            shardCount: result.shardCount,
            totalShards: result.totalShards,
            dataShards: result.dataShards,
            parityShards: result.parityShards,
            bytesStored: result.bytesStored,
        };
    } catch (e) {
        log(`[ShardLock] Store error: ${e.message}`, 'ERROR');
        return { error: `Shard-Lock store failed: ${e.message}` };
    }
}

async function handleShardlockHeartbeat(input) {
    try {
        const result = await androidBridgeCall('/shardlock/heartbeat', {
            merkleRoot: input.merkleRoot || '',
        });

        if (result.error) {
            return { error: `Heartbeat failed: ${result.error}` };
        }

        log(`[ShardLock] Heartbeat signed for root: ${result.merkleRoot}`, 'INFO');
        return {
            success: true,
            merkleRoot: result.merkleRoot,
            shardCount: result.shardCount,
            signature: result.signature,
            signatureLength: result.signatureLength,
            timestamp: result.timestamp,
        };
    } catch (e) {
        log(`[ShardLock] Heartbeat error: ${e.message}`, 'ERROR');
        return { error: `Heartbeat generation failed: ${e.message}` };
    }
}

async function handleShardlockStatus() {
    try {
        const result = await androidBridgeCall('/shardlock/status', {});

        if (result.error) {
            return { error: `Status query failed: ${result.error}` };
        }

        return {
            shardCount: result.shardCount,
            totalBytes: result.totalBytes,
            totalFormatted: result.totalFormatted,
            merkleRoots: result.merkleRoots,
            rootCount: result.rootCount,
            rustCoreAvailable: result.rustCoreAvailable,
            thermalStatus: result.thermalStatus,
        };
    } catch (e) {
        log(`[ShardLock] Status error: ${e.message}`, 'ERROR');
        return { error: `Status query failed: ${e.message}` };
    }
}

async function handleShardlockClear(input) {
    const merkleRoot = input.merkleRoot;
    if (!merkleRoot || typeof merkleRoot !== 'string' || merkleRoot.trim().length === 0) {
        return { error: 'merkleRoot is required.' };
    }

    try {
        const result = await androidBridgeCall('/shardlock/clear', {
            merkleRoot: merkleRoot.trim(),
        });

        if (result.error) {
            return { error: `Clear failed: ${result.error}` };
        }

        log(`[ShardLock] Cleared ${result.deletedCount} shards for root: ${merkleRoot}`, 'INFO');
        return {
            success: true,
            merkleRoot: merkleRoot.trim(),
            deletedCount: result.deletedCount,
        };
    } catch (e) {
        log(`[ShardLock] Clear error: ${e.message}`, 'ERROR');
        return { error: `Clear failed: ${e.message}` };
    }
}

// ============================================================================
// HANDLER MAP
// ============================================================================

const handlers = {
    shardlock_store: handleShardlockStore,
    shardlock_heartbeat: handleShardlockHeartbeat,
    shardlock_status: handleShardlockStatus,
    shardlock_clear: handleShardlockClear,
};

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = { tools, handlers };
