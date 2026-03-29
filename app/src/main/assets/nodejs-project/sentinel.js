// sentinel.js — Automated Shard-Lock heartbeat & integrity guardian
// Periodically scans the shard registry and performs heartbeats to prove storage.
// Part of the Seeker Sentinel Protocol Phase 2.

const { androidBridgeCall } = require('./bridge');
const { getShardRegistry, updateHeartbeat } = require('./tools/shardlock');
const { log } = require('./config');

let isSentinelRunning = false;

/**
 * Runs a scan of the local shard registry and performs heartbeats for 
 * any shards that haven't been verified in the last 24 hours.
 */
async function runSentinel() {
    if (isSentinelRunning) {
        log('[Sentinel] Scan already in progress. Skipping overlapping interval.', 'DEBUG');
        return;
    }

    isSentinelRunning = true;
    log('[Sentinel] Starting automated Shard-Lock scan...', 'INFO');
    
    let registry;
    try {
        registry = getShardRegistry();
    } catch (e) {
        log(`[Sentinel] Failed to read shard registry: ${e.message}`, 'ERROR');
        isSentinelRunning = false;
        return;
    }

    if (registry.length === 0) {
        log('[Sentinel] No shards found in registry. Skipping scan.', 'DEBUG');
        return;
    }

    const now = Date.now();
    const DAY_MS = 24 * 60 * 60 * 1000;
    let heartbeatCount = 0;
    let failCount = 0;

    for (const entry of registry) {
        // Handle both ISO strings and numeric timestamps
        let lastHeartbeatTime = 0;
        if (entry.lastHeartbeat) {
            lastHeartbeatTime = new Date(entry.lastHeartbeat).getTime();
            if (isNaN(lastHeartbeatTime)) lastHeartbeatTime = 0;
        }

        const needsHeartbeat = (now - lastHeartbeatTime) > DAY_MS;

        if (needsHeartbeat) {
            // 0. Bridge Safety: Throttle checks to avoid flooding the IPC bridge
            await new Promise(r => setTimeout(r, 500));

            // 1. Thermal Awareness: Don't cook the battery with storage proofs
            let status;
            try {
                status = await androidBridgeCall('/shardlock/status', {}, 30000);
            } catch (e) {
                log(`[Sentinel] Thermal check bridge call failed: ${e.message}`, 'WARN');
                status = { thermalStatus: -1 };
            }

            if (status.thermalStatus === 2) {
                log('[Sentinel] CRITICAL THERMAL STATE. Suspending scan to protect hardware.', 'ERROR');
                isSentinelRunning = false;
                return;
            }
            if (status.thermalStatus === 1) {
                log('[Sentinel] Thermal throttling active. Waiting 10s...', 'WARN');
                await new Promise(r => setTimeout(r, 10000));
            }

            log(`[Sentinel] Heartbeat due for root: ${entry.merkleRoot} (Last: ${entry.lastHeartbeat || 'Never'})`, 'INFO');
            try {
                // 2. Generate local heartbeat (signs root + shardCount with device key)
                const heartbeat = await androidBridgeCall('/shardlock/heartbeat', {
                    merkleRoot: entry.merkleRoot
                });

                if (heartbeat.error) {
                    log(`[Sentinel] Heartbeat bridge call failed for ${entry.merkleRoot}: ${heartbeat.error}`, 'ERROR');
                    failCount++;
                    continue;
                }

                // 3. Update local registry in SQLite
                updateHeartbeat(entry.merkleRoot);
                heartbeatCount++;
                log(`[Sentinel] Local storage proof generated and recorded for ${entry.merkleRoot}`, 'INFO');

                // NOTE: On-chain anchoring (shardlock_anchor) is NOT automated here
                // as it requires user wallet approval and a small SOL fee.
                // In Phase 3, we will implement "Delegated Anchoring" via TEE.
            } catch (e) {
                log(`[Sentinel] Fatal error processing ${entry.merkleRoot}: ${e.message}`, 'ERROR');
                failCount++;
            }
            }
    }

    // 4. Sentinel MVP: Create "State-of-Mind" Snapshot (Aether Index) if due
    try {
        const { getDb } = require('./database');
        const db = getDb();
        const metaRows = db ? db.exec(`SELECT value FROM meta WHERE key = 'last_aether_snapshot'`) : [];
        const lastSnapshot = (metaRows.length > 0 && metaRows[0].values.length > 0) ? new Date(metaRows[0].values[0][0]).getTime() : 0;
        
        if (now - lastSnapshot > DAY_MS) {
            log('[Sentinel] Initiating Aether Index State-of-Mind snapshot...', 'INFO');
            await createStateSnapshot();
        }
    } catch (e) {
        log(`[Sentinel] Aether snapshot failed: ${e.message}`, 'ERROR');
    }

    if (heartbeatCount > 0 || failCount > 0) {
        log(`[Sentinel] Scan complete. Heartbeats: ${heartbeatCount}, Failures: ${failCount}`, 'INFO');
    } else {
        log('[Sentinel] Scan complete. All shards are up to date.', 'DEBUG');
    }
    isSentinelRunning = false;
    
    // Update internal meta for health dashboard
    try {
        const { getDb } = require('./database');
        const db = getDb();
        if (db) {
            db.run(`INSERT OR REPLACE INTO meta (key, value) VALUES ('last_sentinel_run', ?)`, [new Date().toISOString()]);
        }
    } catch (_) {}
}

/**
 * Captures the current Aether Index (database) and stores it via Shard-Lock.
 */
async function createStateSnapshot() {
    const { handlers } = require('./tools/shardlock');
    const { exportDatabase } = require('./database');
    
    const dbBuffer = exportDatabase();
    if (!dbBuffer) {
        log('[Sentinel] Failed to export database for snapshot', 'ERROR');
        return;
    }

    const base64Data = dbBuffer.toString('base64');
    const label = `AETHER_STATE_${new Date().toISOString().split('T')[0]}`;

    log(`[Sentinel] Storing Aether State (${dbBuffer.length} bytes) in Shard-Lock...`, 'INFO');
    
    const result = await handlers.shardlock_store({
        data: base64Data,
        label: label,
        dataShards: 4,
        parityShards: 2
    });

    if (result.error) {
        log(`[Sentinel] Shard-Lock snapshot failed: ${result.error}`, 'ERROR');
    } else {
        log(`[Sentinel] Aether Index Snapshot secured: ${result.merkleRoot}`, 'INFO');
        // Record successful snapshot time
        const { getDb } = require('./database');
        const db = getDb();
        if (db) {
            db.run(`INSERT OR REPLACE INTO meta (key, value) VALUES ('last_aether_snapshot', ?)`, [new Date().toISOString()]);
        }
    }
}

/**
 * Starts the Sentinel recurring interval.
 * @param {number} intervalMs - Frequency of scans (default 1 hour)
 */
function start(intervalMs = 60 * 60 * 1000) {
    log(`[Sentinel] Initializing automated sentinel (Interval: ${intervalMs / 60000} min)`, 'INFO');
    
    // Initial scan after 30s let the system settle
    setTimeout(() => {
        runSentinel().catch(e => log(`[Sentinel] Initial scan failed: ${e.message}`, 'ERROR'));
    }, 30000);

    // Recurring interval
    setInterval(() => {
        runSentinel().catch(e => log(`[Sentinel] Recurring scan failed: ${e.message}`, 'ERROR'));
    }, intervalMs);
}

module.exports = { run: runSentinel, start, snapshot: createStateSnapshot };
