// tools/shardlock.js — Shard-Lock decentralized storage tools
// Erasure coding, Merkle proofs, Ed25519 heartbeats via Android Bridge → Rust-Core JNI.
// SQLite shard registry for local discovery. On-chain heartbeat anchoring via Solana Memo.
// Depends on: config.js, bridge.js, database.js, solana.js

const { log, localTimestamp } = require('../config');
const { androidBridgeCall } = require('../bridge');
const { getDb } = require('../database');
const {
    solanaRpc, base58Encode, buildMemoTx,
    ensureWalletAuthorized, getConnectedWalletAddress,
} = require('../solana');

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
                label: {
                    type: 'string',
                    description: 'Optional label to describe what this data is (e.g., "wallet backup", "config snapshot").',
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
        description: 'Get the current Shard-Lock storage status: shard count, total bytes stored, Merkle root list, thermal status, and full shard registry from SQLite.',
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
    {
        name: 'shardlock_anchor',
        description: 'Anchor a Shard-Lock heartbeat proof on the Solana blockchain via a Memo transaction. This creates a publicly verifiable, on-chain record of your storage state. The memo contains the Merkle root, Ed25519 signature, shard count, and timestamp — all independently verifiable. IMPORTANT: This prompts the user to approve the transaction in their wallet.',
        input_schema: {
            type: 'object',
            properties: {
                merkleRoot: {
                    type: 'string',
                    description: 'Optional Merkle root hex to anchor. If omitted, uses the latest stored root.',
                },
            },
            required: [],
        },
    },
];

// ============================================================================
// SQLite HELPERS
// ============================================================================

function upsertShard(merkleRoot, shardCount, dataShards, parityShards, totalBytes, originalSize, label) {
    const db = getDb();
    if (!db) return;
    try {
        db.run(
            `INSERT OR REPLACE INTO shards (merkle_root, shard_count, data_shards, parity_shards, total_bytes, original_size, label, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            [merkleRoot, shardCount, dataShards, parityShards, totalBytes, originalSize, label || null, localTimestamp()]
        );
    } catch (e) {
        log(`[ShardLock] DB upsert error: ${e.message}`, 'WARN');
    }
}

function updateHeartbeat(merkleRoot) {
    const db = getDb();
    if (!db) return;
    try {
        db.run(`UPDATE shards SET last_heartbeat = ? WHERE merkle_root = ?`, [localTimestamp(), merkleRoot]);
    } catch (e) {
        log(`[ShardLock] DB heartbeat update error: ${e.message}`, 'WARN');
    }
}

function updateAnchorTx(merkleRoot, txSignature) {
    const db = getDb();
    if (!db) return;
    try {
        db.run(`UPDATE shards SET anchor_tx = ? WHERE merkle_root = ?`, [txSignature, merkleRoot]);
    } catch (e) {
        log(`[ShardLock] DB anchor update error: ${e.message}`, 'WARN');
    }
}

function deleteShard(merkleRoot) {
    const db = getDb();
    if (!db) return;
    try {
        db.run(`DELETE FROM shards WHERE merkle_root = ?`, [merkleRoot]);
    } catch (e) {
        log(`[ShardLock] DB delete error: ${e.message}`, 'WARN');
    }
}

function getShardRegistry() {
    const db = getDb();
    if (!db) return [];
    try {
        const rows = db.exec(`SELECT merkle_root, shard_count, data_shards, parity_shards, total_bytes, original_size, label, created_at, last_heartbeat, anchor_tx FROM shards ORDER BY created_at DESC`);
        if (!rows.length || !rows[0].values.length) return [];
        return rows[0].values.map(([merkleRoot, shardCount, dataShards, parityShards, totalBytes, originalSize, label, createdAt, lastHeartbeat, anchorTx]) => ({
            merkleRoot,
            shardCount,
            dataShards,
            parityShards,
            totalBytes,
            originalSize: originalSize || 0,
            label: label || null,
            createdAt,
            lastHeartbeat: lastHeartbeat || null,
            anchorTx: anchorTx || null,
        }));
    } catch (e) {
        log(`[ShardLock] DB query error: ${e.message}`, 'WARN');
        return [];
    }
}

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

        // Persist to SQLite registry
        upsertShard(
            result.merkleRoot,
            result.shardCount,
            result.dataShards,
            result.parityShards,
            result.bytesStored,
            data.length,
            input.label || null
        );

        log(`[ShardLock] Stored ${result.shardCount} shards, root: ${result.merkleRoot}`, 'INFO');
        return {
            success: true,
            merkleRoot: result.merkleRoot,
            shardCount: result.shardCount,
            totalShards: result.totalShards,
            dataShards: result.dataShards,
            parityShards: result.parityShards,
            bytesStored: result.bytesStored,
            originalSize: data.length,
            label: input.label || null,
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

        // Update last_heartbeat in SQLite
        updateHeartbeat(result.merkleRoot);

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

        // Merge bridge stats with SQLite registry
        const registry = getShardRegistry();

        return {
            // Bridge stats (live filesystem)
            shardCount: result.shardCount,
            totalBytes: result.totalBytes,
            totalFormatted: result.totalFormatted,
            rustCoreAvailable: result.rustCoreAvailable,
            thermalStatus: result.thermalStatus,
            // SQLite registry (enriched metadata)
            registry,
            registryCount: registry.length,
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

        // Remove from SQLite registry
        deleteShard(merkleRoot.trim());

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

async function handleShardlockAnchor(input) {
    // Step 1: Generate heartbeat (get signature + merkle root)
    let heartbeat;
    try {
        heartbeat = await androidBridgeCall('/shardlock/heartbeat', {
            merkleRoot: input.merkleRoot || '',
        });
        if (heartbeat.error) {
            return { error: `Heartbeat failed: ${heartbeat.error}` };
        }
    } catch (e) {
        return { error: `Heartbeat generation failed: ${e.message}` };
    }

    // Step 2: Get wallet address
    let walletAddress;
    try {
        walletAddress = getConnectedWalletAddress();
    } catch (e) {
        return { error: e.message };
    }

    // Step 3: Build the verifiable memo payload
    // Format: SHARDLOCK|v1|<merkleRoot>|<ed25519Sig>|<shardCount>|<timestamp>|<walletPubkey>
    // Anyone can verify: the Ed25519 sig covers [merkleRoot(32) || shardCount(4 LE)]
    const timestamp = Math.floor(Date.now() / 1000);
    const memoText = [
        'SHARDLOCK',
        'v1',
        heartbeat.merkleRoot,
        heartbeat.signature,
        heartbeat.shardCount.toString(),
        timestamp.toString(),
        walletAddress,
    ].join('|');

    log(`[ShardLock] Anchoring heartbeat on-chain (${memoText.length} bytes)...`, 'INFO');

    // Step 4: Get blockhash
    const blockhashResult = await solanaRpc('getLatestBlockhash', [{ commitment: 'finalized' }]);
    if (blockhashResult.error) return { error: 'Failed to get blockhash: ' + blockhashResult.error };
    const recentBlockhash = blockhashResult.blockhash || (blockhashResult.value && blockhashResult.value.blockhash);
    if (!recentBlockhash) return { error: 'No blockhash returned from RPC' };

    // Step 5: Build unsigned Memo transaction
    let unsignedTx;
    try {
        unsignedTx = buildMemoTx(walletAddress, recentBlockhash, memoText);
    } catch (e) {
        return { error: 'Failed to build Memo transaction: ' + e.message };
    }
    const txBase64 = unsignedTx.toString('base64');

    // Step 6: Send to wallet for signing + broadcast
    await ensureWalletAuthorized();
    const signResult = await androidBridgeCall('/solana/sign', { transaction: txBase64 }, 120000);
    if (signResult.error) return { error: signResult.error };
    if (!signResult.signature) return { error: 'No signature returned from wallet' };

    // Convert base64 signature to base58
    const sigBytes = Buffer.from(signResult.signature, 'base64');
    const sigBase58 = base58Encode(sigBytes);

    // Step 7: Update SQLite with anchor tx
    updateHeartbeat(heartbeat.merkleRoot);
    updateAnchorTx(heartbeat.merkleRoot, sigBase58);

    log(`[ShardLock] Heartbeat anchored on-chain: ${sigBase58}`, 'INFO');

    return {
        success: true,
        transactionSignature: sigBase58,
        merkleRoot: heartbeat.merkleRoot,
        shardCount: heartbeat.shardCount,
        heartbeatSignature: heartbeat.signature,
        timestamp,
        wallet: walletAddress,
        explorerUrl: `https://solscan.io/tx/${sigBase58}`,
        verificationGuide: 'Memo payload format: SHARDLOCK|v1|<merkleRoot>|<ed25519Signature>|<shardCount>|<timestamp>|<wallet>. The Ed25519 signature covers [merkleRoot(32 bytes) || shardCount(4 bytes LE)]. Verify by checking the signature against the signing key derived from the demo seed.',
    };
}

// ============================================================================
// HANDLER MAP
// ============================================================================

const handlers = {
    shardlock_store: handleShardlockStore,
    shardlock_heartbeat: handleShardlockHeartbeat,
    shardlock_status: handleShardlockStatus,
    shardlock_clear: handleShardlockClear,
    shardlock_anchor: handleShardlockAnchor,
};

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = { tools, handlers };
