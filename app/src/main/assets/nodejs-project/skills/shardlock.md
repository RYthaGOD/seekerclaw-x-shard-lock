---
name: shard-lock
description: "Decentralized storage primitives — erasure coding, Merkle proofs, Ed25519 heartbeats, and on-chain anchoring via Solana Memo"
version: "2.0.0"
triggers:
  - shard-lock
  - shardlock
  - shard lock
  - erasure
  - merkle
  - heartbeat
  - storage proof
  - anchor heartbeat
  - anchor proof
  - on-chain proof
  - depin storage
emoji: "🔒"
---

## Description
Shard-Lock gives SeekerClaw hardware-accelerated decentralized storage.
Data is split via Reed-Solomon erasure coding, hashed into a SHA-256 Merkle tree,
and signed with Ed25519 to produce verifiable storage proofs (heartbeats).
Heartbeats can be anchored on the Solana blockchain via Memo transactions for
publicly verifiable, timestamped proof of storage.

## Instructions

### Store data
Use `shardlock_store` with `data` (required), optional `dataShards` (default 4),
`parityShards` (default 2), and `label`. Returns the `merkleRoot` as the unique
identifier for this shard set.

### Generate a heartbeat
Use `shardlock_heartbeat` with optional `merkleRoot`. Produces a 64-byte Ed25519
signature proving the device actively holds the shards.

### Check status
Use `shardlock_status` — returns live stats from the device plus the full shard
registry from SQLite (labels, timestamps, anchor tx, last heartbeat).

### Clear shards
Use `shardlock_clear` with the `merkleRoot` to delete. Removes from both
filesystem and SQLite.

### Anchor on-chain
Use `shardlock_anchor` to submit a heartbeat proof to the Solana blockchain as a
Memo transaction. This creates a permanent, publicly verifiable record. The user
must approve the transaction in their wallet. Costs ~0.000005 SOL (tx fee only).
Memo format: `SHARDLOCK|v1|<merkleRoot>|<ed25519Sig>|<shardCount>|<timestamp>|<wallet>`

### Retrieve and Verify
Use `shardlock_retrieve` with `merkleRoot` to reconstruct the original data.
Use `shardlock_verify` to perform a local integrity check on stored shards. Set `deep: true` to perform a full cryptographic re-hash of all shards.

### Node Health
Use `shardlock_health` to get a summary of the storage node health, including total bytes stored, healthy vs missing roots, and overdue heartbeats.

## Tools
- shardlock_store: Erasure-encode and persist data
- shardlock_heartbeat: Sign a storage proof
- shardlock_status: Query shard registry and device stats
- shardlock_clear: Remove shards by Merkle root
- shardlock_anchor: Anchor heartbeat on Solana (Memo tx)
- shardlock_retrieve: Reconstruct original data from shards
- shardlock_verify: Local integrity check (supports `deep` mode)
- shardlock_health: Node health summary and recommendations
