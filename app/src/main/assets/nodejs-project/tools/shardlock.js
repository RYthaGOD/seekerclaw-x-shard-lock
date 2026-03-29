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
    {
        name: 'shardlock_retrieve',
        description: 'Retrieve and reconstruct the original data from stored shards. Returns the reconstructed data string.',
        input_schema: {
            type: 'object',
            properties: {
                merkleRoot: {
                    type: 'string',
                    description: 'The Merkle root hex of the shard set to retrieve.',
                },
            },
            required: ['merkleRoot'],
        },
    },
    {
        name: 'shardlock_verify',
        description: 'Verify the local integrity of stored shards for a specific Merkle root.',
        input_schema: {
            type: 'object',
            properties: {
                merkleRoot: {
                    type: 'string',
                    description: 'The Merkle root hex to verify.',
                },
                deep: {
                    type: 'boolean',
                    description: 'Perform a cryptographic re-hash of all shards (Deep Verify). default: false',
                },
            },
            required: ['merkleRoot'],
        },
    },
    {
        name: 'shardlock_reindex',
        description: 'Scan the local filesystem for orphaned shards and rebuild the SQLite registry using on-disk metadata.',
        input_schema: {
            type: 'object',
            properties: {},
        },
    },
    {
        name: 'shardlock_health',
        description: 'Get a summary of the Shard-Lock node health, including storage utilization and overdue heartbeats.',
        input_schema: {
            type: 'object',
            properties: {},
        },
    },
    {
        name: 'sentinel_snapshot',
        description: 'Immediately trigger a "State-of-Mind" snapshot of the Aether Index (database) and secure it via Shard-Lock.',
        input_schema: {
            type: 'object',
            properties: {},
        },
    },
];

// ============================================================================
// SQLite HELPERS
// ============================================================================

/**
 * Validates if a string is a valid SHA-256 Merkle root hex.
 */
function isValidMerkleRoot(root) {
    return /^[a-fA-F0-9]{64}$/.test(root);
}

function upsertShard({ merkleRoot, shardCount, dataShards, parityShards, totalBytes, originalSize, label }) {
    const db = getDb();
    if (!db) return;
    
    if (!isValidMerkleRoot(merkleRoot)) {
        log(`[ShardLock] Invalid Merkle root blocked: ${merkleRoot}`, 'WARN');
        return;
    }

    try {
        db.run(
            `INSERT OR REPLACE INTO shards (merkle_root, shard_count, data_shards, parity_shards, total_bytes, original_size, label, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?)`,
            [merkleRoot, shardCount, dataShards, parityShards, totalBytes, originalSize, label || null, localTimestamp()]
        );
        // Explicitly trigger summary update (which triggers DB save eventually)
        const { markDbSummaryDirty } = require('../database');
        markDbSummaryDirty();
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
    const label = input.label;
    const dataShards = input.dataShards || 4;
    const parityShards = input.parityShards || 2;

    if (!data || typeof data !== 'string') {
        return { error: 'data is required and must be a string.' };
    }

    // IPC Safety: Limit data size to 5MB to avoid Android Binder/Memory issues
    const MAX_DATA_SIZE = 5 * 1024 * 1024;
    if (data.length > MAX_DATA_SIZE) {
        return { error: `Data too large (${(data.length / 1024 / 1024).toFixed(2)}MB). Shard-Lock current IPC limit is 5MB.` };
    }
    if (dataShards < 1 || dataShards > 255) {
        return { error: 'dataShards must be between 1 and 255.' };
    }
    if (parityShards < 1 || parityShards > 255) {
        return { error: 'parityShards must be between 1 and 255.' };
    }

    log(`[ShardLock] Encoding ${data.length} bytes into ${dataShards}+${parityShards} shards...`, 'INFO');

    try {
        // Convert to Base64 for binary-safe transfer to bridge
        const base64Data = Buffer.from(data).toString('base64');

        const result = await androidBridgeCall('/shardlock/encode', {
            data: base64Data,
            encoding: 'base64',
            dataShards,
            parityShards,
        });

        if (result.error) {
            return { error: `Shard-Lock encode failed: ${result.error}` };
        }

        // Persist to SQLite registry
        upsertShard({
            merkleRoot: result.merkleRoot,
            shardCount: result.shardCount,
            dataShards: result.dataShards,
            parityShards: result.parityShards,
            totalBytes: result.bytesStored,
            originalSize: data.length,
            label: input.label || null
        });

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
    if (!merkleRoot || typeof merkleRoot !== 'string' || !isValidMerkleRoot(merkleRoot.trim())) {
        return { error: 'A valid 64-character hex merkleRoot is required.' };
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
    const merkleRoot = input.merkleRoot || '';
    if (merkleRoot && !isValidMerkleRoot(merkleRoot)) {
        return { error: 'merkleRoot must be a valid 64-character hex string.' };
    }

    // Step 1: Generate heartbeat (get signature + merkle root)
    let heartbeat;
    try {
        heartbeat = await androidBridgeCall('/shardlock/heartbeat', {
            merkleRoot: merkleRoot,
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

const handlers = {
    shardlock_store: handleShardlockStore,
    shardlock_heartbeat: handleShardlockHeartbeat,
    shardlock_status: handleShardlockStatus,
    shardlock_clear: handleShardlockClear,
    shardlock_anchor: handleShardlockAnchor,
    shardlock_retrieve: handleShardlockRetrieve,
    shardlock_verify: handleShardlockVerify,
    shardlock_reindex: handleShardlockReindex,
    shardlock_health: handleShardlockHealth,
    sentinel_snapshot: handleSentinelSnapshot,
};

async function handleShardlockRetrieve(input) {
    const merkleRoot = input.merkleRoot;
    if (!merkleRoot || typeof merkleRoot !== 'string' || !isValidMerkleRoot(merkleRoot.trim())) {
        return { error: 'A valid 64-character hex merkleRoot is required.' };
    }

    // Step 1: Look up shard config in SQLite
    const registry = getShardRegistry();
    const entry = registry.find(r => r.merkleRoot === merkleRoot.trim());
    if (!entry) {
        return { error: `Shard root ${merkleRoot} not found in local registry.` };
    }

    log(`[ShardLock] Retrieving shards for root: ${entry.merkleRoot} (${entry.dataShards}+${entry.parityShards})...`, 'INFO');

    try {
        // Step 2: Call bridge to decode
        const result = await androidBridgeCall('/shardlock/decode', {
            merkleRoot: entry.merkleRoot,
            dataShards: entry.dataShards,
            parityShards: entry.parityShards,
        });

        if (result.error) {
            return { error: `Retrieval failed: ${result.error}` };
        }

        // Decode Base64 response
        let reconstructed = Buffer.from(result.data, 'base64');
        
        // Trim padding based on original size
        if (entry.originalSize && reconstructed.length > entry.originalSize) {
            reconstructed = reconstructed.slice(0, entry.originalSize);
        }

        const dataString = reconstructed.toString('utf8'); // Assuming UTF8 for now, can be adjusted for binary

        log(`[ShardLock] Successfully reconstructed ${reconstructed.length} bytes for root: ${result.merkleRoot}`, 'INFO');
        return {
            success: true,
            merkleRoot: result.merkleRoot,
            data: dataString,
            originalSize: entry.originalSize,
            label: entry.label,
        };
    } catch (e) {
        log(`[ShardLock] Retrieve error: ${e.message}`, 'ERROR');
        return { error: `Retrieve failed: ${e.message}` };
    }
}

async function handleShardlockVerify(input) {
    const merkleRoot = input.merkleRoot;
    if (!merkleRoot || typeof merkleRoot !== 'string' || !isValidMerkleRoot(merkleRoot.trim())) {
        return { error: 'A valid 64-character hex merkleRoot is required.' };
    }

    const registry = getShardRegistry();
    const entry = registry.find(r => r.merkleRoot === merkleRoot.trim());
    if (!entry) {
        return { error: `Shard root ${merkleRoot} not found in local registry.` };
    }

    try {
        // 1. Basic Check: Existence on disk
        const status = await androidBridgeCall('/shardlock/status', {});
        const onDisk = (status.merkleRoots || []).includes(entry.merkleRoot);
        
        if (!onDisk) {
            return { 
                success: false, 
                merkleRoot: entry.merkleRoot, 
                status: 'MISSING',
                message: 'Shards are missing from local storage.'
            };
        }

        // 2. Deep Check: Cryptographic re-hash (if requested)
        if (input.deep) {
            log(`[ShardLock] Deep Verify: hashing all shards for ${entry.merkleRoot}...`, 'DEBUG');
            // We use /shardlock/decode as the deep verify mechanism for now
            const decodeResult = await androidBridgeCall('/shardlock/decode', {
                merkleRoot: entry.merkleRoot,
                dataShards: entry.dataShards,
                parityShards: entry.parityShards,
            });

            if (decodeResult.error) {
                return { 
                    success: false, 
                    merkleRoot: entry.merkleRoot, 
                    status: 'CORRUPT',
                    error: decodeResult.error 
                };
            }
        }

        return {
            success: true,
            merkleRoot: entry.merkleRoot,
            status: input.deep ? 'VERIFIED_DEEP' : 'VERIFIED_LOCAL',
            shardCount: entry.shardCount,
            lastHeartbeat: entry.lastHeartbeat,
            anchorTx: entry.anchorTx,
        };
    } catch (e) {
        return { error: `Verification failed: ${e.message}` };
    }
}

async function handleShardlockReindex() {
    log('[ShardLock] Starting registry re-index...', 'INFO');
    try {
        const status = await androidBridgeCall('/shardlock/status', {});
        const rootsOnDisk = status.merkleRoots || [];
        const registry = getShardRegistry();
        const registryRoots = new Set(registry.map(r => r.merkleRoot));

        let reindexed = 0;
        let errors = 0;

        for (const root of rootsOnDisk) {
            if (!registryRoots.has(root)) {
                log(`[ShardLock] Orphaned root found: ${root}. Attempting recovery...`, 'DEBUG');
                try {
                    const meta = await androidBridgeCall('/shardlock/read-meta', { merkleRoot: root });
                    if (meta.error) {
                        log(`[ShardLock] Metadata recovery failed for ${root}: ${meta.error}`, 'WARN');
                        errors++;
                        continue;
                    }

                    upsertShard({
                        merkleRoot: root,
                        dataShards: meta.dataShards,
                        parityShards: meta.parityShards,
                        shardCount: meta.totalShards,
                        originalSize: meta.originalSize,
                        label: `recovered-${root.substring(0, 8)}`,
                    });
                    reindexed++;
                } catch (e) {
                    log(`[ShardLock] Error re-indexing ${root}: ${e.message}`, 'ERROR');
                    errors++;
                }
            }
        }

        return {
            success: true,
            reindexedCount: reindexed,
            errorCount: errors,
            message: `Re-index complete. ${reindexed} roots recovered to registry.`,
        };
    } catch (e) {
        return { error: `Re-indexing failed: ${e.message}` };
    }
}

async function handleShardlockHealth() {
    try {
        const registry = getShardRegistry();
        const status = await androidBridgeCall('/shardlock/status', {});
        
        const now = Date.now();
        const DAY_MS = 24 * 60 * 60 * 1000;
        const WEEK_MS = 7 * DAY_MS;

        let totalShardsStored = status.shardCount || 0;
        let healthyRoots = 0;
        let missingRoots = 0;
        let overdueHeartbeats = 0;
        let pendingAnchors = 0;

        // Get last sentinel run & aether snapshot from meta
        const db = getDb();
        const metaRowsRun = db ? db.exec(`SELECT value FROM meta WHERE key = 'last_sentinel_run'`) : [];
        const lastSentinelRun = (metaRowsRun.length > 0 && metaRowsRun[0].values.length > 0) ? metaRowsRun[0].values[0][0] : null;

        const metaRowsSnap = db ? db.exec(`SELECT value FROM meta WHERE key = 'last_aether_snapshot'`) : [];
        const lastAetherSnapshot = (metaRowsSnap.length > 0 && metaRowsSnap[0].values.length > 0) ? metaRowsSnap[0].values[0][0] : null;

        for (const entry of registry) {
            const onDisk = (status.merkleRoots || []).includes(entry.merkleRoot);
            if (!onDisk) {
                missingRoots++;
                continue;
            }
            healthyRoots++;

            const lastHb = entry.lastHeartbeat ? new Date(entry.lastHeartbeat).getTime() : 0;
            if (now - lastHb > DAY_MS) overdueHeartbeats++;
            
            if (!entry.anchorTx) {
                const created = entry.createdAt ? new Date(entry.createdAt).getTime() : 0;
                if (now - created > WEEK_MS) pendingAnchors++;
            }
        }

        return {
            nodeStatus: missingRoots > 0 ? 'WARNING' : 'HEALTHY',
            storage: {
                totalRootsStored: registry.length,
                healthyRoots,
                missingRoots,
                totalShardsStored,
                totalBytes: status.totalBytes,
                totalFormatted: status.totalFormatted,
            },
            heartbeats: {
                overdueCount: overdueHeartbeats,
                pendingAnchorCount: pendingAnchors,
            },
            system: {
                rustCoreAvailable: status.rustCoreAvailable,
                thermalStatus: status.thermalStatus,
                lastSentinelRun,
                lastAetherSnapshot,
            },
            recommendation: missingRoots > 0 ? 'Restore missing shards from backup.' : 
                           overdueHeartbeats > 0 ? 'Wait for Sentinel background heartbeat scan.' :
                           pendingAnchors > 0 ? 'Run shardlock_anchor to anchor storage proofs on-chain.' :
                           'Node is operating within optimal parameters.'
        };
    } catch (e) {
        return { error: `Health summary failed: ${e.message}` };
    }
}

async function handleSentinelSnapshot() {
    log('[ShardLock] Manual Aether Snapshot triggered via tool', 'INFO');
    try {
        const sentinel = require('../sentinel');
        // We call the internal function directly for the tool
        await sentinel.snapshot(); 
        
        return {
            success: true,
            message: 'Aether Index State-of-Mind snapshot completed and secured.',
            timestamp: new Date().toISOString()
        };
    } catch (e) {
        return { error: `Snapshot failed: ${e.message}` };
    }
}

// ============================================================================
// EXPORTS
// ============================================================================

module.exports = { 
    tools, 
    handlers,
    upsertShard,
    updateHeartbeat,
    updateAnchorTx,
    deleteShard,
    getShardRegistry
};
