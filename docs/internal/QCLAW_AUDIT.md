# QClaw Deep Audit — What SeekerClaw Can Borrow

> **Date:** 2026-03-22 | **Site:** https://qclaw.qq.com/
> **QClaw:** Tencent's OpenClaw wrapper, closed-source, desktop + WeChat, China market
> **SeekerClaw:** Android-native 24/7 agent, Telegram, crypto/DeFi, global market

---

## TL;DR — Top 5 Steals

| # | Feature | Priority | Effort | Why |
|---|---------|----------|--------|-----|
| 1 | WeChat-style remote control UX | **Insight** | — | Their killer feature is "message from phone → agent acts on PC." Our equivalent: "message from Telegram → agent acts on phone." We already do this — but their UX polish is worth studying. |
| 2 | Inspiration Square (preset tasks) | **P2** | S | One-click task templates. We could do "Quick Actions" inline keyboard buttons in Telegram. |
| 3 | Skill Vetter (security audit) | **P1** | M | Auto-scan skills for dangerous operations before install. We need this as MCP/skills grow. |
| 4 | Zero-config onboarding | **Insight** | — | QClaw's 20-second setup is their moat in China. Our QR scan flow is already good but could be smoother. |
| 5 | Multi-channel distribution | **P3** | L | They're on WeChat + QQ + Mini Program. We're Telegram-only. WhatsApp/Discord could expand reach. |

---

## 1. What Is QClaw

QClaw is **Tencent's consumer packaging of OpenClaw** into a one-click desktop application. Built by the Tencent PC Manager (电脑管家) team. It wraps the open-source OpenClaw framework into an Electron app with native WeChat/QQ integration.

**Launched:** March 9, 2026 (internal testing) → March 18, 2026 (WeChat Mini Program, public beta)

**The pitch:** "Send a WeChat message from your phone, QClaw does it on your PC."

### How It Works

```
Phone (WeChat/QQ)  ──message──►  QClaw Desktop App  ──►  PC actions
                                 (OpenClaw inside)       (files, browser, email)
```

1. User sends natural language via WeChat or QQ chat
2. QClaw on desktop receives the command
3. Agent decomposes into steps
4. Executes via system tools (file system, browser, GUI automation)

### Tech Stack

- **Framework:** OpenClaw (open-source)
- **Desktop:** Electron (Mac + Windows)
- **Phone:** WeChat Mini Program
- **Models:** Multi-model — DeepSeek, Kimi, MiniMax, GLM, Claude, GPT, Ollama (local)
- **Skills:** ClawHub marketplace (5,000+) + GitHub skills
- **MCP:** Supported (confirmed in docs)

---

## 2. Feature Inventory

### Core Features

| Feature | Description | SeekerClaw Equivalent |
|---------|-------------|----------------------|
| **WeChat Remote Control** | Send commands from phone via WeChat → executes on PC | Telegram → executes on phone ✅ |
| **QQ Remote Control** | Same via QQ messaging | N/A (Telegram only) |
| **WeChat Mini Program** | No separate app needed on phone — embedded in WeChat | N/A (we need the app installed) |
| **File Management** | Organize desktop files, categorize by project, extract summaries | `read`, `write`, `ls`, `delete` ✅ |
| **Browser Control** | Open URLs, fill forms, navigate pages via GUI automation | `web_fetch`, `web_search` (no GUI) |
| **Email Composition** | Learns writing style, drafts emails | N/A |
| **Scheduled Tasks** | Weather alerts, timed notifications, automated workflows | `cron_create` ✅ |
| **Persistent Memory** | Learns preferences across sessions | MEMORY.md + daily files ✅ |
| **5,000+ Skills** | Via ClawHub marketplace + GitHub community | ~5 user-created skills |
| **Inspiration Square** | ~20 preset task templates (one-click execution) | **None** |
| **Skill Vetter** | Security audit scanning for dangerous operations in skills | **None** |
| **Custom Model Switching** | Domestic + international LLMs, API key entry | Claude/OpenAI/OpenRouter ✅ |
| **Local-first** | All data on device, no cloud upload | All data on device ✅ |

### Built-in Safety Skills

| Skill | Purpose | SeekerClaw Equivalent |
|-------|---------|----------------------|
| `qclaw-rules` | Forces Chinese language replies | N/A (we're English-first) |
| `qclaw-env` | Auto-resolves network issues, switches mirrors | N/A (direct API calls) |
| `skill-vetter` | Scans skills for dangerous operations before install | **None — we need this** |

### What QClaw Has That We Don't

| Feature | Value for SeekerClaw | Priority |
|---------|---------------------|----------|
| **Inspiration Square** (preset tasks) | Quick-action buttons in Telegram for common tasks | P2 |
| **Skill Vetter** | Security scanning for 3rd-party skills/MCP servers | P1 |
| **GUI Automation** | They click buttons, fill forms. We can't (no screen access) | N/A |
| **File Transfer (phone↔PC)** | They send files via WeChat. We send via `telegram_send_file` | Already have ✅ |
| **Multi-messaging** | WeChat + QQ + Mini Program | We're Telegram-only (by design) |

### What We Have That QClaw Doesn't

| Feature | Why It Matters |
|---------|---------------|
| **24/7 background operation** | QClaw needs desktop running. We run on phone always-on. |
| **Crypto/DeFi** | Jupiter swaps, DCA, limit orders, wallet, NFTs. QClaw has zero. |
| **Android native tools** | Camera, GPS, SMS, calls, contacts, TTS, apps. QClaw is desktop-only. |
| **Cron/scheduling** | Full cron system with persistence. QClaw has basic reminders. |
| **Multi-provider with streaming** | Claude, OpenAI, OpenRouter — all with tool support. QClaw has model switching but limited tool-use across providers. |
| **Solana wallet integration** | MWA sign-only, gasless swaps. Completely unique to us. |
| **Watchdog + crash recovery** | Auto-restart, checkpoint resume. QClaw just dies if Electron crashes. |
| **Encrypted config** | Android Keystore AES-256-GCM. QClaw stores API keys in plaintext config. |

---

## 3. Architecture Comparison

| Dimension | QClaw (Tencent) | SeekerClaw |
|-----------|----------------|------------|
| **Platform** | Desktop (Electron, Mac+Win) | Android (Kotlin + Node.js) |
| **Framework** | OpenClaw (direct wrapper) | OpenClaw-compatible (custom port) |
| **Runtime** | Node.js (desktop native) | Node.js 18 (nodejs-mobile, ARM64) |
| **Interface** | WeChat/QQ (remote) + Desktop UI | Telegram (primary) + Android app (config) |
| **Distribution** | Direct download (qclaw.qq.com) | Solana dApp Store + Google Play |
| **Target** | Chinese consumers (1.4B WeChat MAU) | Global crypto users (Solana Seeker) |
| **Models** | Multi-model (domestic + international) | Claude, OpenAI, OpenRouter |
| **Skills** | 5,000+ (ClawHub + GitHub) | ~5 user-created |
| **MCP** | Supported | Supported ✅ |
| **Memory** | OpenClaw standard (MEMORY.md) | OpenClaw standard ✅ |
| **Security** | skill-vetter scanning | Secret redaction, bridge auth, js_eval sandbox |
| **Pricing** | Free | Free (bring your own API key) |
| **Open source** | Closed (OpenClaw underneath is open) | Open source |

---

## 4. "Lobster Fever" — Competitive Landscape in China

All three Chinese tech giants have built OpenClaw wrappers:

| Company | Product | Distribution | Differentiator |
|---------|---------|-------------|----------------|
| **Tencent** | QClaw + WorkBuddy | WeChat/QQ (1.4B MAU) | Zero-config, WeChat Mini Program |
| **Alibaba** | JVS Claw | DingTalk | Enterprise workflow integration |
| **ByteDance** | Feishu OpenClaw Plugin | Feishu/Lark | Calendar, task, document integration |

**Key insight:** All three are **distribution plays**, not technology plays. The OpenClaw core is identical. The moat is the messaging platform integration.

**SeekerClaw's position:** We're the only **mobile-native** OpenClaw-compatible agent. No Chinese competitor runs on the phone itself. They all require a desktop.

---

## 5. What We Should Borrow

### 5.1 Inspiration Square → "Quick Actions" (P2)

QClaw has ~20 preset task templates that users can execute with one click. Examples:
- "Clean up my desktop"
- "Summarize this document"
- "Check today's weather"
- "Monitor news about [topic]"

**For SeekerClaw:** Add a `/quick` command or inline keyboard in Telegram with preset actions:

```
🔋 Device Status    📊 Portfolio Check
📰 News Brief       💰 SOL Price
🧹 Memory Cleanup   📋 My Tasks
```

Each button sends a pre-formatted message to the agent. Zero typing needed.

**Effort:** Small. Just Telegram inline keyboard buttons that send preset messages.

### 5.2 Skill Vetter → Skill Security Scanner (P1)

QClaw's `skill-vetter` scans skills for dangerous operations before installation. This is critical as our skills ecosystem grows.

**What to scan for:**
- `shell_exec` with dangerous commands (rm -rf, curl | bash, etc.)
- API key extraction attempts in skill instructions
- Prompt injection patterns in skill text
- MCP server URLs pointing to suspicious domains
- Skills requesting tools they shouldn't need (e.g., a "weather" skill requesting `solana_swap`)

**For SeekerClaw:** Add a `validateSkill()` function in `skills.js` that runs before `skill_install` completes. Warn user via Telegram if suspicious patterns found. Don't block — warn and require explicit confirmation.

**Effort:** Medium. ~100 lines of pattern matching + Telegram confirmation flow.

### 5.3 Mini Program Concept → Telegram Bot Menu (P3)

QClaw's WeChat Mini Program means users don't need a separate app on their phone to interact. It's embedded in WeChat.

**Telegram equivalent:** Telegram Bot Menu Button + Web App. Telegram supports mini web apps inside the chat. We could build a lightweight dashboard (status, portfolio, settings) as a Telegram Web App.

**Effort:** Large. Needs a web frontend served from the device or a static host. Defer.

### 5.4 Multi-Account / Multi-Bot (P3)

QClaw supports pairing multiple devices to one WeChat account. We could support multiple Telegram bots (e.g., one for personal, one for trading).

**Effort:** Large. Significant architecture change. Defer.

### 5.5 Network Resilience Skill (P2)

QClaw's `qclaw-env` auto-resolves network issues and switches mirrors. For mobile, this translates to:
- Auto-retry on network errors with exponential backoff
- Detect Wi-Fi vs cellular and adjust behavior (e.g., skip large web_fetch on cellular)
- Notify user when connectivity is poor

**We partially have this** (error classification in claude.js), but a dedicated network resilience layer could be more robust.

**Effort:** Small. Extend existing `classifyNetworkError()`.

---

## 6. What We Should NOT Borrow

| Feature | Why Not |
|---------|---------|
| **GUI Automation** | We can't control the Android screen. Our tools work at the system level (contacts, SMS, camera), not UI level. Different paradigm. |
| **Electron packaging** | We're already native Android. Electron would be a regression. |
| **WeChat/QQ integration** | Wrong market. Our users are on Telegram globally. |
| **Chinese LLM defaults** | Our users want Claude/GPT. DeepSeek/Kimi/MiniMax are niche outside China. |
| **Desktop file management** | We manage phone files, not desktop. Different OS, different UX. |
| **"Lobster" branding** | Cute in China, confusing globally. Keep SeekerClaw brand. |

---

## 7. Security Lessons from QClaw

### CVE-2026-25253 & 135K Exposed Instances

The rapid rollout of OpenClaw-based agents (including QClaw) exposed serious security issues:
- **135,000+ publicly exposed OpenClaw instances** found globally
- **15,000 vulnerable to RCE** (Remote Code Execution)
- China's Ministry of Industry flagged default configs as having "material security exposure"

**Lessons for SeekerClaw:**
1. Our Android-native approach is inherently safer — no exposed ports (bridge is localhost-only)
2. Our per-boot auth token on the Android Bridge prevents unauthorized access
3. Our `js_eval` sandbox + `shell_exec` allowlist limits blast radius
4. **BUT:** As we add MCP servers, each is a new attack surface. The skill-vetter concept becomes critical.

### QClaw's Security Stack vs Ours

| Security Layer | QClaw | SeekerClaw |
|---------------|-------|------------|
| API key storage | Plaintext config file | Android Keystore AES-256-GCM ✅ |
| Network exposure | Electron app (desktop ports) | Localhost-only bridge ✅ |
| Skill scanning | `skill-vetter` built-in | **None — need this** |
| Secret redaction | Unknown | `security.js` redacts all outputs ✅ |
| MCP rug-pull detection | Unknown | SHA-256 description monitoring ✅ |
| Tool output sanitization | Unknown | EXTERNAL_UNTRUSTED_CONTENT wrapping ✅ |

**We're ahead on core security but behind on skill/plugin security scanning.**

---

## 8. Key Insights

### What QClaw Gets Right

1. **Distribution is king.** QClaw's moat isn't technology — it's WeChat's 1.4B users. The zero-config install + Mini Program means anyone can try it in 20 seconds.
2. **Preset tasks lower the bar.** "Inspiration Square" eliminates the "what do I say to an AI?" problem. Users pick from templates instead of writing prompts.
3. **Safety skills ship built-in.** `skill-vetter` runs automatically. Users don't need to think about security.
4. **Local-first is a selling point.** "Your data never leaves your device" resonates with privacy-conscious users.

### What QClaw Gets Wrong (per 36kr Review)

1. **Complex tasks fail.** Multi-step operations that require adaptation (not just execution) are unreliable.
2. **Auth flows break it.** Can't handle QR codes, CAPTCHAs, or login prompts.
3. **GUI automation is fragile.** Entering "aaa/aaaaaa" during searches, clicking wrong buttons.
4. **"Thinking interns, not employees."** Current agent products including QClaw are characterized as unreliable for real work.

**SeekerClaw advantage:** We don't do GUI automation. Our tools are API-level (Telegram, Solana, Android Bridge), which are deterministic and reliable. A `solana_swap` either succeeds or fails — it doesn't accidentally click the wrong button.

### The "Lobster Fever" Validation

The fact that Tencent, Alibaba, and ByteDance all simultaneously built OpenClaw wrappers validates:
1. **The OpenClaw agent paradigm works.** Skills, memory, tools, personality — the architecture is sound.
2. **Distribution matters more than tech.** All three use the same core. The winner will be who has the best channel.
3. **Mobile is the next frontier.** All three are desktop-first. Nobody has cracked mobile-native yet. **That's us.**

---

## 9. Competitive Positioning Update

| Feature | QClaw | DeerFlow | SeekerClaw | Claude Code |
|---------|-------|----------|------------|-------------|
| Runs on phone | ❌ (Mini Program only) | ❌ | ✅ | ❌ |
| 24/7 background | ❌ (needs desktop) | ❌ (needs server) | ✅ | ❌ |
| Crypto/DeFi | ❌ | ❌ | ✅ | ❌ |
| Skill marketplace | ✅ (5,000+) | ✅ (17) | Partial (~5) | ✅ |
| Skill security scanning | ✅ | ❌ | ❌ (need this) | ❌ |
| MCP support | ✅ | ✅ | ✅ | ✅ |
| Zero-config setup | ✅ | ❌ | Partial (QR scan) | ❌ |
| Preset task templates | ✅ | ❌ | ❌ | ❌ |
| Loop detection | ❌ | ✅ | ❌ (planned) | ✅ |
| Context summarization | ❌ | ✅ | ❌ (planned) | ✅ |
| GUI automation | ✅ | ✅ (Docker) | ❌ | ❌ |
| Memory system | ✅ (OpenClaw) | ✅ (LLM-extracted) | ✅ (OpenClaw) | ❌ |
| Open source | ❌ | ✅ | ✅ | ✅ |

---

## 10. Action Items

- [ ] **Skill Vetter (P1)** — Add `validateSkill()` scanning for dangerous patterns before install
- [ ] **Quick Actions (P2)** — Telegram inline keyboard with preset task buttons
- [ ] **Network Resilience (P2)** — Extend error classification for mobile-specific scenarios
- [ ] **Study QClaw UX** — Download and test for onboarding flow inspiration
- [ ] **Update competitive positioning** in PROJECT.md with QClaw/lobster fever context

---

*Generated from deep audit of https://qclaw.qq.com/ (Tencent QClaw)*
*Cross-referenced with SeekerClaw v1.7.0 and DeerFlow audit*
