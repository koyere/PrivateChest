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

📜 License
PrivateChest is distributed under the MIT License.
See LICENSE for more information.

🎮 Thanks for using PrivateChest!
If you enjoy the plugin, leave a star ⭐ on GitHub and a rating on SpigotMC!