# 📜 PrivateChest - Changelog

All notable changes to this project will be documented here.

Releases from 2.1 onwards have their own detailed file; this page is the summary.

---

## [3.0]

**Full notes: [changelog3_0.md](changelog3_0.md)** — read the "Before you update" section first.

The largest release so far. Every part of the plugin was reworked.

### Data safety
- Protections in worlds that were not loaded at startup are no longer deleted
- `data.yml` is written atomically, so an interrupted save can no longer destroy every protection
- SQLite now actually stores trust relations and container names, which 2.x silently discarded
- Automatic backups on first start, before anything is touched

### Performance
- Changes are collected and written by a background thread instead of rewriting the whole dataset on
  every lock, unlock, break and `/trust`, on the main thread
- Hot event paths rule themselves out with one index lookup instead of building container snapshots
- Per-player limit checks and cleanup no longer force chunks to load

### Security
- Piston protection: a locked barrel or shulker box can no longer be pushed out of its protection
- Container opening is guarded at the inventory level, not only on right-click
- Fire, mobs and hopper minecarts covered
- Passwords hashed with PBKDF2-HMAC-SHA256 instead of a single SHA-256 pass, upgraded transparently
- Wrong-password rate limiting, with optional logging

### Compatibility
- Copper chests and crafters can be protected; container types resolve at runtime, so future ones work
  without a plugin update
- `[Private]` signs read both sides
- Real Folia support: block access happens on the thread that owns the chunk
- Minecraft 1.16.5 through 26.2 on one jar

### Features that existed but did nothing
- Per-container-type limits are connected to the locking flow
- Container names are displayed
- Bedrock message adaptation actually runs
- `/privatechest migrate` exists, alongside new `status` and `save` subcommands

### Translation
- Nothing shown to a player is hardcoded, including the sign tag and container type names
- English and Spanish bundled, with optional per-player language
- Container names accept accented characters

### Behaviour changes to review before updating
- Trusted players can no longer break containers, only use them
- Breaking half of a double chest no longer unlocks the other half
- Hoppers may be placed next to containers you have access to, which 2.x refused
- Per-shulker-colour limits are replaced by per-family limits

---

## [2.3.1] - 2026-03-20
See [changelog2_3_1.md](changelog2_3_1.md). Granular command permissions, shared container utilities,
explosion fix for double chests, thread safety.

## [2.3] - 2026-01
See [changelog2_3.md](changelog2_3.md). Shulker box support, Folia and Bedrock groundwork.

## [2.2] - 2025
See [changelog2_2.md](changelog2_2.md).

## [2.1] - 2025
See [Changelog2_1.md](Changelog2_1.md).

## [2.0] - 2025
Hashed passwords, pluggable storage backends, public API.

---

## [1.3] - 2025-04-26
### Added
- `/clearchests` admin command to automatically clean orphaned chest entries from data.yml.
- Prefix control moved to `config.yml` with options:
  - `use-prefix: true/false`
  - `prefix: "&7[&6PrivateChest&7] "`
- Multilingual support: now easier to translate via `messages.yml` and `config.yml`.

### Improvements
- Messages.yml is now clean and only contains messages (prefix settings moved).
- Prefix and config changes can be reloaded live with `/privatechest reload`.
- Internal optimizations for location and data handling.
- Maven build improved with relocation of bStats to avoid conflicts.

### Compatibility
- Minecraft 1.16.x – 1.21.5 (tested on Paper, Purpur, Pufferfish).

---

## [1.2] - 2025-04-25
### Added
- bStats integration to anonymously track plugin usage.
- `/privatechest reload` command to reload config and messages without restarting.

### Improvements
- Internal structure cleanup and minor performance optimizations.

---

## [1.1] - 2025-04-24
### Added
- Barrel support (protect barrels the same way as chests).

---

## [1.0] - 2025-04-22
### Initial Release
- Password-protected chests.
- Lock and unlock commands.
- Basic explosion protection.
