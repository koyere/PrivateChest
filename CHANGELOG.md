# 📜 PrivateChest - Changelog

All notable changes to this project will be documented here.

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
