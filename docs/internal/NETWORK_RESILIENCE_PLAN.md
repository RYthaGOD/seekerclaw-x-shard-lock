# Network Resilience — Mobile-Aware Error Recovery

> **Status:** Planned | **Date:** 2026-03-22
> **Inspired by:** QClaw `qclaw-env` skill ([audit](QCLAW_AUDIT.md))
> **Effort:** Small-Medium (~100 lines JS modifications)
> **Risk:** Low — extends existing `classifyNetworkError()` in claude.js

## Problem

SeekerClaw runs 24/7 on a phone. Mobile networks are inherently unreliable:
- Wi-Fi → cellular handoff drops connections mid-API-call
- Tunnel/elevator/subway = total connectivity loss for seconds to minutes
- Cellular data can be metered — large `web_fetch` results burn user's plan
- API rate limits hit differently on high-latency mobile connections (retries pile up)
- Telegram polling silently fails on network change, causing "deaf" periods

Currently, `classifyNetworkError()` in claude.js (lines 2031-2034) catches errors and sanitizes them for the user, but it doesn't **adapt behavior** based on network conditions. The agent just fails and shows an error.

QClaw's `qclaw-env` skill auto-detects network issues and switches strategies (mirror switching, retry policies). We need the mobile equivalent.

## Solution

Three-layer network resilience system built on top of existing infrastructure:

### Layer 1: Smart Retry with Exponential Backoff

**Current behavior:** API call fails → error thrown → user sees "Network error."

**New behavior:** API call fails → classify error → retry with backoff if transient → only show error after retries exhausted.

```javascript
// In claude.js, wrap claudeApiCall() with retry logic
async function resilientApiCall(body, chatId, opts) {
    const MAX_RETRIES = 2;           // Max 2 retries (3 total attempts)
    const BASE_DELAY_MS = 1000;      // 1s, 2s backoff

    for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        try {
            const res = await claudeApiCall(body, chatId, opts);

            // Retry on 429 (rate limit) and 529 (overloaded)
            if ((res.status === 429 || res.status === 529) && attempt < MAX_RETRIES) {
                const retryAfter = parseRetryAfter(res) || BASE_DELAY_MS * (attempt + 1);
                log(`[Network] ${res.status} — retry ${attempt + 1}/${MAX_RETRIES} in ${retryAfter}ms`, 'WARN');
                await sleep(retryAfter);
                continue;
            }

            // Retry on 500/502/503 (server errors)
            if (res.status >= 500 && res.status < 600 && attempt < MAX_RETRIES) {
                log(`[Network] ${res.status} — retry ${attempt + 1}/${MAX_RETRIES} in ${BASE_DELAY_MS * (attempt + 1)}ms`, 'WARN');
                await sleep(BASE_DELAY_MS * (attempt + 1));
                continue;
            }

            return res;
        } catch (err) {
            // Network-level errors (ECONNRESET, ETIMEDOUT, ENOTFOUND)
            if (attempt < MAX_RETRIES && isTransientNetworkError(err)) {
                log(`[Network] ${err.code || err.message} — retry ${attempt + 1}/${MAX_RETRIES}`, 'WARN');
                await sleep(BASE_DELAY_MS * (attempt + 1));
                continue;
            }
            throw err;
        }
    }
}
```

**Transient errors to retry:** `ECONNRESET`, `ETIMEDOUT`, `ECONNREFUSED`, `EPIPE`, `EAI_AGAIN`, `EHOSTUNREACH`, `ENETUNREACH`, `socket hang up`.

**Non-transient errors (no retry):** `ENOTFOUND` (DNS failure — likely no internet), 400/401/403 (client errors).

### Layer 2: Connectivity-Aware Tool Behavior

**Current behavior:** `web_fetch` always fetches full page content (up to 50KB).

**New behavior:** Check network status via Android Bridge before large operations.

```javascript
// Before web_fetch of large content:
const network = await androidBridgeCall('/network');
if (network.type === 'cellular' && !network.unmetered) {
    // On metered cellular, cap web_fetch to 10KB instead of 50KB
    maxBytes = 10 * 1024;
    log('[Network] Metered cellular — capping web_fetch to 10KB', 'DEBUG');
}
if (!network.connected) {
    return { error: 'No network connectivity. The device appears to be offline.' };
}
```

**Integration point:** `tools/web.js` → `web_fetch` handler. Check Android Bridge `/network` endpoint before fetch.

### Layer 3: Telegram Polling Recovery

**Current behavior:** If Telegram polling fails (network drop), the bot stops receiving messages until the polling loop reconnects. Users experience "deaf" periods.

**New behavior:** Detect polling failures, force-reconnect with fresh offset.

```javascript
// In main.js Telegram polling loop:
// After a polling error, instead of just retrying with same offset:
pollingErrors++;
if (pollingErrors >= 3) {
    log('[Telegram] 3 consecutive polling failures — forcing reconnect', 'WARN');
    // Get fresh offset by calling getUpdates with offset=-1
    await telegram('getUpdates', { offset: -1, limit: 1, timeout: 0 });
    pollingErrors = 0;
}
```

**Also:** After successful poll, notify user if there was a connectivity gap:
```javascript
if (wasOffline && now - lastSuccessfulPoll > 60000) {
    // Been offline >1 minute, just came back
    log(`[Network] Connectivity restored after ${Math.round((now - lastSuccessfulPoll) / 1000)}s gap`, 'INFO');
    // Don't spam user — just log it. They'll see the agent responding again.
}
```

## Files to Change

| File | Change | Lines (approx) |
|------|--------|----------------|
| `claude.js` | Wrap `claudeApiCall` with `resilientApiCall` + retry logic | ~40 lines new |
| `claude.js` | Add `isTransientNetworkError()` helper | ~15 lines new |
| `claude.js` | Add `parseRetryAfter()` helper | ~10 lines new |
| `tools/web.js` | Add network check before `web_fetch` large content | ~15 lines modified |
| `main.js` | Add polling failure counter + reconnect logic | ~20 lines modified |

**Total:** ~100 lines, all JS modifications. No Kotlin changes. No Gradle sync.

## Edge Cases

| Scenario | Handling |
|----------|----------|
| Complete network loss during API call | Retry 2x with backoff, then surface clean error to user |
| Wi-Fi → cellular handoff mid-request | `ECONNRESET` caught, retry succeeds on cellular |
| 429 rate limit with Retry-After header | Parse header, wait specified duration, retry |
| Metered cellular + large web_fetch | Cap to 10KB, log warning |
| Telegram polling dead for 5+ minutes | Force reconnect after 3 consecutive failures |
| Android Bridge `/network` unreachable | Fallback to no-check behavior (assume connected) |

## Verification

1. **Retry logic:** Enable airplane mode briefly during API call → verify retry happens → re-enable → verify success
2. **Metered cap:** Switch to cellular → trigger `web_fetch` → verify 10KB cap in logs
3. **Polling recovery:** Kill Wi-Fi for 30s → verify agent resumes receiving messages after reconnect
4. **Rate limit:** Trigger 429 → verify backoff + retry → verify eventual success
5. **No regression:** Run full test suite on stable Wi-Fi → verify no behavior change
