# 🔐 PrivateChest

Lock your containers with a password. No land claims, no regions, no setup — place a sign or type one
command and the container is yours.

---

## ✨ What it does

- Lock chests, trapped chests, barrels, shulker boxes, crafters and copper chests
- Lock by command or by writing a tag on a sign next to the container
- Trust other players so they can use your containers without knowing your password
- Give containers custom names
- List every container you own, with its world and coordinates
- Choose which worlds containers can be locked in
- Protection against breaking, explosions, fire, pistons, mobs and hoppers
- Per-player container limits by permission, overall or per container type
- YAML or SQLite storage, switchable at runtime
- Translatable end to end, with English and Spanish included

---

## ✅ Compatibility

### Minecraft

**1.16.5 through 26.2.** One jar for all of them.

Everything version-specific is resolved when the server starts, so newer containers and newer sign
features are used when they exist and skipped quietly when they do not.

| Feature | Requires |
|:---|:---|
| Chests, trapped chests, barrels, shulker boxes | 1.16.5+ |
| Reading both sides of a sign | 1.20+ |
| Crafters | 1.21+ |
| Copper chests, all oxidation and waxed stages | 1.21.9+ |
| Containers added after this release | Automatic, see `containers.auto-discover` |

### Server software

**Single-threaded:** CraftBukkit, Spigot, Paper, Purpur, Pufferfish, and forks.

**Regionised:** Folia, Luminol, LightingLuminol, LeafMC, Kaiiju, and forks.

Folia support is real, not just a `plugin.yml` flag. Anything that reads or writes a block runs on the
thread that owns that chunk, and background work uses the async scheduler. The detected software is
printed on the startup line.

### Java

Java 8 or newer. The jar is built for Java 8 so it also loads on the older servers listed above.

### Bedrock Edition

Bedrock players can use every feature, through Geyser and Floodgate. Messages are adapted for
Bedrock's chat: formatting it ignores is stripped, symbols with no glyph are replaced, and long lines
are wrapped instead of cut off.

Sign-based locking is the easiest route for Bedrock players, because it needs no command typing.

**Not included:** a native Bedrock input form for entering passwords. Commands work by typing them.

---

## 📜 Commands

### Players

| Command | Description | Permission |
|:---|:---|:---|
| `/lockchest <password>` | Lock the container you are looking at | `privatechest.lock` |
| `/unlockchest <password>` | Remove your protection from a container | `privatechest.unlock` |
| `/trust <player>` | Let a player use your protected containers | `privatechest.trust` |
| `/trust list` | List the players you have trusted | `privatechest.trust` |
| `/untrust <player>` | Revoke a player's access | `privatechest.untrust` |
| `/untrust all` | Revoke everyone's access | `privatechest.untrust` |
| `/renamecontainer <name>` | Name a container you own | `privatechest.rename` |
| `/renamecontainer remove` | Clear the name (`clear` and `delete` also work) | `privatechest.rename` |
| `/chestlist [page]` | List your protected containers with their world and coordinates | `privatechest.list` |

### Admins

| Command | Description |
|:---|:---|
| `/chestlist <player> [page]` | List another player's protected containers, online or offline. Needs `privatechest.list.others` |
| `/privatechest reload` | Reload `config.yml` and the message files |
| `/privatechest status` | Storage backend, protected container count, trust count, protectable types, unsaved changes |
| `/privatechest save` | Write all pending changes to disk now |
| `/privatechest migrate <yaml\|sqlite>` | Move all data to the other backend and switch to it |
| `/clearchests` | Remove records whose container no longer exists |

All other admin commands require `privatechest.admin`.

Aliases: `/pc` for `/privatechest`, `/renamechest` for `/renamecontainer`, and `/mychests` or
`/listchests` for `/chestlist`.

Every command has tab completion, and it respects permissions: a player who cannot run a command is
offered nothing for it. Passwords are never suggested.

---

## 🪧 Sign protection

Write the tag on a sign placed on, above or next to a container and it locks. Break the sign and it
unlocks.

The tag is part of the translation, not a fixed English word. In `messages.yml`:

```yaml
sign_tag: "[Private]"
sign_tag_aliases:
  - "[Private]"
  - "[Privado]"
sign_tag_locked: "&4[&cPrivate&4]"
```

Matching ignores case, colour codes and surrounding spaces. The aliases list lets you change your
server's language without invalidating signs your players already placed. Both sides of the sign are
read on Minecraft 1.20 and newer.

Sign protection uses its own permission, `privatechest.sign`, so you can block `/lockchest` and still
allow signs, or the other way round.

---

## 🔒 Permissions

| Permission | Description | Default |
|:---|:---|:---|
| `privatechest.use` | Parent permission, grants everything below | `true` |
| `privatechest.lock` | `/lockchest` | `true` |
| `privatechest.unlock` | `/unlockchest` | `true` |
| `privatechest.trust` | `/trust` | `true` |
| `privatechest.untrust` | `/untrust` | `true` |
| `privatechest.rename` | `/renamecontainer` | `true` |
| `privatechest.sign` | Locking with a sign | `true` |
| `privatechest.list` | `/chestlist` for your own containers | `true` |
| `privatechest.admin` | Admin commands and bypassing protection. Includes the two below | `op` |
| `privatechest.list.others` | `/chestlist <player>` | `op` |
| `privatechest.bypass.worlds` | Locking in worlds excluded by `worlds` in `config.yml` | `op` |

> To block one command, negate its permission in your permissions plugin. The rest keep working.

### Limit permissions

Only used when `enable-chest-limits: true`.

| Permission | Meaning |
|:---|:---|
| `privatechest.limit.unlimited` | No limit at all |
| `privatechest.limit.<number>` | Overall limit, any number |
| `privatechest.limit.<family>.<number>` | Limit for one container type |
| `privatechest.limit.<family>.unlimited` | No limit for one container type |

Families: `chest`, `trapped_chest`, `copper_chest`, `barrel`, `shulker_box`, `crafter`, `other`.

Any number works, not just the examples registered in `plugin.yml`. The most generous permission a
player has wins, and a permission always overrides `default-chest-limit`.

Per-family limits need `container-limits.enabled: true`. All shulker box colours count as one family,
and every copper chest oxidation stage counts as one family, so a player's count does not shift when
their copper chests weather.

---

## ⚙️ Configuration

`config.yml` documents every option inline. The main sections:

| Section | What it controls |
|:---|:---|
| `storage-type`, `save-interval-seconds` | Which backend, and how often pending changes are written |
| `language`, `use-player-locale` | Which translation, and whether each player gets their own |
| `container-names` | Length, allowed characters and forbidden words for `/renamecontainer` |
| `containers` | Which blocks can be protected, including auto-discovery of new ones |
| `worlds` | Which worlds containers can be locked in |
| `container-limits` | Per-container-type limits |
| `security` | Password hashing strength and wrong-attempt rate limiting |
| `auto-cleanup` | When and how hard the orphaned-record cleanup runs |
| `hopper-protection` | Whether hoppers may reach locked containers |
| `notify-*-on-open` | The small notices shown when a container is opened |

### Storage

```yaml
storage-type: yaml          # or sqlite
save-interval-seconds: 5
```

Changes are collected in memory and written by a background thread on that interval, so locking a
container never stalls the server. A clean shutdown always writes everything. A hard crash can lose at
most `save-interval-seconds` of changes, and `/privatechest save` forces a write at any time.

`/privatechest migrate` moves your data between backends live, updates `storage-type` for you, and
leaves the old files in place as a backup.

### Protectable containers

```yaml
containers:
  auto-discover: true
  discovery-patterns: ["*_CHEST", "*_SHULKER_BOX"]
  additional: []                 # e.g. FURNACE, BLAST_FURNACE, SMOKER, BREWING_STAND
  excluded: ["ENDER_CHEST"]
  disabled: []
```

With `auto-discover` on, a container added by a future Minecraft update is protectable the day it
ships. `disabled` stops new locks on a type without touching protections players already have.

### World restrictions

```yaml
worlds:
  mode: DISABLED     # WHITELIST, BLACKLIST or DISABLED
  list: []           # e.g. "world", "world_nether"
```

`WHITELIST` allows locking only in the listed worlds, and `BLACKLIST` everywhere except them. This
covers `/lockchest` and private signs.

Only new locks are affected. Containers that are already protected stay protected in every world, so
adding a world to a blacklist never opens anyone's containers. Staff with `privatechest.bypass.worlds`
can lock anywhere.

### Security

```yaml
security:
  password-hash-iterations: 20000
  max-password-attempts: 5
  attempt-window-seconds: 60
  lockout-seconds: 300
  log-failed-attempts: true
```

Passwords are stored as PBKDF2-HMAC-SHA256 with a random salt. Older formats are read and rewritten
automatically the first time a password is entered correctly, so raising the iteration count
strengthens existing passwords with no migration step.

---

## 🌐 Translation

English and Spanish ship with the plugin, in `plugins/PrivateChest/lang/`.

```yaml
language: en
use-player-locale: false
```

- `language` picks the translation used to create `messages.yml` on first start, and the language new
  keys arrive in when an update adds them.
- `use-player-locale: true` loads every `lang/*.yml` and matches each player by their Minecraft
  language setting, falling back to `messages.yml` for anything a translation is missing. A partial
  translation is safe to use.

`messages.yml` is your active, editable file. Nothing overwrites your wording.

To add a language, copy a file in `lang/` to `lang/<code>.yml` and translate the values. Nothing shown
to a player is hardcoded, including the sign tag and the container type names.

---

## 🛡️ What is protected

| Against | Covered |
|:---|:---|
| Opening by another player | Yes, including any route other than right-clicking |
| Breaking the container | Yes. Trusted players can use a container but not destroy it |
| TNT, creepers and other explosions | Yes, both halves of a double chest |
| Fire | Yes |
| Mobs changing the block | Yes |
| Pistons moving the container | Yes. Barrels and shulker boxes are pushable, so this matters |
| Hoppers, droppers, dispensers | Yes, configurable |
| Hopper minecarts | Yes |
| Placing a hopper against someone else's container | Yes |

**Known gap:** copper golems (Minecraft 1.21.9+) carry items out of copper chests on their own. If your
server reports that as a normal item transfer, existing protection already blocks it. If it is
implemented purely as mob behaviour, no plugin can intercept it. Test it on your setup before relying
on it.

---

## 📊 Statistics

bStats collects anonymous usage data: https://bstats.org/plugin/bukkit/PrivateChest/25606

---

## 🧩 Developer API

```java
import me.tuplugin.privatechest.api.PrivateChestAPI;

// Is this block protected?
boolean locked = PrivateChestAPI.isLocked(block);

// Does this player own it?
boolean owner = PrivateChestAPI.isOwner(block, player);

// Who owns it?
UUID owner = PrivateChestAPI.getOwner(block);

// Lock and unlock. Both persist automatically.
PrivateChestAPI.lockBlock(block, player, "password");
PrivateChestAPI.unlockBlock(block);

// Can this material be protected on this server?
boolean lockable = PrivateChestAPI.isLockableContainer(Material.BARREL);

// How many containers are protected in total?
int count = PrivateChestAPI.getLockedChestCount();

// Does the server's worlds configuration allow new locks here? (3.1+)
boolean allowed = PrivateChestAPI.isLockingAllowedIn(world);
```

`isLockableContainer` answers from the server's live container list, so it reflects your
`containers` configuration.

`lockBlock` is not restricted by world. Call `isLockingAllowedIn` first if your plugin should honour
the server owner's `worlds` setting.

---

## 📦 Building

```bash
mvn clean package
```

The jar lands in `target/PrivateChest-<version>.jar`. bStats is shaded and relocated.

---

## 📄 License

MIT. See [LICENSE](LICENSE).

---

Thanks for using PrivateChest. A star on GitHub or a rating on SpigotMC is always appreciated.
