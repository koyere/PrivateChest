# PrivateChest 3.1

Two features requested by server owners: a way for players to find their own containers, and a way to
choose which worlds containers can be locked in.

---

## Updating from 3.0

Drop in the new jar and restart. No data changes, nothing to convert.

| | |
|:---|:---|
| New config options | `worlds.mode` and `worlds.list` are added to `config.yml`, with no restriction by default |
| New messages | Added to `messages.yml` in your configured language, your wording untouched |
| New permissions | `privatechest.list` is granted to everyone through `privatechest.use`. The two staff nodes come with `privatechest.admin` |

Nothing changes for your players until you configure it.

---

## `/chestlist`

Lists the containers a player has protected, with the world and exact coordinates of each.

```
=== Your protected containers (3) ===
- chest (double) | world | X: 120, Y: 64, Z: -450
- Ores | world | X: 131, Y: 60, Z: -447
- barrel | world_nether | X: -12, Y: 32, Z: 88
=== Page 1 of 1 ===
```

| Command | Permission | Default |
|:---|:---|:---|
| `/chestlist [page]` | `privatechest.list` | everyone |
| `/chestlist <player> [page]` | `privatechest.list.others` | op |

Aliases: `/mychests`, `/listchests`.

- Containers with a custom name show it, set with `/renamecontainer`. The rest show their type.
- Both halves of a double chest are listed once, marked `(double)`.
- Eight entries per page, in a stable order: world, then position.
- Staff can look up offline players, as long as that player owns at least one protected container.

**Why not `/lockchest list`:** the only argument of `/lockchest` is a password. Reserving the word
`list` there would turn a lock attempt with that password into a listing, without any warning.

**Performance:** the list is read from the owner index already kept in memory. No block is read and no
chunk is loaded, however far away the containers are. Looking up an offline player by name runs off
the server thread.

**One limitation:** the plugin decides which entries form a double chest from their positions, without
reading the world. Two single chests of the same kind placed side by side by the same player will be
listed as one double chest. This only affects how the list reads, never what is protected.

---

## World restrictions

Choose which worlds containers can be locked in, for example allowing locks in survival but not in a
creative or minigame world.

```yaml
worlds:
  mode: WHITELIST   # WHITELIST, BLACKLIST or DISABLED
  list:
    - "world"
    - "world_nether"
    - "world_the_end"
```

| Mode | Effect |
|:---|:---|
| `DISABLED` | No restriction (default) |
| `WHITELIST` | Containers can only be locked in the listed worlds |
| `BLACKLIST` | Containers can be locked everywhere except the listed worlds |

- Applies to `/lockchest` and to private signs.
- **Containers that are already locked stay protected in every world.** Adding a world to a blacklist
  never opens anyone's containers. Players can still remove protections there with `/unlockchest` or
  by breaking their container.
- World names are matched ignoring case.
- Staff with `privatechest.bypass.worlds` can lock anywhere. It is included in `privatechest.admin`.
- An unrecognised `mode` is treated as `DISABLED`, with a console warning, so a typo cannot stop every
  player from locking. A `WHITELIST` with an empty list also logs a warning, since it blocks every world.
- `/privatechest reload` applies changes immediately, and `/privatechest status` shows the active
  restriction.

---

## API

```java
// Does the server's worlds configuration allow new locks here? Ignores permissions.
boolean allowed = PrivateChestAPI.isLockingAllowedIn(world);
```

`PrivateChestAPI.lockBlock` is not restricted by world, so integrations decide for themselves. Call
`isLockingAllowedIn` first if your plugin should honour the server owner's choice.
