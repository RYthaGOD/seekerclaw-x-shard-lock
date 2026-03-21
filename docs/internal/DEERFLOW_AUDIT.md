# DeerFlow v2.0 Deep Audit — What SeekerClaw Can Borrow

> **Date:** 2026-03-21 | **Repo:** https://github.com/bytedance/deer-flow
> **DeerFlow:** 32,400 stars, MIT, Python 3.12+, LangGraph/LangChain, Next.js frontend
> **SeekerClaw:** Production Android app, Node.js 18, Telegram-only, 4,700+ users

---

## TL;DR — Top 5 Steals

| # | Feature | Priority | Effort | Why |
|---|---------|----------|--------|-----|
| 1 | Loop Detection | **P0** | S | Already planned. Ship it. Saves $$ and user frustration. |
| 2 | Context Summarization | **P1** | M | Prevents amnesia on long sessions. DeerFlow's middleware approach is battle-tested. |
| 3 | Clarification Tool | **P1** | S | Ask before guessing. 5-type taxonomy is clean. |
| 4 | TODO Persistence | **P2** | S | Survives context summarization. Cheap to add. |
| 5 | Deferred Tool Loading | **P1** | M | Critical for MCP scale. Already in our plan. Per-provider strategy needed. |

---

## 1. What Is DeerFlow v2.0

DeerFlow (Deep Exploration and Efficient Research Flow) is ByteDance's open-source "super agent harness." V2.0 is a **ground-up rewrite** (shares no code with v1). It orchestrates sub-agents, memory, sandboxed Docker execution, and skills to handle complex multi-step tasks taking minutes to hours.

### Architecture (4 Services)

| Service | Port | Role |
|---------|------|------|
| Nginx | 2026 | Reverse proxy |
| LangGraph Server | 2024 | Agent runtime (Python) |
| Gateway API | 8001 | FastAPI REST (models, MCP, skills, memory, uploads) |
| Frontend | 3000 | Next.js web UI |

**Key design:** Clean split between publishable **harness** package (`deerflow/`) and app layer (`app/`). CI-enforced import boundary — harness never imports app.

### Tech Stack

- Python 3.12+, `uv` package manager, LangGraph 1.0.6+, LangChain 1.2.3+
- FastAPI 0.115+ (Gateway), Docker (sandbox), Nginx
- Node.js 22+ (frontend only)
- DuckDB, tiktoken, agent-sandbox, markitdown

---

## 2. Complete Tool Inventory

### Built-in Tools (6)

| Tool | Purpose | SeekerClaw Equivalent |
|------|---------|----------------------|
| `task` | Spawn subagents (general-purpose or bash). Async with polling. Max 3 concurrent, 15min timeout. | **None** — we're single-agent |
| `ask_clarification` | Human-in-the-loop pause. 5 types: missing_info, ambiguous_requirement, approach_choice, risk_confirmation, suggestion. | **None** — agent just guesses |
| `present_file` | Surface generated files to user UI. Only from `/mnt/user-data/outputs`. | `send_document` (Telegram) |
| `tool_search` | Discover deferred MCP tools by regex/keyword. Returns full JSON schema. Max 5 results. | **None** — all tools always loaded |
| `view_image` | Inject images for vision models. | `vision` tool (we have this) |
| `setup_agent` | Bootstrap custom agent creation. | **None** |

### Sandbox Tools (6)

| Tool | Purpose | SeekerClaw Equivalent |
|------|---------|----------------------|
| `bash` | Shell in isolated Docker container | `shell_exec` (Android toybox, not isolated) |
| `ls` | Directory listing (2 levels, tree format) | `list_dir` |
| `read_file` | Read with optional line range | `read` |
| `write_file` | Write/append text | `write` |
| `str_replace` | String replacement in files | `patch` |
| `update_file` | Binary file write | **None** |

### Community/Search Tools (6)

| Tool | Provider | SeekerClaw Equivalent |
|------|----------|----------------------|
| `web_search` | Tavily or InfoQuest | `web_search` (Brave) |
| `web_fetch` | Jina AI or InfoQuest | `web_fetch` |
| `image_search` | DuckDuckGo | **None** |
| Firecrawl | Firecrawl API | **None** (via MCP possible) |

### Tool Groups

Tools organized into named groups: `web`, `file:read`, `file:write`, `bash`. Groups can be selectively enabled per agent/subagent. **We don't have this — all tools available to all conversations.**

---

## 3. Feature Deep Dive

### 3.1 Middleware Pipeline (11 components)

DeerFlow's killer feature is a **strictly ordered middleware chain** that wraps every agent turn. Each middleware can inspect, modify, or short-circuit the message flow.

| # | Middleware | What It Does | SeekerClaw Status |
|---|-----------|-------------|-------------------|
| 1 | ToolErrorHandling | Converts tool exceptions to ToolMessages | We do this inline |
| 2 | Summarization | Compresses old context when thresholds hit | **MISSING — P1** |
| 3 | DanglingToolCall | Patches missing ToolMessages for interrupted calls | **MISSING** |
| 4 | Todo | Persists task list across summarization | **MISSING — P2** |
| 5 | Title | Auto-generates conversation title | N/A (Telegram) |
| 6 | Memory | Queues conversation for async memory update | We do this inline |
| 7 | ViewImage | Injects image data for vision models | We handle this |
| 8 | DeferredToolFilter | Hides deferred tool schemas from model | **MISSING — P1** |
| 9 | SubagentLimit | Caps concurrent subagent spawns | N/A (single agent) |
| 10 | LoopDetection | Detects repeated tool calls (warn@3, break@5) | **MISSING — P0** |
| 11 | Clarification | Pauses for user input (always last) | **MISSING — P1** |

**Takeaway:** We should adopt a middleware-style architecture for our tool loop. Not necessarily a formal middleware chain (overkill for Node.js single-file), but the **concepts** of loop detection, summarization, and clarification should be plugged into `claude.js`'s tool loop.

### 3.2 Loop Detection

**DeerFlow approach:**
- MD5 hash of tool call set (order-independent for parallel calls)
- Sliding window of 20 hashes per thread
- Warn at 3 repeats → inject "stop and produce final answer"
- Hard break at 5 repeats → strip ALL tool_calls from response

**What we should do:**
Already planned in [DEERFLOW_FEATURES_PLAN.md](DEERFLOW_FEATURES_PLAN.md). Implementation is ready. **Just ship it.**

### 3.3 Context Summarization

**DeerFlow approach:**
- Triggers on OR of: token count threshold, message count (50), or % of model's max input (80%)
- Preserves last N messages (default 20), compresses older into single summary
- AI/tool message pairs never split
- Trims to 4000 tokens for summarization input
- TODO middleware re-injects task list if summarization drops it

**What we should do:**
This is critical for SeekerClaw. Long conversations (common with 24/7 agents) eventually blow the context window. Current behavior: `adaptiveTrim()` fires at 90% and removes oldest messages. This is **lossy** — important context is silently dropped.

**Better approach inspired by DeerFlow:**
1. At 80% context, summarize old messages into a `[Context Summary]` system message
2. Keep last 15-20 messages verbatim
3. Preserve tool call/result pairs atomically
4. Re-inject active TODO list after summarization
5. Use a cheaper/faster model for summarization (Haiku) to save costs

**Effort:** Medium. ~150 lines in `claude.js`. Need to handle the summarization API call + message restructuring.

### 3.4 Clarification Tool (Human-in-the-Loop)

**DeerFlow's taxonomy (5 types):**

| Type | Icon | When |
|------|------|------|
| `missing_info` | ❓ | Required info not provided |
| `ambiguous_requirement` | 🔄 | Request can be interpreted multiple ways |
| `approach_choice` | 🛤️ | Multiple valid approaches, user should pick |
| `risk_confirmation` | ⚠️ | Action has consequences, needs explicit OK |
| `suggestion` | 💡 | Agent has a recommendation to validate |

**What we should do:**
Add an `ask_user` tool that pauses the tool loop and sends a Telegram message asking for clarification. When user replies, resume the tool loop with the answer injected.

**This is high-value for SeekerClaw** because:
- Our agent runs 24/7 — wrong guesses waste time and money
- Telegram is async — users expect to be asked, not surprised
- The `risk_confirmation` type is perfect for Solana swap confirmations (we already have this pattern!)

**Effort:** Small. ~40 lines for the tool + message routing change.

### 3.5 Deferred Tool Loading

**DeerFlow approach:**
- MCP tools listed by name only in `<available-deferred-tools>` block
- Agent calls `tool_search` to fetch full schema when needed
- Keeps system prompt compact (critical with 50+ MCP tools)
- Query patterns: `select:tool1,tool2`, `+keyword`, regex

**What we should do:**
This is already in our plan. Critical as MCP adoption grows. Strategy:
- **Claude:** Native deferred tools (API supports it)
- **OpenAI:** Include tool names in system prompt, intercept calls, lazy-load schema
- **OpenRouter:** Same as OpenAI approach

**Effort:** Medium. Provider-specific implementation needed.

### 3.6 Subagent System

**DeerFlow approach:**
- Lead agent decomposes tasks, spawns via `task` tool
- Two types: `general-purpose` (full tools) and `bash` (command specialist)
- Max 3 concurrent, 15-minute timeout
- Isolated contexts (no cross-contamination)
- Results stream back with status events

**What we should do:**
**Not recommended for v1.** Reasons:
- Node.js 18 single-process constraint (no Docker on mobile)
- Would require multiple concurrent API calls (expensive on mobile data)
- Our single-agent loop with 56+ tools handles most use cases
- The `task` tool is DeerFlow's answer to Claude Code's Agent tool — we're Telegram-first, not IDE-first

**Future consideration:** If we ever add a web UI or desktop mode, subagents become viable.

### 3.7 Memory System

**DeerFlow approach:**
- LLM-powered extraction: user context, confidence-scored facts, history
- Structured JSON storage (work context, personal context, top-of-mind, facts with categories)
- Debounced updates (30s), deduplication, atomic writes
- Top 15 facts + context injected via `<memory>` tags
- Upload mentions stripped (session-scoped files don't persist)

**Comparison with SeekerClaw:**
| Feature | DeerFlow | SeekerClaw |
|---------|----------|------------|
| Storage | JSON (structured) | Markdown files (MEMORY.md + daily) |
| Extraction | LLM-powered with confidence scores | Agent writes directly |
| Dedup | Automatic | Manual (agent's judgment) |
| Injection | Top 15 facts in `<memory>` tags | Full file read |
| Debounce | 30s async queue | Immediate |

**What we could borrow:**
1. **Confidence-scored facts** — instead of raw daily memory files, extract structured facts with scores. Lower-confidence facts get pruned first.
2. **Debounced memory writes** — don't save memory on every message. Queue changes, batch-write every 30s. Reduces IO on mobile.
3. **Upload mention stripping** — don't persist references to temporary file paths in memory.

**Effort:** Large. This is a significant memory system overhaul. **Defer to post-v2.0.**

### 3.8 Skills System

**DeerFlow approach:**
- 17 built-in public skills (research, PPT, podcast, charts, consulting, video, image gen)
- SKILL.md with YAML frontmatter (same format as SeekerClaw!)
- Progressive loading — only inject when relevant (saves tokens)
- `.skill` archive installation via Gateway API
- `find-skills` skill for discovering from open ecosystem (skills.sh)
- `skill-creator` skill for collaborative skill development

**Comparison:**
| Feature | DeerFlow | SeekerClaw |
|---------|----------|------------|
| Format | SKILL.md + YAML | SKILL.md + YAML ✅ |
| Loading | Progressive (on-demand) | All at startup ✅ |
| Count | 17 built-in | ~5 user-created |
| Discovery | skills.sh marketplace | Manual upload |
| Creation | AI-assisted `skill-creator` | Agent writes directly |
| Archives | `.skill` bundles | Not supported |

**What we could borrow:**
1. **Skill marketplace discovery** — a `find_skill` tool that searches a curated registry. Could be a simple GitHub repo or API.
2. **Skill archives** — `.skill` bundles for one-click install via Telegram file upload.
3. **Notable skill ideas to port:**
   - `consulting-analysis` — McKinsey/BCG-style reports with SWOT, Porter's frameworks
   - `chart-visualization` — 26 chart types (would need a web renderer, maybe via Telegram's inline images)
   - `data-analysis` — DuckDB SQL engine for CSV/Excel analysis (SQL.js could do this!)
   - `surprise-me` — Creative mashup of skills into unexpected deliverables

**Effort:** Varies. Marketplace is Medium. Individual skills are Small each.

### 3.9 Sandbox Execution

**DeerFlow:** Full Docker containers with isolated filesystem, bash, file ops.
**SeekerClaw:** `shell_exec` runs on Android's toybox shell. No isolation.

**Gap analysis:**
- DeerFlow's Docker sandbox is their biggest architectural advantage
- We can't run Docker on mobile
- Our `js_eval` tool is the closest equivalent (sandboxed JS execution)
- `shell_exec` is already rate-limited and path-restricted

**What we could borrow:**
- **Virtual path system** — DeerFlow maps `/mnt/user-data/{workspace,uploads,outputs}`. We could formalize our workspace paths similarly, making the agent's mental model cleaner.
- **Present file tool** — Explicit "here's your deliverable" vs our current approach of just writing files.

### 3.10 IM Channel Integrations

**DeerFlow:** Telegram, Slack, Feishu/Lark. Per-user session config. Commands: `/new`, `/status`, `/models`, `/memory`, `/help`.

**SeekerClaw:** Telegram only (by design).

**What we could borrow:**
- `/new` command to start fresh conversation (clear context)
- `/status` command for quick health check
- `/memory` command to view/search memory
- Per-user session overrides (model, behavior per authorized user)

**These are small wins** — most are just Telegram command handlers.

---

## 4. Architecture Comparison

| Dimension | DeerFlow v2.0 | SeekerClaw v1.7.0 |
|-----------|--------------|-------------------|
| **Runtime** | Python 3.12+ (server) | Node.js 18 (Android) |
| **Framework** | LangGraph + LangChain | Raw API calls |
| **Agent type** | Multi-agent (lead + subagents) | Single agent |
| **UI** | Next.js web app | Telegram bot + Android app |
| **Sandbox** | Docker containers | Android toybox + js_eval |
| **Memory** | LLM-extracted structured JSON | Agent-written markdown |
| **MCP** | langchain-mcp-adapters | Custom mcp-client.js |
| **Models** | Any LangChain-compatible | Claude, OpenAI, OpenRouter |
| **Hosting** | Self-hosted server | On-device (phone) |
| **Skills** | 17 built-in, progressive load | User-created, all loaded |
| **Context mgmt** | Summarization middleware | Adaptive trim (lossy) |
| **Loop detection** | MD5 hash, warn@3, break@5 | None (MAX_TOOL_USES=25) |
| **Clarification** | 5-type tool + middleware | None |
| **Tool groups** | Named groups, per-agent | All tools for all |
| **Channels** | Telegram, Slack, Feishu | Telegram only |

---

## 5. What We Should NOT Borrow

| Feature | Why Not |
|---------|---------|
| **Subagent system** | Can't run parallel API calls efficiently on mobile. Single-agent is fine for Telegram. |
| **Docker sandbox** | No Docker on Android. Our js_eval + shell_exec is adequate. |
| **LangGraph/LangChain** | Heavy framework dependency. Our raw API approach is lighter and faster on mobile. |
| **Next.js frontend** | We're Telegram-first. Android app is for config only. |
| **DuckDB** | We already have SQL.js. No need for another embedded DB. |
| **Checkpointer (Postgres)** | Overkill for single-user on-device. File-based is fine. |
| **OAuth MCP** | Nice-to-have but no immediate user demand. |
| **Multi-user sessions** | SeekerClaw is personal (owner-only by design). |

---

## 6. Implementation Roadmap

### Phase 1 — Quick Wins (1-2 weeks)

| Feature | Effort | Impact | Files |
|---------|--------|--------|-------|
| Loop Detection | S | High — saves $$ | New `loop-detector.js`, modify `claude.js` |
| Clarification Tool | S | High — better UX | New tool in `tools/system.js`, modify `main.js` |
| `/new` command | XS | Medium — fresh context | `main.js` command handler |
| `/memory` command | XS | Medium — transparency | `main.js` command handler |

### Phase 2 — Core Improvements (2-4 weeks)

| Feature | Effort | Impact | Files |
|---------|--------|--------|-------|
| Context Summarization | M | Critical — prevents amnesia | `claude.js`, new `summarizer.js` |
| Deferred Tool Loading | M | High — MCP scalability | `providers/*.js`, `mcp-client.js` |
| TODO Persistence | S | Medium — survives summarization | `claude.js` |
| Debounced Memory Writes | S | Medium — less IO | `memory.js` |

### Phase 3 — Polish (4-6 weeks)

| Feature | Effort | Impact | Files |
|---------|--------|--------|-------|
| Skill Marketplace | M | High — ecosystem growth | New `skill-registry.js`, tool |
| Skill Archives (.skill) | S | Medium — easier install | `skills.js` |
| Virtual Path System | S | Low — cleaner mental model | `tools/file.js`, system prompt |
| Confidence-Scored Memory | L | Medium — smarter recall | `memory.js`, system prompt |

---

## 7. Key Insights

### What DeerFlow Does Better Than Us

1. **Context management** — Summarization + TODO persistence = agent never loses track. We just drop old messages.
2. **Loop prevention** — They detect and break loops. We burn through 25 iterations blindly.
3. **Clarification** — They ask before guessing. We guess and sometimes waste a full API turn.
4. **Tool scaling** — Deferred loading keeps prompt compact with 100+ tools. We load everything.
5. **Middleware architecture** — Clean separation of concerns. Our `claude.js` is a monolith.

### What We Do Better Than DeerFlow

1. **Mobile-native** — We run ON the phone, 24/7, no server needed. DeerFlow needs Docker + 4 services.
2. **Telegram-first** — Deep Telegram integration (reactions, inline keyboards, document sharing). DeerFlow treats Telegram as a secondary channel.
3. **Solana/crypto** — Jupiter DEX, MWA wallet, swap confirmations. DeerFlow has zero crypto.
4. **Battery-aware** — Heartbeat tuning, wake locks, boot receiver, watchdog. DeerFlow assumes unlimited power.
5. **Encryption** — Android Keystore (AES-256-GCM) for secrets. DeerFlow uses `.env` files.
6. **Single-user simplicity** — No auth complexity, no multi-tenant overhead. Personal agent.

### What Makes DeerFlow's Architecture Elegant

1. **Harness/App split** — The core agent framework is publishable as a library (`DeerFlowClient`). Clean boundary enforced by CI.
2. **Config-driven everything** — Models, tools, skills, memory, sandbox all configured via `config.yaml`. No code changes to swap providers.
3. **Middleware chain** — 11 ordered middlewares handle cross-cutting concerns. Each is independently testable.
4. **Progressive skill loading** — Skills injected only when relevant. Saves tokens on every turn.

---

## 8. Specific Code Patterns Worth Adopting

### Pattern 1: Tool Call Hash for Loop Detection
```javascript
// DeerFlow: MD5(sorted tool names + args), sliding window of 20
// We can copy this almost verbatim. Already in our plan.
```

### Pattern 2: Summarization Trigger (OR logic)
```javascript
// DeerFlow triggers on ANY of:
// - Token count > threshold
// - Message count > 50
// - Usage > 80% of model max
// We currently only check token percentage (90%). Add message count check.
```

### Pattern 3: Atomic Tool Pair Preservation
```javascript
// DeerFlow never splits AI message (with tool_calls) from its ToolMessage results
// During summarization, pairs are treated as atomic units
// Our adaptiveTrim already does this — good.
```

### Pattern 4: Clarification Types as Enum
```javascript
// Instead of free-form "ask user", categorize:
// missing_info | ambiguous | approach_choice | risk_confirmation | suggestion
// This helps the agent make better decisions about WHEN to ask
```

### Pattern 5: Deferred Tool Registry
```javascript
// Keep tool names in system prompt, full schemas in a registry
// Agent calls tool_search("web_scrape") → gets full schema → can now call it
// Reduces prompt size from ~50K tokens to ~5K when you have 100+ MCP tools
```

---

## 9. Competitive Positioning

| Feature | DeerFlow | SeekerClaw | Claude Code | OpenClaw |
|---------|----------|------------|-------------|----------|
| Runs on phone | ❌ | ✅ | ❌ | ❌ |
| 24/7 background | ❌ (needs server) | ✅ | ❌ | ✅ (server) |
| Sub-agents | ✅ | ❌ | ✅ | ❌ |
| Loop detection | ✅ | ❌ (planned) | ✅ | ❌ |
| Context summarization | ✅ | ❌ (planned) | ✅ | ❌ |
| Clarification tool | ✅ | ❌ (planned) | ✅ | ❌ |
| Deferred tools | ✅ | ❌ (planned) | ✅ | ❌ |
| Crypto/DeFi | ❌ | ✅ | ❌ | ❌ |
| Telegram-native | Partial | ✅ | ❌ | ✅ |
| MCP support | ✅ | ✅ | ✅ | ❌ |
| Skills system | ✅ (17) | ✅ (~5) | ✅ | ✅ |
| Memory | ✅ (LLM-extracted) | ✅ (agent-written) | ❌ | ✅ |
| Open source | ✅ | ✅ | ✅ | ✅ |

**SeekerClaw's moat:** Only mobile-native 24/7 AI agent with crypto capabilities. DeerFlow can't touch this.

**SeekerClaw's gap:** Context engineering (summarization, loop detection, clarification). DeerFlow is ahead here. **Close this gap in Phase 1-2.**

---

## 10. Action Items

- [ ] **Ship loop detection** — Code is written in DEERFLOW_FEATURES_PLAN.md. Just integrate.
- [ ] **Add clarification tool** — 5-type taxonomy, pause tool loop, ask via Telegram.
- [ ] **Build context summarization** — 80% threshold, preserve last 20 messages, use Haiku for summary.
- [ ] **Implement deferred tool loading** — Per-provider strategy (Claude native, OpenAI/OR via system prompt).
- [ ] **Add TODO persistence** — Inject active task list after summarization.
- [ ] **Add `/new` and `/memory` Telegram commands** — Quick wins.
- [ ] **Create Linear tickets** for Phase 1 items.

---

*Generated from deep audit of https://github.com/bytedance/deer-flow (32,400 stars, v2.0)*
*Cross-referenced with SeekerClaw v1.7.0 codebase and existing DEERFLOW_FEATURES_PLAN.md*
