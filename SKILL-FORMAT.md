# shardclaw Skill Format Specification

## Standard Format

Every skill MUST use YAML frontmatter:

```yaml
---
name: skill-name
description: "What the skill does — AI reads this to decide when to use the skill"
version: "1.0.0"
emoji: "🔧"
image: "https://shardclaw.xyz/assets/partner-skills/skill-name.jpg"
requires:
  bins: []
  env: []
  config: []
allowed-tools:
  - tool1
  - tool2
---
```

## Field Reference

| Field | Required | Description |
|-------|----------|-------------|
| name | YES | Unique kebab-case identifier |
| description | YES | One-line description. This is the PRIMARY trigger — Claude reads it to decide when to use the skill. Include WHEN to use, not just WHAT it does. |
| version | YES | SemVer (major.minor.patch). Bump when updating content. |
| emoji | No | Single emoji for display |
| image | No | Absolute HTTPS URL to skill avatar/logo (displayed in Skills screen). Must start with `https://` or `http://`. Falls back to emoji if missing or fails to load. Partner skills host images at `https://shardclaw.xyz/assets/partner-skills/{skill-id}.{ext}`. |
| requires.bins | No | Required command-line tools |
| requires.env | No | Required environment variables |
| requires.config | No | Required config keys |
| allowed-tools | No | Restrict which tools the skill can use |

## Body Structure

After frontmatter, the body IS the instructions. No need for `## Instructions` header.

### Best Practices (from OpenClaw skill-creator)
- Keep SKILL.md under 500 lines
- Put WHEN to use in `description`, not in body
- Use `references/` subfolder for detailed docs
- Use `scripts/` subfolder for deterministic code
- Body should be action-oriented instructions

## Directory Structure

### Directory-based skill (recommended for complex skills):
```
skills/
  my-skill/
    SKILL.md          # Main skill file
    references/       # Reference docs
    scripts/          # Helper scripts
```

### Flat skill (for simple skills):
```
skills/
  my-skill.md         # Single file skill
```

## OpenClaw Compatibility

For compatibility with OpenClaw skills, the parser also supports the nested `metadata.openclaw` format:

```yaml
---
name: my-skill
description: "What it does"
version: "1.0.0"
metadata:
  openclaw:
    emoji: "🔧"
    requires:
      bins: []
      env: []
---
```

Both top-level fields (`emoji`, `requires`, `allowed-tools`) and nested `metadata.openclaw.*` fields are supported. Top-level fields take precedence. The `requires` values accept both YAML arrays and comma-separated strings.

## Legacy Format (deprecated)

The following format is still supported but deprecated:
```markdown
# Skill Name

Trigger: keyword1, keyword2

## Description
...

## Instructions
...
```

Skills using the legacy format will produce warnings in the log at load time.
Migrate to YAML frontmatter for marketplace compatibility and auto-update support.

## Skill Images

Skills can include an `image:` field with a full HTTPS URL to a logo/avatar. This is a **shardclaw extension** (not part of the OpenClaw spec).

### How it works
1. Android app loads the image via Coil with crossfade
2. Falls back to `emoji:` while loading or on error
3. Falls back to ⚡ if no emoji is set either

### Partner skill image convention
- Host images at `https://shardclaw.xyz/assets/partner-skills/{skill-id}.{ext}`
- Image files live in the `shardclaw_Web` repo under `assets/partner-skills/`
- Supported formats: `.jpg`, `.png`
- Keep file size small (under 50KB recommended)

### Validation rules
- Must be an absolute URL starting with `https://` or `http://`
- Relative paths and data URIs are **not** supported
- Invalid URLs are silently ignored (emoji fallback)

## Version Bumping

- Patch (1.0.x): Fix typos, clarify instructions
- Minor (1.x.0): Add new capabilities, new API endpoints
- Major (x.0.0): Breaking changes to skill behavior

## shardclaw Tokenization & Bags Hackathon

shardclaw will **tokenize** and join the **Bags Hackathon** at [https://bags.fm/hackathon](https://bags.fm/hackathon).

### Token Details

| Field    | Value                                           |
|----------|-------------------------------------------------|
| Name     | shardclaw                                      |
| Symbol   | SKC                                             |
| Supply   | 1,000,000,000 (1B)                              |
| Platform | [Bags](https://bags.fm)                         |
| Hackathon| [Bags Hackathon](https://bags.fm/hackathon)     |

## Examples

### Simple skill (flat file)

```yaml
---
name: crypto-prices
description: "Get real-time cryptocurrency prices and market data from CoinGecko (free, no API key)"
version: "1.0.0"
emoji: "💰"
requires:
  bins: []
  env: []
---

# Crypto Prices

Get cryptocurrency prices using the free CoinGecko API.

## When to Use

User asks about crypto prices, market data, or coin comparisons.

## API Endpoints

### Get single coin price

web_fetch({ url: "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd" })
```

### Partner skill (with image)

```yaml
---
name: clawpump
description: "Launch tokens on Solana via ClawPump — gasless pump.fun launches, earn 65% of trading fees"
version: "1.1.0"
emoji: "🐾"
image: "https://shardclaw.xyz/assets/partner-skills/clawpump.jpg"
requires:
  bins: []
  env: []
allowed-tools:
  - web_fetch
  - solana_address
  - solana_balance
  - solana_send
---

# ClawPump Token Launchpad

> **Third-party service.** ClawPump is an independent platform not affiliated with shardclaw.

## Actions

- Launch a new token on Solana
- Check earnings and trading fees
- Search available domains
```

### Complex skill (directory-based)

```
skills/
  home-automation/
    SKILL.md
    references/
      hue-api.md
      homeassistant-api.md
    scripts/
      discover-devices.js
```

**SKILL.md:**
```yaml
---
name: home-automation
description: "Control smart home devices — lights, thermostat, locks — via Home Assistant or Philips Hue"
version: "2.1.0"
emoji: "🏠"
requires:
  env: ["HOME_ASSISTANT_URL", "HOME_ASSISTANT_TOKEN"]
allowed-tools:
  - web_fetch
---

# Home Automation

Control smart home devices through their APIs.

## Setup

Requires Home Assistant URL and long-lived access token.
See references/homeassistant-api.md for setup instructions.

## Actions

- Turn lights on/off
- Set thermostat temperature
- Lock/unlock doors
- Check device status
```
