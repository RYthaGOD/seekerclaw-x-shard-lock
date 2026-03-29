# SAB-AUDIT-v10 — shardclaw Agent Self-Knowledge Audit

> **Date:** 2026-03-06
> **Scope:** Re-audit after BAT-315 (multi-provider architecture — Claude/OpenAI adapter pattern, OpenAI Responses API streaming, Codex support, provider-agnostic DB logging) + 4 smoke-test bug fixes
> **Method:** Full read of buildSystemBlocks() + constants verification + diagnostic coverage map
> **Baseline:** SAB-AUDIT-v9.md (111/111, 100%)

---

## Overall Scorecard

| Section | Pre-fix | Post-fix | Max | Percentage | Delta |
|---------|---------|----------|-----|-----------|-------|
| A: Knowledge & Doors | 63 | 63 | 63 | 100% | — (held) |
| B: Diagnostic Coverage | 48 | 48 | 48 | 100% | — (held, cosmetic fix) |
| **Combined** | **111** | **111** | **111** | **100%** | — |

Scoring: ✅ = 3 pts, ⚠️ = 1 pt, ❌ = 0 pts.

---

## Constants Verification

| Constant | Code Value | Prompt Claim | Match |
|----------|-----------|-------------|-------|
| MAX_TOOL_USES | 25 (claude.js:1459) | "Up to 25 tool-call rounds" (line 814) | ✅ |
| MAX_HISTORY | 35 (claude.js:172) | "35 messages per chat" (line 813) | ✅ |
| max_tokens | 4096 (claude.js:1473) | "4096 tokens per response" (line 815) | ✅ |
| SHELL_ALLOWLIST | 34 commands (config.js:241-248) | 34 commands listed (line 649) | ✅ |
| SECRETS_BLOCKED | config.json, config.yaml, shardclaw.db (config.js:232) | shardclaw.db listed as BLOCKED (line 565) | ✅ |
| BLOCKED_MODULES | 7 modules (tools.js) | Lists all 7 (line 422) | ✅ |
| js_eval code limit | 10,000 chars (tools.js) | "10,000-character code limit" (line 650) | ✅ |

---

## Section A: Knowledge & Doors (21 items)

### Changes Since v9

BAT-315 is a multi-provider architecture PR. Evaluated against the 3-part door test:

| Change | What Shipped | New Door? | Reason |
|--------|-------------|-----------|--------|
| Provider adapter pattern | Claude/OpenAI adapters in `providers/` | No | System prompt already dynamic: `Provider: ${PROVIDER}, Model: ${MODEL}` (line 570) |
| OpenAI Responses API streaming | SSE streaming for OpenAI models | No | Transparent infrastructure — agent doesn't need to know streaming protocol |
| Codex support | GPT-5.3 Codex with reasoning parameter | No | Model identity shown dynamically; no model-specific behavior guidance needed |
| Provider-agnostic DB logging | API logs work for all providers | No | Transparent infrastructure |
| Credential hygiene | Provider-specific key storage | No | Agent already directed to check Settings for keys |
| Provider-aware diagnosis | Billing/connectivity URLs adapt to provider | Already covered | Lines 659-660 use `${PROVIDER}` dynamically |
| Model memory per provider | Kotlin SharedPreferences fix | No | Android-side UX, no agent knowledge needed |
| /status message count fix | Bug fix in getSessionTrack() | No | Bug fix, not a capability change |
| GitHub skill ungating | Removed env requirement | No | Skill just works now, no door needed |

**0 new doors added.** Item count remains at 21.

### Full Item Scores

**Identity (5/5)**

| # | Item | v7 | v8 | v9 | v10 | Notes |
|---|------|----|----|-----|------|-------|
| 1 | Own name/version | ✅ | ✅ | ✅ | ✅ | Lines 381-382 |
| 2 | Model | ✅ | ✅ | ✅ | ✅ | PLATFORM.md (auto-generated) + line 570 |
| 3 | Device/hardware | ✅ | ✅ | ✅ | ✅ | PLATFORM.md |
| 4 | Who built it | ✅ | ✅ | ✅ | ✅ | Line 383 (OpenClaw) |
| 5 | Official channels | ✅ | ✅ | ✅ | ✅ | Line 384 |

**Architecture (4/4)**

| # | Item | v7 | v8 | v9 | v10 | Notes |
|---|------|----|----|-----|------|-------|
| 6 | Node↔Kotlin bridge | ✅ | ✅ | ✅ | ✅ | Lines 388-393 |
| 7 | UI vs :node process | ✅ | ✅ | ✅ | ✅ | Lines 389-391 |
| 8 | Health monitoring | ✅ | ✅ | ✅ | ✅ | Lines 588-593 |
| 9 | Telegram polling | ✅ | ✅ | ✅ | ✅ | Lines 447-451 |

**Capabilities (4/4)**

| # | Item | v7 | v8 | v9 | v10 | Notes |
|---|------|----|----|-----|------|-------|
| 10 | Full tool list | ✅ | ✅ | ✅ | ✅ | Lines 405-426 |
| 11 | Sandboxed tools | ✅ | ✅ | ✅ | ✅ | Line 422 (7 blocked modules + path restrictions) |
| 12 | What it cannot do | ✅ | ✅ | ✅ | ✅ | Lines 794-799 |
| 13 | Skills load/trigger | ✅ | ✅ | ✅ | ✅ | Lines 465-478 |

**Configuration (4/4)**

| # | Item | v7 | v8 | v9 | v10 | Notes |
|---|------|----|----|-----|------|-------|
| 14 | Config files | ✅ | ✅ | ✅ | ✅ | Lines 554-566 |
| 15 | Settings agent can change | ✅ | ✅ | ✅ | ✅ | Lines 568-585 |
| 16 | API keys needed | ✅ | ✅ | ✅ | ✅ | Lines 570-572 |
| 17 | Model/heartbeat change | ✅ | ✅ | ✅ | ✅ | Line 570 (now includes Provider) |

**Self-Diagnosis (4/4)**

| # | Item | v7 | v8 | v9 | v10 | Notes |
|---|------|----|----|-----|------|-------|
| 18 | Health stale | ✅ | ✅ | ✅ | ✅ | Lines 637-641 |
| 19 | Telegram disconnects | ✅ | ✅ | ✅ | ✅ | Lines 624-629 |
| 20 | Skill fails | ✅ | ✅ | ✅ | ✅ | Lines 631-635 |
| 21 | Conversation corruption | ✅ | ✅ | ✅ | ✅ | Lines 643-647 |

**Section A Total: 63/63 (100%)**

---

## Section B: Diagnostic Coverage (16 failure modes)

All 16 failure modes from v9 held at ✅. No regressions.

| # | Subsystem | Failure Mode | v7 | v8 | v9 | v10 | Coverage Location |
|---|-----------|-------------|----|----|-----|------|-------------------|
| 1 | Telegram | Bot token invalid/revoked | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Telegram |
| 2 | Telegram | Rate limited (429) | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Telegram |
| 3 | LLM API | Transport timeout | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §LLM API |
| 4 | LLM API | Context overflow | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §LLM API |
| 5 | Tools | Confirmation gate timeout | ✅ | ✅ | ✅ | ✅ | System prompt lines 507-511 |
| 6 | Tools | Tool result truncated | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Tools |
| 7 | Memory | memory_save fails (fs full) | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Memory |
| 8 | Memory | memory_search returns nothing | ✅ | ✅ | ✅ | ✅ | System prompt lines 515-521 |
| 9 | Cron | Job fails to send reminder | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Cron |
| 10 | Cron | Jobs lost after restart | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Cron |
| 11 | Bridge | Service down (ECONNREFUSED) | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Bridge |
| 12 | Bridge | Permission-specific errors | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Bridge |
| 13 | MCP | Server unreachable | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §MCP |
| 14 | MCP | Tool definition changed (rug-pull) | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §MCP |
| 15 | MCP | Rate limit exceeded | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §MCP |
| 16 | Skills | Requirements not met | ✅ | ✅ | ✅ | ✅ | DIAGNOSTICS.md §Skills |

**Section B Total: 48/48 (100%)**

### New Failure Modes Check

| Change | Potential Failure Mode | Assessment |
|--------|----------------------|------------|
| Provider adapter pattern | Wrong adapter loaded | providerById() has explicit fallback to Claude adapter — not a diagnosable failure |
| OpenAI Responses API | Different error format | classifyError() in openai.js handles all HTTP status codes — same error surface |
| OpenAI streaming | Different SSE events | streamProtocol field routes to correct parser — transparent infrastructure |
| Codex reasoning parameter | Reasoning causes errors on non-codex models | Only added for models containing 'codex' — not a failure mode |
| Provider credential mismatch | OpenAI key missing when on OpenAI | Existing "Auth error (401/403)" playbook covers this (line 657) |

No new failure modes to add.

---

## Gaps Fixed

| File | Change | Issue | Fix |
|------|--------|-------|-----|
| DIAGNOSTICS.md | Line 30 | Section titled "Claude API" — stale after multi-provider (BAT-315) | Renamed to "LLM API (Claude / OpenAI)" |

---

## Code Issues Found

None.

---

## Remaining Gaps

**None.** All 21 knowledge items and all 16 diagnostic failure modes score ✅. The system prompt's dynamic `${PROVIDER}` and `${MODEL}` injection (line 570) and provider-aware diagnosis playbook (lines 659-660) ensure the agent adapts to any provider without additional doors.

---

## Observations

1. **BAT-315 is a clean abstraction.** The provider adapter pattern keeps all provider-specific logic in `providers/*.js` files. The system prompt doesn't need to know about providers because it already uses dynamic variables (`${PROVIDER}`, `${MODEL}`) that adapt automatically.

2. **No prompt bloat.** Despite adding OpenAI support (a major feature), zero new system prompt lines were needed. This validates the design of using dynamic injection over static descriptions.

3. **DIAGNOSTICS.md cosmetic fix.** The only change was renaming the "Claude API" section header to "LLM API (Claude / OpenAI)" for clarity when the agent is running on OpenAI. The diagnosis steps themselves (check trace logs, check latency, check network) are provider-agnostic.

4. **7 consecutive 100% audits** (v4→v10 post-fix). The system prompt and DIAGNOSTICS.md continue to be well-maintained.

---

## Score Progression

```
        Knowledge (Section A)               Diagnostics (Section B)
v1  ████████████████████░░░░░░░░░░  42/60  (70%)    (not audited)
v2  ████████████████████████████░░  56/60  (93%)    (not audited)
v3  ██████████████████████████████  60/60 (100%)    (not audited)
v4  ██████████████████████████████  60/60 (100%)    ██████████████████████████████  48/48 (100%)
v5  ██████████████████████████████  60/60 (100%)    ██████████████████████████████  48/48 (100%)
v6  ██████████████████████████████  63/63 (100%)    ██████████████████████████████  48/48 (100%)
v7  ██████████████████████████████  63/63 (100%)    ██████████████████████████████  48/48 (100%)
v8  ██████████████████████████████  63/63 (100%)    ██████████████████████████████  48/48 (100%)
v9  ██████████████████████████████  63/63 (100%)    ██████████████████████████████  48/48 (100%)
v10 ██████████████████████████████  63/63 (100%)    ██████████████████████████████  48/48 (100%)

Combined SAB Score:
v1  ██████████████████████████████░░░░░░░░░░░░░░░░░░░░  42/60   (70%)
v2  ██████████████████████████████████████████████░░░░  56/60   (93%)
v3  ██████████████████████████████████████████████████  60/60  (100%)
v4  ██████████████████████████████████████████████████ 108/108 (100%)
v5  ██████████████████████████████████████████████████ 108/108 (100%)  [SHELL_ALLOWLIST 22→34]
v6  ██████████████████████████████████████████████████ 111/111 (100%)  [+Official channels]
v7  ██████████████████████████████████████████████████ 111/111 (100%)  [DIAGNOSTICS.md MCP rate fix]
v8  ██████████████████████████████████████████████████ 111/111 (100%)  [enriched restart flushing door]
v9  ██████████████████████████████████████████████████ 111/111 (100%)  [fixed stale js_eval sandbox desc]
v10 ██████████████████████████████████████████████████ 111/111 (100%)  [DIAGNOSTICS.md provider-agnostic header]
```

---

## Methodology

- **Source-only audit** — all scores derived from code reads, no runtime testing
- **Files read:** claude.js buildSystemBlocks() (lines 335-870), providers/openai.js (full), providers/claude.js (referenced), config.js (SHELL_ALLOWLIST, SECRETS_BLOCKED), DIAGNOSTICS.md (full 198 lines), SAB-AUDIT-v9.md (baseline), PROJECT.md changelog
- **Constants verified:** MAX_TOOL_USES=25, MAX_HISTORY=35, max_tokens=4096, SHELL_ALLOWLIST=34 commands, SECRETS_BLOCKED=3 files, BLOCKED_MODULES=7 modules, js_eval code limit=10,000 chars
- **Syntax verified:** `node -c claude.js` — pass
