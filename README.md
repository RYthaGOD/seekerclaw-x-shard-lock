<div align="center">
  <img src="design/banner.png" alt="SeekerClaw" width="100%">
  <br><br>
  <p>
    <img src="https://img.shields.io/badge/Android-14+-3DDC84?logo=android&logoColor=white" alt="Android 14+">
    <img src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin">
    <img src="https://img.shields.io/badge/Claude-Powered-cc785c?logo=anthropic&logoColor=white" alt="Claude">
    <img src="https://img.shields.io/badge/OpenAI-Powered-412991?logo=openai&logoColor=white" alt="OpenAI">
    <img src="https://img.shields.io/badge/OpenRouter-Powered-6467F2?logo=data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI+PHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMiAyQzYuNDggMiAyIDYuNDggMiAxMnM0LjQ4IDEwIDEwIDEwIDEwLTQuNDggMTAtMTBTMTcuNTIgMiAxMiAyem0wIDE4Yy00LjQyIDAtOC0zLjU4LTgtOHMzLjU4LTggOC04IDggMy41OCA4IDgtMy41OCA4LTggOHoiLz48L3N2Zz4=&logoColor=white" alt="OpenRouter">
    <img src="https://img.shields.io/badge/Solana-Seeker-9945FF?logo=solana&logoColor=white" alt="Solana">
    <img src="https://img.shields.io/badge/Shard--Lock-DePIN-FF6B35?logo=rust&logoColor=white" alt="Shard-Lock">
    <img src="https://img.shields.io/badge/Telegram-Bot-26A5E4?logo=telegram&logoColor=white" alt="Telegram">
    <img src="https://img.shields.io/badge/License-MIT-blue" alt="MIT License">
  </p>
  <p>
    <a href="https://www.producthunt.com/products/seekerclaw"><img src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=seekerclaw&theme=dark" alt="SeekerClaw on Product Hunt" width="250" height="54"></a>
  </p>
</div>

---

SeekerClaw embeds a Node.js AI agent inside an Android app, running 24/7 as a foreground service. You interact through Telegram — ask questions, control your phone, trade crypto, shard data, schedule tasks. **61 tools, 36 skills, Solana wallet, Shard-Lock storage, multi-provider AI (Claude + OpenAI + OpenRouter)**, all running locally on your device. Built for the Solana Seeker, runs on any Android 14+ phone.

<div align="center">
  <img src="design/screenshots/01-first-launch.png" width="130">
  <img src="design/screenshots/02-always-on.png" width="130">
  <img src="design/screenshots/03-quick-deploy.png" width="130">
  <img src="design/screenshots/04-memory.png" width="130">
  <img src="design/screenshots/05-solana.png" width="130">
  <img src="design/screenshots/06-skills.png" width="130">
</div>

## Features

| | Feature | What it does |
|---|---|---|
| :robot: | **AI Engine** | Claude, OpenAI, or OpenRouter (multi-provider) with multi-turn tool use |
| :speech_balloon: | **Telegram** | Full bot — reactions, file sharing, inline keyboards, 12 commands |
| :link: | **Solana Wallet** | Swaps, limit orders, DCA, transfers via Jupiter + MWA |
| :lock: | **Shard-Lock** | Erasure-coded storage, Merkle proofs, Ed25519 heartbeats via Rust JNI |
| :iphone: | **Device Control** | Battery, GPS, camera, SMS, calls, clipboard, TTS |
| :brain: | **Memory** | Persistent personality, daily notes, ranked keyword search |
| :alarm_clock: | **Scheduling** | Cron jobs with natural language ("remind me in 30 min") |
| :globe_with_meridians: | **Web Intel** | Search (Brave / Perplexity / Exa / Tavily / Firecrawl), fetch, caching |
| :electric_plug: | **Extensible** | 36 skills + custom skills + MCP remote tools |

<details>
<summary><strong>Architecture</strong></summary>

<br>

```mermaid
graph LR
    You["You (Telegram)"] -->|messages| Agent["SeekerClaw Agent"]
    Agent -->|reasoning| AI["AI Provider (Claude / OpenAI / OpenRouter)"]
    Agent -->|swaps, balance| Solana["Solana / Jupiter"]
    Agent -->|device access| Bridge["Android Bridge"]
    Agent -->|shard, prove| ShardLock["Shard-Lock (Rust JNI)"]
    Agent -->|search, fetch| Web["Web APIs"]
    AI -->|tool calls| Agent
```

**On-device stack:**

```
Android App (Kotlin, Jetpack Compose)
 ├─ Rust-Core (JNI)      — Reed-Solomon erasure, SHA-256 Merkle, Ed25519 signing
 ├─ StorageManager        — On-device shard persistence
 └─ Foreground Service
     └─ Node.js Runtime (nodejs-mobile)
         ├─ claude.js      — AI provider API, system prompt, conversations
         ├─ tools/         — 61 tool handlers across 13 modules
         ├─ task-store.js  — Persistent task checkpoints
         ├─ solana.js      — Jupiter swaps, DCA, limit orders
         ├─ shardlock.js   — Shard-Lock storage tools (encode, heartbeat, status, clear)
         ├─ telegram.js    — Bot, formatting, commands
         ├─ memory.js      — Persistent memory + ranked search
         ├─ skills.js      — Skill loading + semantic routing
         ├─ cron.js        — Job scheduling + natural language parsing
         ├─ mcp-client.js  — MCP Streamable HTTP client
         ├─ web.js         — Search + fetch + caching
         ├─ database.js    — SQL.js analytics
         ├─ security.js    — Prompt injection defense
         ├─ bridge.js      — Android Bridge HTTP client
         ├─ config.js      — Config loading + validation
         └─ main.js        — Orchestrator + heartbeat
```

</details>

## Quick Start

**Prerequisites:** Android Studio, JDK 17, Android SDK 35, Android NDK, Rust + `cargo-ndk` (for Shard-Lock)

```bash
git clone https://github.com/RYthaGOD/seekerclaw-x-shard-lock.git
cd seekerclaw-x-shard-lock

# Build Rust-Core JNI (requires WSL/Linux with cargo-ndk installed)
bash scripts/build-rust-core.sh

./gradlew assembleDappStoreDebug
adb install app/build/outputs/apk/dappStore/debug/app-dappStore-debug.apk
```

Open the app → pick your AI provider (Claude, OpenAI, or OpenRouter) → enter your API key + [Telegram bot token](https://t.me/BotFather) + choose a model + name your agent — or generate a QR code at [seekerclaw.xyz/setup](https://seekerclaw.xyz/setup) and scan it. Done.

> **Step-by-step setup guide:** [How to set up SeekerClaw](https://x.com/SeekerClaw/status/2029197829068005849)

> **Beta** — SeekerClaw is under active development. Expect rough edges and breaking changes. Issues and PRs welcome.

## Shard-Lock Integration

This fork integrates the [Shard-Lock](https://github.com/RYthaGOD/seeker-storage) decentralized storage protocol, giving the AI agent the ability to erasure-encode data, generate Merkle proofs, and sign Ed25519 heartbeats — all powered by a Rust JNI core running natively on ARM64.

| Tool | Description |
|---|---|
| `shardlock_store` | Erasure-encode data into resilient shards stored on-device |
| `shardlock_heartbeat` | Generate a signed Ed25519 storage proof |
| `shardlock_status` | Query shard count, storage usage, Merkle roots, thermal status, SQLite registry |
| `shardlock_clear` | Clear shards for a specific Merkle root |
| `shardlock_anchor` | Anchor a heartbeat proof on Solana via Memo transaction |

The Rust-Core JNI provides:
- **Reed-Solomon erasure coding** — split data into data + parity shards for fault tolerance
- **SHA-256 Merkle tree** — compute a unique root hash over all shards
- **Ed25519 signing** — cryptographically prove the device is storing data
- **Thermal Delta Engine** — ambient-aware throttling to protect device hardware

**Local Shard Discovery** — All shard metadata is persisted in a local SQLite registry (`shards` table) for instant querying without a server.

**On-Chain Heartbeat Anchoring** — Heartbeat proofs can be anchored on Solana via a Memo transaction (~0.000005 SOL). The memo contains a versioned, pipe-delimited payload:
```
SHARDLOCK|v1|<merkleRoot>|<ed25519Signature>|<shardCount>|<timestamp>|<walletPubkey>
```
Anyone can verify the proof by checking the Ed25519 signature against the Merkle root and shard count.

## Partner Skills

Install via Telegram: send your agent the install link and it handles the rest.

| | Skill | What it does | Install |
|---|---|---|---|
| :paw_prints: | **ClawPump** | Launch tokens on Solana via pump.fun — gasless launches | [Install](https://seekerclaw.xyz/partner-skills/clawpump.md) |
| :crystal_ball: | **Dune Analytics** | Query onchain data — DEX trades, token stats, wallet activity | [Install](https://seekerclaw.xyz/partner-skills/dune-analytics.md) |
| :house: | **Home Assistant** | Control smart home — lights, climate, vacuum, alarm, media | [Install](https://seekerclaw.xyz/partner-skills/home-assistant.md) |

> **Build your own:** Skills are Markdown files with YAML frontmatter. See [SKILL-FORMAT.md](SKILL-FORMAT.md) for the spec.

## Important Safety Notice

SeekerClaw gives an AI agent real capabilities on your phone — including wallet transactions, messaging, and device control. Please be aware:

- **AI can make mistakes.** Large language models hallucinate, misinterpret instructions, and occasionally take unintended actions. Always verify before trusting critical outputs.
- **Prompt injection is a real risk.** Malicious content from websites, messages, or files could manipulate the agent. SeekerClaw includes defenses, but no system is bulletproof.
- **Wallet transactions are irreversible.** Swaps, transfers, and DCA orders on Solana cannot be undone. The agent requires confirmation for financial actions — read the details before approving.
- **Start with small amounts.** Don't connect a wallet with significant funds until you're comfortable with how the agent behaves.
- **You are responsible for your agent's actions.** SeekerClaw is a tool, not financial advice. The developers are not liable for any losses.

> **TL;DR:** Treat your agent like a capable but imperfect assistant. Verify important actions, secure your wallet, and don't trust it with more than you can afford to lose.

## Community

Thanks to all contributors:

<a href="https://github.com/sepivip"><img src="https://github.com/sepivip.png" width="50" height="50" alt="sepivip" title="sepivip — creator"></a>
<a href="https://github.com/DashLabsDev"><img src="https://github.com/DashLabsDev.png" width="50" height="50" alt="DashLabsDev" title="DashLabsDev — code contributor"></a>
<a href="https://github.com/DyorAlex"><img src="https://github.com/DyorAlex.png" width="50" height="50" alt="DyorAlex" title="DyorAlex — proposed OpenRouter support"></a>
<a href="https://github.com/LevanIlashvili"><img src="https://github.com/LevanIlashvili.png" width="50" height="50" alt="LevanIlashvili" title="LevanIlashvili — security audit"></a>

## Links

**Website:** [seekerclaw.xyz](https://seekerclaw.xyz) · **Product Hunt:** [SeekerClaw](https://www.producthunt.com/products/seekerclaw) · **Twitter:** [@SeekerClaw](https://x.com/SeekerClaw) · **Telegram:** [t.me/seekerclaw](https://t.me/seekerclaw)

---

<div align="center">

[Contributing](CONTRIBUTING.md) · [Security](SECURITY.md) · [Changelog](CHANGELOG.md) · [License](LICENSE)

</div>
