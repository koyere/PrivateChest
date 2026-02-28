# 📜 PrivateChest - Version 2.3.1 Release Notes

**Release Date:** February 27th, 2026

---

## [2.3.1] - 2026-02-27

### Added
- **Granular Command Permissions**
  - New individual permission nodes for each player command:
    - `privatechest.lock` — Controls access to `/lockchest`
    - `privatechest.unlock` — Controls access to `/unlockchest`
    - `privatechest.trust` — Controls access to `/trust`
    - `privatechest.untrust` — Controls access to `/untrust`
    - `privatechest.rename` — Controls access to `/renamecontainer`
  - `privatechest.use` now acts as a **parent permission** that grants all of the above
  - Server admins can now deny specific commands per player or group without affecting the rest

- **Shared Container Utility Class (`ContainerUtils`)**
  - Centralized double chest detection, container validation, and location serialization
  - Eliminates code duplication across 6 files (listeners and commands)
  - Single source of truth for container logic — easier to maintain and less error-prone

### Fixed
- **Explosion protection now covers both halves of double chests**
  - Previously, if only one half of a double chest had the lock record, the other half could be destroyed by TNT/creepers
  - Both `EntityExplodeEvent` and `BlockExplodeEvent` now check all container parts

- **Sign protection now uses granular permission (`privatechest.lock`)**
  - Previously used `privatechest.use`, inconsistent with the new granular system

- **Null safety in `ChestLocker.serializeLocation()`**
  - Previously could throw `NullPointerException` if a world was unloaded
  - Now delegates to `ContainerUtils.serializeLocation()` with proper null checks

- **Sign-based protection password is now cryptographically secure**
  - Previously used deterministic `hashCode()` based on player UUID and location (predictable)
  - Now uses `SecureRandom` to generate unpredictable 32-character hex passwords

### Improved
- **Thread Safety**
  - `ChestLocker`: `chestOwners` and `chestPasswords` maps changed from `HashMap` to `ConcurrentHashMap`
  - `TrustManager`: `trustRelations` map changed from `HashMap` to `ConcurrentHashMap`, inner sets use `ConcurrentHashMap.newKeySet()`
  - Prevents `ConcurrentModificationException` when async cleanup runs alongside main thread operations

- **Timing-safe password comparison**
  - `PasswordManager.verifyPassword()` now uses `MessageDigest.isEqual()` instead of `String.equals()`
  - Prevents timing attacks that could leak password hash information

### How Granular Permissions Work
- By default, nothing changes for existing servers. `privatechest.use` still grants all commands.
- To block a specific command, negate its permission in your permissions plugin:
  ```yaml
  # LuckPerms example — block only /lockchest for a group
  permissions:
    - privatechest.lock: false
  ```
- The remaining commands will continue working normally.

### Migration Notes
- **Fully backwards compatible** — no configuration changes required
- Existing permission setups using `privatechest.use` will continue to work identically
- Thread safety improvements are transparent — no action needed

### Compatibility
- Minecraft 1.16.x – 1.21.5
- Tested on Paper, Purpur, Pufferfish, and Spigot

---

## Support & Feedback
- Report issues on GitHub: [PrivateChest Issues](https://github.com/anthropics/claude-code/issues)
- SpigotMC Resource: https://www.spigotmc.org/resources/privatechest.124359/

Thank you for using PrivateChest!
