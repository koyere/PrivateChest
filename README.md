# 🔐 PrivateChest

PrivateChest is a lightweight and powerful plugin that allows players to lock their chests and barrels with a personal password.

No complicated land-claim systems required — simple, secure, and optimized for survival servers!

---

## ✨ Features

- 🔒 Lock chests and barrels with a password.
- 🔑 Unlock your own chest with the correct password.
- 👤 Only the owner can access protected chests.
- 💥 Protection against TNT, creeper, and explosion damages.
- 📦 Support for double chests and barrels.
- 🔁 Reload configuration and messages live with `/privatechest reload`.
- 🧹 Clean orphaned chest data with `/clearchests`.
- 🎨 Customizable prefix and full message control.
- 🌐 Multilingual ready — translate easily via `messages.yml` and `config.yml`.
- 📊 bStats integration to anonymously monitor plugin usage.

---

## 📜 Commands

| Command | Description | Permission |
|:---|:---|:---|
| `/lockchest <password>` | Lock the chest you are looking at. | `privatechest.use` |
| `/unlockchest <password>` | Unlock your locked chest. | `privatechest.use` |
| `/privatechest reload` | Reload config and messages without restart. | `privatechest.admin` |
| `/clearchests` | Remove orphaned chest entries from data.yml. | `privatechest.admin` |

---

## 🔒 Permissions

- `privatechest.use` → Allows players to lock and unlock chests.
- `privatechest.admin` → Allows access to administrative commands.

---

## ⚙️ Configuration

### config.yml

```yaml


use-prefix: true
prefix: "&7[&6PrivateChest&7] "
notify-owner-on-open: true

messages.yml
Easily edit all plugin messages to your preferred language!

✅ Compatibility
Minecraft Versions: 1.16.x ➔ 1.21.5

Supported forks: Paper, Pufferfish, Purpur

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
