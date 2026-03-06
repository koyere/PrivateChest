# 🔐 PrivateChest

PrivateChest is a lightweight and powerful plugin that allows players to lock their chests and barrels with a personal password.

No complicated land-claim systems required — simple, secure, and optimized for survival servers!

---

## ✨ Features

- 🔒 Lock chests, barrels, and shulker boxes with a password.
- 🔑 Unlock your own containers with the correct password.
- 👤 Only the owner can access protected containers.
- 💥 Protection against TNT, creeper, and explosion damages.
- 📦 Support for double chests and all shulker box variants.
- 🤝 Trust system - give other players access without sharing passwords.
- 🔁 Reload configuration and messages live with `/privatechest reload`.
- 🧹 Automatic cleanup of orphaned container data.
- 🎨 Customizable prefix and full message control.
- 🌐 Multilingual ready — translate easily via `messages.yml` and `config.yml`.
- 📊 bStats integration to anonymously monitor plugin usage.
- 🎮 Bedrock Edition support via Floodgate.

---

## 📜 Commands

| Command | Description | Permission |
|:---|:---|:---|
| `/lockchest <password>` | Lock the chest you are looking at. | `privatechest.lock` |
| `/unlockchest <password>` | Unlock your locked chest. | `privatechest.unlock` |
| `/trust <player>` | Give another player access to your locked containers. | `privatechest.trust` |
| `/trust list` | List all players you have trusted. | `privatechest.trust` |
| `/untrust <player>` | Remove access from a player. | `privatechest.untrust` |
| `/untrust all` | Remove access from all trusted players. | `privatechest.untrust` |
| `/renamecontainer <name>` | Set a custom name for a protected container. | `privatechest.rename` |
| `/renamecontainer remove` | Remove the custom name from a container. | `privatechest.rename` |
| `/privatechest reload` | Reload config and messages without restart. | `privatechest.admin` |
| `/clearchests` | Remove orphaned chest entries from data.yml. | `privatechest.admin` |

---

## 🔒 Permissions

| Permission | Description | Default |
|:---|:---|:---|
| `privatechest.use` | Parent permission — grants all player commands below. | `true` |
| `privatechest.lock` | Allows using `/lockchest`. | `true` |
| `privatechest.unlock` | Allows using `/unlockchest`. | `true` |
| `privatechest.trust` | Allows using `/trust`. | `true` |
| `privatechest.untrust` | Allows using `/untrust`. | `true` |
| `privatechest.rename` | Allows using `/renamecontainer`. | `true` |
| `privatechest.sign` | Allows locking containers via `[Private]` signs. | `true` |
| `privatechest.admin` | Admin commands and bypass protection. | `op` |

> **Tip:** To block only the `/lockchest` command while keeping sign-based protection, negate `privatechest.lock` in your permissions plugin. Signs use `privatechest.sign` independently.

---

## ⚙️ Configuration

### config.yml

```yaml


use-prefix: true
prefix: "&7[&6PrivateChest&7] "
notify-owner-on-open: true

messages.yml
Easily edit all plugin messages to your preferred language!

## ✅ Compatibility

### Minecraft Versions
**Supported:** 1.16.5 → 1.21.x

### Server Software

**Standard (Single-threaded):**
- CraftBukkit
- Spigot
- Paper
- Purpur
- Pufferfish
- Any Spigot/Paper fork

**Regionized (Multi-threaded):**
- Folia
- Luminol
- LightingLuminol
- LeafMC
- Kaiiju
- Any Folia-based fork

### Cross-Platform (Java + Bedrock)
- Geyser (standalone or plugin)
- Floodgate (for authentication)
- Full support for Bedrock Edition players

📊 Plugin Statistics
View usage data at:
🔗 bStats - PrivateChest

---

## 🧩 Public API (Since v1.4)

PrivateChest now offers a simple and safe API for developers to integrate with protected chests and barrels.

**Example usage:**

```java
import me.tuplugin.privatechest.api.PrivateChestAPI;

// Check if a block is locked
boolean isLocked = PrivateChestAPI.isLocked(block);

// Check if a player owns a block
boolean isOwner = PrivateChestAPI.isOwner(block, player);

// Lock a chest manually
PrivateChestAPI.lockBlock(block, player, "mypassword");

// Unlock a chest manually
PrivateChestAPI.unlockBlock(block);

// Get the owner of a locked chest
UUID owner = PrivateChestAPI.getOwner(block);


📜 License
PrivateChest is distributed under the MIT License.
See LICENSE for more information.

🎮 Thanks for using PrivateChest!
If you enjoy the plugin, leave a star ⭐ on GitHub and a rating on SpigotMC!
