# Quick Actions — Telegram Preset Task Buttons

> **Status:** Planned | **Date:** 2026-03-22
> **Inspired by:** QClaw "Inspiration Square" ([audit](QCLAW_AUDIT.md))
> **Effort:** Small (~80 lines JS, ~20 lines Kotlin)
> **Risk:** Low — additive, uses existing Telegram inline keyboard API

## Problem

Users open Telegram, stare at the chat, and think "what do I say?" The blank text box is intimidating — especially for non-technical users. QClaw solved this with "Inspiration Square": ~20 preset task templates that execute with one tap. Zero typing, zero prompt engineering.

## Solution

Add a `/quick` command (or auto-show on idle) that sends a Telegram inline keyboard with preset action buttons. Each button sends a pre-formatted message to the agent as if the user typed it.

## Button Layout

Two rows of 3 buttons, covering the most common use cases:

```
┌──────────────┬──────────────┬──────────────┐
│ 🔋 Status    │ 💰 Portfolio │ 📊 SOL Price │
├──────────────┼──────────────┼──────────────┤
│ 📰 News Brief│ ⏰ My Tasks  │ 🧠 Memory    │
└──────────────┴──────────────┴──────────────┘
```

| Button | Sends to Agent | What Agent Does |
|--------|---------------|-----------------|
| 🔋 Status | "Quick status check — battery, storage, uptime, last message time" | Calls `android_battery`, `android_storage`, `session_status` |
| 💰 Portfolio | "Check my Solana portfolio — balances and total USD value" | Calls `jupiter_wallet_holdings` or `solana_balance` |
| 📊 SOL Price | "What's the current SOL price?" | Calls `solana_price` |
| 📰 News Brief | "Give me a 3-sentence summary of today's top crypto/tech news" | Calls `web_search` + summarizes |
| ⏰ My Tasks | "List my scheduled tasks and any pending TODOs" | Calls `cron_list` + `read_todos` |
| 🧠 Memory | "What do you remember about me? Summarize key facts." | Calls `memory_read` + summarizes |

## Implementation

### Node.js (`main.js`)

**Command handler for `/quick`:**
```javascript
if (text === '/quick') {
    await telegram('sendMessage', {
        chat_id: chatId,
        text: '⚡ Quick Actions — tap to run:',
        reply_markup: {
            inline_keyboard: [
                [
                    { text: '🔋 Status', callback_data: 'quick:status' },
                    { text: '💰 Portfolio', callback_data: 'quick:portfolio' },
                    { text: '📊 SOL Price', callback_data: 'quick:sol_price' },
                ],
                [
                    { text: '📰 News Brief', callback_data: 'quick:news' },
                    { text: '⏰ My Tasks', callback_data: 'quick:tasks' },
                    { text: '🧠 Memory', callback_data: 'quick:memory' },
                ],
            ],
        },
    });
    return;
}
```

**Callback query handler** (in Telegram polling loop):
```javascript
// Map callback_data → pre-formatted user message
const QUICK_ACTIONS = {
    'quick:status': 'Quick status check — battery, storage, uptime, last message time',
    'quick:portfolio': 'Check my Solana portfolio — balances and total USD value',
    'quick:sol_price': "What's the current SOL price?",
    'quick:news': 'Give me a 3-sentence summary of today\'s top crypto/tech news',
    'quick:tasks': 'List my scheduled tasks and any pending TODOs',
    'quick:memory': 'What do you remember about me? Summarize key facts.',
};
```

When a callback query comes in:
1. Answer the callback (remove loading spinner)
2. Delete the inline keyboard message (clean up)
3. Send the mapped text as a regular user message into `handleMessage()`

### Kotlin (`main.js` polling already handles callback_query)

No Kotlin changes needed — callback queries are already part of Telegram's `getUpdates` response. The Node.js polling loop in `main.js` already processes updates. We just need to handle `callback_query` type in addition to `message` type.

### System Prompt

Add to `buildSystemBlocks()`:
```
Quick Actions: Users may send pre-formatted messages via /quick buttons.
Treat these exactly like regular messages — respond naturally and use tools as needed.
```

## Customization (Future)

- Users could customize buttons via Settings or a skill
- Agent could suggest adding frequently-used queries as quick actions
- Buttons could be context-aware (show "Resume Task" if a checkpoint exists)

## Verification

1. Send `/quick` → verify 6 buttons appear
2. Tap each button → verify agent receives correct message and responds
3. Verify inline keyboard disappears after tap (clean UX)
4. Test on both dappStore and googlePlay flavors
