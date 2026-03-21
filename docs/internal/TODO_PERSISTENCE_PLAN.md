# TODO Persistence — Implementation Plan

> **Status:** Approved | **Date:** 2026-03-22
> **Inspired by:** DeerFlow v2.0 `TodoMiddleware` ([audit](DEERFLOW_AUDIT.md))
> **Effort:** Small-Medium (~200 lines new, ~30 lines modifications)
> **Risk:** Low — additive, no existing behavior changed

## Context

When the agent works on multi-step tasks, the plan exists only as text in conversation messages. When `adaptiveTrim()` fires at 90% context usage (claude.js:1738), old messages — including the original plan — are silently dropped. The agent loses track of remaining steps and either stops early or asks the user to repeat themselves. DeerFlow solves this with a `write_todos` tool that stores TODOs in thread state (outside messages) + a middleware that re-injects them after trim. We adopt the same approach, adapted for our Node.js/Telegram architecture.

---

## Architecture Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Storage | In-memory Map + JSON file per chat | Follows task-store.js pattern. Per-chat files = simple cleanup |
| Tool design | `write_todos` (full replacement) + `read_todos` (explicit fetch) | DeerFlow-proven. No IDs needed. Agent sends full state each time |
| Reminder injection | Inside tool loop, after adaptiveTrim, before API call | Must react to mid-loop trims. buildSystemBlocks runs once per turn (too early) |
| Schema | `[{task, status}]` with 3 statuses | Simple. pending/in_progress/done |
| Cleanup | Auto-clear on successful task completion | Matches clearActiveTask pattern |
| Staleness | 24h max age, cleaned on startup | TODOs are for active tasks, not long-term |

---

## Files to Change

### 1. NEW: `tools/todo.js` (~150 lines)

Core TODO module. Follows exact pattern of `tools/cron.js`.

**Exports:** `tools`, `handlers`, `getTodos`, `clearTodos`, `buildTodoReminder`, `restoreTodos`, `cleanupStaleTodos`

**Tools:**

```javascript
// write_todos — Full list replacement (DeerFlow style)
{
  name: 'write_todos',
  description: 'Create or update your TODO list for tracking multi-step tasks. Replaces the entire list. '
    + 'This list is stored OUTSIDE the conversation and survives context trimming and restarts. '
    + 'Send an empty array to clear.',
  input_schema: {
    type: 'object',
    properties: {
      todos: {
        type: 'array',
        items: {
          type: 'object',
          properties: {
            task: { type: 'string', description: 'Task description (max 500 chars)' },
            status: { type: 'string', enum: ['pending', 'in_progress', 'done'], default: 'pending' }
          },
          required: ['task']
        },
        description: 'Complete TODO list. Send empty array to clear.'
      }
    },
    required: ['todos']
  }
}

// read_todos — Explicit fetch (for resume / uncertainty)
{
  name: 'read_todos',
  description: 'Read your current TODO list. Use after restart or when unsure of task progress.',
  input_schema: { type: 'object', properties: {} }
}
```

**Key functions:**

- `todoState` Map: `chatId → { items: [{task, status}], updatedAt }`
- `getTodos(chatId)` — returns from Map, lazy-loads from disk if missing
- `saveTodosToDisk(chatId)` — atomic write (tmp → bak → rename), same pattern as task-store.js:22-129
- `clearTodos(chatId)` — deletes from Map + disk
- `restoreTodos(chatId, items)` — used on resume to populate Map from checkpoint
- `cleanupStaleTodos()` — delete files >24h old, called on startup
- `buildTodoReminder(chatId, messages)` — the key function:

```javascript
function buildTodoReminder(chatId, messages) {
    const todos = getTodos(chatId);
    if (!todos || todos.items.length === 0) return '';
    const pending = todos.items.filter(i => i.status !== 'done');
    if (pending.length === 0) return '';
    // Is write_todos still visible in messages?
    const visible = messages.some(msg =>
        msg.role === 'assistant' && msg.toolCalls &&
        msg.toolCalls.some(tc => tc.name === 'write_todos')
    );
    if (visible) return '';  // Agent can still see it, no reminder needed
    // Build reminder
    const lines = ['\n\n<todo_reminder>',
        'Your TODO list from earlier (preserved after context trim):'];
    for (const item of todos.items) {
        const mark = item.status === 'done' ? '[x]' : item.status === 'in_progress' ? '[~]' : '[ ]';
        lines.push(`${mark} ${item.task}`);
    }
    lines.push('Continue from the first unchecked item. Update with write_todos as you progress.');
    lines.push('</todo_reminder>');
    return lines.join('\n');
}
```

**Validation:** task capped at 500 chars, max 50 items, status enum enforced.

---

### 2. MODIFY: `tools/index.js`

**Line 18** — add import:
```javascript
const todoMod     = require('./todo');
```

**Line 32** — add to TOOLS array:
```javascript
    ...todoMod.tools,
```

**Line 47** — add to handlerMap:
```javascript
    todoMod.handlers,
```

---

### 3. MODIFY: `claude.js`

**4 changes:**

**(a) Import** (around line 33):
```javascript
const { clearTodos, buildTodoReminder, getTodos } = require('./tools/todo');
```

**(b) System prompt section** in `buildSystemBlocks()` — insert after "Conversation Limits" section (after line 918, before MCP section at line 920):
```javascript
// Task Planning — TODO persistence that survives context trimming
lines.push('## Task Planning');
lines.push('For multi-step tasks (3+ steps), use `write_todos` to create a TODO list before starting.');
lines.push('Update with `write_todos` as you complete steps. Your TODO list is stored outside the conversation — it survives context trimming and restarts.');
lines.push('If context is trimmed and your plan is lost, the system auto-injects your TODO list as a reminder.');
lines.push('When all items are done, call write_todos with an empty array to clear.');
lines.push('');
```

**(c) Tool loop** — TODO reminder injection. Between line 1743 (sanitize after trim) and line 1746 (toApiMessages). This is the critical integration:

```javascript
// TODO Persistence: if todos exist but write_todos was trimmed, rebuild system blocks with reminder
let effectiveSystemBlocks = systemBlocks;
const todoReminder = buildTodoReminder(chatId, messages);
if (todoReminder) {
    effectiveSystemBlocks = adapter.formatSystemPrompt(
        stablePrompt, dynamicPrompt + resumeBlock + todoReminder, AUTH_TYPE
    );
    _ctxCache.systemChars = JSON.stringify(effectiveSystemBlocks).length;
    log(`[TODO] Injected reminder for chatId=${chatId} | turnId=${turnId}`, 'DEBUG');
}

const apiMessages = adapter.toApiMessages(messages);
const body = adapter.formatRequest(MODEL, 4096, effectiveSystemBlocks, apiMessages, formattedTools);
```

Note: `stablePrompt`, `dynamicPrompt`, `resumeBlock` are already local variables in scope (lines 1665, 1669, 1695).

**(d) Cleanup on task completion** — line 2009, after `cleanupChatCheckpoints(chatId)`:
```javascript
if (toolUseCount > 0) {
    cleanupChatCheckpoints(chatId);
    clearTodos(chatId);  // Clear TODO list on successful completion
}
```

Do NOT clear TODOs on error (line 2029) or budget exhaustion (line 1902) — the agent may resume.

---

### 4. MODIFY: `config.js`

Add `TODOS_DIR` constant (after `TASKS_DIR` ~line 198):
```javascript
const TODOS_DIR = path.join(workDir, 'todos');
```

Create directory on startup (after `TASKS_DIR` mkdir ~line 210):
```javascript
if (!fs.existsSync(TODOS_DIR)) fs.mkdirSync(TODOS_DIR, { recursive: true });
```

Add to exports.

---

### 5. MODIFY: `main.js` (resume path)

Where checkpoint is restored and `chat()` is called with `isResume: true`, restore TODO state:

```javascript
if (checkpoint.todos && checkpoint.todos.length > 0) {
    const { restoreTodos } = require('./tools/todo');
    restoreTodos(checkpoint.chatId, checkpoint.todos);
}
```

---

### 6. MODIFY: `claude.js` checkpoint save (line 1880)

Add `todos` field:
```javascript
todos: getTodos(chatId)?.items || null,
```

---

## How It Works End-to-End

```
User: "Research memecoins, write report, check portfolio, send summary"

Turn 1 (agent sees 3+ steps):
  → Agent calls write_todos([
       {task: "Research top memecoins", status: "in_progress"},
       {task: "Write report", status: "pending"},
       {task: "Check portfolio", status: "pending"},
       {task: "Send summary", status: "pending"}
     ])
  → Saved to Map + workspace/todos/{chatId}.json
  → Agent starts researching...

Turn 1, iteration 15 (context at 92%):
  → adaptiveTrim() removes oldest 12 messages (including write_todos call)
  → buildTodoReminder() checks: todos exist? YES. write_todos visible? NO.
  → Injects <todo_reminder> into system prompt with current state
  → Agent sees: "[x] Research, [x] Write report, [ ] Check portfolio, [ ] Send summary"
  → Agent continues with "Check portfolio" — never loses track

Task completes:
  → clearTodos(chatId) — file deleted, Map entry removed
```

---

## Edge Cases

| Scenario | Handling |
|----------|----------|
| TODO file corruption | Atomic write (tmp→bak→rename) + .bak fallback |
| Process restart mid-TODO | Loaded from disk on next `getTodos()` call |
| Multiple chats with TODOs | Per-chat files, Map keyed by chatId |
| Cron sessions | Works but agent naturally won't need TODOs for single-turn |
| Reminder + context near limit | Reminder is ~200 tokens. Worst case triggers another trim pass (loop runs up to 3) |
| Very large TODO list | Max 50 items, 500 chars/task enforced in validation |
| All items done | `buildTodoReminder` returns empty string (no injection) |

---

## Verification

1. **Build & run** — no Gradle sync needed (JS-only changes)
2. Send agent a multi-step request (4+ steps) → verify `write_todos` tool call appears
3. Continue conversation until context trim fires (check logs for `[Context] Adaptive trim`) → verify `[TODO] Injected reminder` in logs
4. Kill process, restart, send "continue" → verify agent loads TODOs and continues
5. After task completes → verify `workspace/todos/{chatId}.json` is deleted
6. Test with all 3 providers (Claude, OpenAI, OpenRouter)
