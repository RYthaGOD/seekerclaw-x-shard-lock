---
name: shardlock
description: Manage decentralized storage on your Seeker using the Shard-Lock protocol. Encode data into erasure-coded shards, generate signed storage proofs (heartbeats), and monitor your node's storage state.
version: 1.0.0
emoji: 🔒
triggers:
  - shard
  - shardlock
  - shard-lock
  - storage proof
  - erasure
  - merkle
  - heartbeat proof
  - encode shards
---

# Shard-Lock Storage

You can help the user manage decentralized storage using the Shard-Lock protocol.

## Capabilities

- **Store data**: Use `shardlock_store` to erasure-encode data into resilient shards stored on-device.
- **Generate proofs**: Use `shardlock_heartbeat` to create Ed25519-signed storage proofs (heartbeats).
- **Check status**: Use `shardlock_status` to show shard count, storage usage, and Merkle roots.
- **Clear shards**: Use `shardlock_clear` to remove shards for a specific Merkle root.

## How It Works

1. Data is split into **data shards** and **parity shards** using Reed-Solomon erasure coding.
2. A **SHA-256 Merkle tree** is computed over all shards, producing a unique root hash.
3. The Merkle root can be signed with an Ed25519 key to produce a **heartbeat** — a cryptographic proof that the device is actively storing data.

## Tips

- Default: 4 data shards + 2 parity shards (can recover from up to 2 lost shards).
- The Merkle root is the unique identifier for each stored dataset.
- Heartbeats are 64-byte Ed25519 signatures — the building block for on-chain storage proofs.
