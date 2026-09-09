# PrivateChest 3.0

The largest release so far. Every part of the plugin was reworked: storage, protection, threading,
version support and translation. Several features that were configurable in 2.x but did nothing are now
connected, and several ways to lose data or bypass protection are closed.

---

## Updating from 2.x

**Back up your `plugins/PrivateChest/` folder.** Then drop in the new jar and restart. No manual
conversion, no downtime, no data file changes.

### Done for you

| | |
|:---|:---|
| Backups | `data.yml.v2-backup`, or `privatechest.db.v2-backup` plus a `privatechest_data_v2_backup` table |
| Data format | Unchanged, read as-is |
| Passwords | Old formats still work, upgraded to PBKDF2 as each is entered correctly |
| Plain text passwords | Hashed at first startup (pre-2.0 data only) |
| New config options | Added to `config.yml`, your values untouched |
| New messages | Added to `messages.yml`, your wording untouched |

### Needs your attention

**1. SQLite users: trust and container names were never saved by 2.x.** The SQLite backend accepted
them and discarded them, so they vanished on every restart. 3.0 stores them properly but cannot recover
what earlier versions threw away. Your players may need to run `/trust` again once.

**2. Per-shulker-colour limits are gone.** If you configured
`container-limits.types.white_shulker_box` and friends, replace them with the single `shulker_box`
entry. Old keys and the old per-colour permission nodes are ignored.

**3. Four behaviour changes.** All of them make the plugin stricter or fix something broken:

| Change | Why |
|:---|:---|
| Trusted players can no longer **break** containers, only use them | Trust granted destruction rights by accident |
| Breaking half a double chest no longer unlocks the other half | It used to leave the surviving chest open |
| Hoppers **can** be placed next to containers you have access to | 2.x refused next to your own chests, breaking players' own sorting systems |
| `/privatechest` requires a valid subcommand | It used to reload on any argument, including typos |

**4. Optional:** set `language: es` (or your own translation) and delete `messages.yml` if you want it
recreated in another language.

---

## Data safety

Four ways to lose protections, all closed.

- **Worlds not loaded at startup lost every protection inside them.** Records were dropped from memory
  and the next save wrote that loss to disk permanently. Affects anyone unmounting a world or using a
  world manager that loads after plugins.
- **An interrupted save could truncate `data.yml`** and destroy every protection on the server. Writes
  are now atomic: a temp file moved into place.
- **Worlds with a dot in the name** (`world.old`) had their records silently mangled.
- **A pre-2.0 plain text password containing `:`** was mistaken for a hash, never upgraded, and could
  never be verified again, permanently locking its owner out.

Also: unreadable records are now counted and reported at startup instead of vanishing, and a double
chest that fails to lock halfway through is rolled back instead of leaving one protected half.

---

## Performance

- **Saves no longer happen per action.** Every lock, unlock, break, `/trust` and rename used to rewrite
  the entire dataset on the main thread. Changes are now collected and written by a background thread on
  an interval. Ten players locking chests in one second produce one write, not ten full rewrites.
- **`InventoryMoveItemEvent` was the plugin's biggest per-tick cost.** It fires for every item transfer
  on the server, and 2.x built a full container snapshot, including a copy of its inventory, for both
  the source and the destination of each one. Handlers now start with one lookup in a per-chunk index.
- **Chunk loads removed from hot paths.** Limit checks read every one of a player's recorded positions
  out of the world on each lock attempt. `/clearchests` did the same for every record on the server.
  Neither does now, and cleanup only examines chunks that are already loaded.
- Double chest detection reads block data instead of block state.
- SQLite writes only changed rows, in a transaction, with WAL and an indexed owner column. It also
  falls back to YAML instead of starting with no protection if it cannot open.

```yaml
save-interval-seconds: 5   # a clean shutdown always writes everything
```

---

## Protection gaps closed

| Gap | Detail |
|:---|:---|
| **Pistons** | Barrels and shulker boxes are pushable. A griefer could shove a locked barrel one block: the record stayed at the old coordinates and the container came to rest unprotected |
| **Only right-clicks were checked** | Any other route into a container's contents went straight through. Opening is now guarded at the inventory level too |
| **Fire and mobs** | Nothing stopped a protected container burning or being removed by a mob |
| **Hopper minecarts** | Item pickup into a protected container was not covered |
| **Duplicate messages** | A right-click fires once per hand and the handler did not check which, so every message arrived twice |

### Passwords

Stored as **PBKDF2-HMAC-SHA256** with a random salt, instead of one pass of SHA-256 which is fast enough
to brute force offline. Raising the iteration count later strengthens existing passwords as they are
used, with no migration step. Measured cost at the default: about 9 ms per `/unlockchest`.

Guessing was also unlimited and invisible. Now rate limited and optionally logged with the player name
and container position.

```yaml
security:
  password-hash-iterations: 20000
  max-password-attempts: 5
  attempt-window-seconds: 60
  lockout-seconds: 300
  log-failed-attempts: true
```

---

## Minecraft version support

**One jar for 1.16.5 through 26.2.** Everything version-specific resolves at startup.

- **Copper chests can be protected**, all oxidation and waxed stages, and **crafters** too. Neither was
  possible in 2.x, whose container list was twenty types written into the code.
- **An oxidizing copper chest stays yours.** Its block type changes as it weathers; every stage counts
  as the same container, so it stays locked and your count does not shift.
- **`[Private]` signs read both sides.** Signs have had a writable back since 1.20, and a tag written
  there locked nothing while appearing to.
- **The container list is yours**, and future containers work without a plugin update:

```yaml
containers:
  auto-discover: true                          # protect containers added by future versions
  discovery-patterns: ["*_CHEST", "*_SHULKER_BOX"]
  additional: []                               # e.g. FURNACE, BLAST_FURNACE, BREWING_STAND
  excluded: ["ENDER_CHEST"]
  disabled: []                                 # stop new locks, keep existing ones
```

`disabled` never deletes existing data: the cleanup treats a disabled type as still being a container.

### Folia

Folia support was advertised in 2.x and did not exist. `/renamecontainer` failed outright, the cleanup
read blocks from an async timer, `/clearchests` read across regions, and repeating tasks could not be
cancelled so they survived plugin disable.

Block access now runs on the thread that owns the chunk, on Folia, Luminol, LightingLuminol, LeafMC and
Kaiiju. Two side benefits on regular servers: cleanup is spread over ticks instead of running in one
block, and it no longer forces chunks to load.

---

## Features that did nothing

Three subsystems were fully written in 2.x and never called.

- **Per-container-type limits.** The whole `container-limits` section and every per-type permission node
  had no effect. `/lockchest` and `[Private]` signs now share one limit check, so they cannot disagree.
- **Container names.** `/renamecontainer` stored a name that was never displayed. Names now appear in
  access and denial messages through `{container}`, alongside `{owner}`.
- **Bedrock message adaptation.** Not one message ever reached it, so Bedrock players got identical
  text. It now runs for every message, and three bugs in it were fixed: it truncated at 100 characters
  (shorter than several of the plugin's own messages), it re-resolved Floodgate on every call, and its
  symbol list did not cover the characters the plugin actually uses.

### Limit fixes

- **A limit permission now beats the default.** `default-chest-limit` was applied first and the larger
  of the two won, so `privatechest.limit.1` on a server with a default of 5 granted **5**. Permissions
  below the default could not restrict anyone.
- **Any number works.** Only eleven values were ever checked, so `privatechest.limit.7` silently did
  nothing.

Limits and permissions now key off a container *family*: `chest`, `trapped_chest`, `copper_chest`,
`barrel`, `shulker_box`, `crafter`, `other`. A per-material limit can never work for a container whose
material changes on its own.

---

## Commands

`/privatechest` gained real subcommands, all under `privatechest.admin`:

| Command | Description |
|:---|:---|
| `reload` | Reload configuration and messages |
| `status` | Backend in use, protected containers, trust relations, protectable types, unsaved changes |
| `save` | Write pending changes now |
| `migrate <yaml\|sqlite>` | Move all data to the other backend and switch live |

`migrate` was documented in 2.x `config.yml` but did not exist. It updates `storage-type` for you
without stripping your comments, and leaves the old files as a backup. If it fails at any point the
current backend stays in use, untouched.

### Tab completion

Every command and subcommand completes, filtered by permission: a player who cannot run something is
offered nothing for it.

- **Passwords are never suggested.** A command with no completer falls back to the server default, which
  suggests player names, so 2.x offered your players' names as password candidates.
- **Vanish is respected.** Players you cannot see are not listed.
- `/untrust` suggests only the players you actually trusted; `/trust` leaves out those you already have.

### Smaller fixes

- Breaking your own `[Private]` sign said it was an admin action, because ownership was checked after
  the record had already been removed.
- `/lockchest` and `/unlockchest` used a hardcoded English message from console.
- Unresolvable player names in `/trust list` use a message key instead of a hardcoded word.
- A missing message key is logged with its name and shown as `[key_name]`, instead of English prose.

---

## Cleanup no longer deletes trust lists

2.x wiped a player's entire trusted list on every pass if they owned no containers at that moment. A
player who ran `/trust` before locking their first container lost it within half an hour, and so did
anyone who temporarily broke all their containers. Now off by default, and the rest is configurable
instead of fixed in code:

```yaml
auto-cleanup:
  startup-enabled: true
  startup-delay-seconds: 10     # let world managers mount their worlds
  periodic-enabled: true
  interval-minutes: 30          # 0 disables
  max-chunks-per-pass: 200
  chunks-per-tick: 20
  remove-unused-trust: false
```

Positions in unloaded chunks are never touched, on any setting.

---

## Translation

**Nothing shown to a player is hardcoded.** These were baked into the code: the `[Private]` sign tag,
container type names, "Unlimited", the hopper/dropper/dispenser names, the console-only refusal, the
forbidden container names, and the missing-key placeholder.

```yaml
# messages.yml
sign_tag: "[Private]"
sign_tag_aliases: ["[Private]", "[Privado]"]
sign_tag_locked: "&4[&cPrivate&4]"
```

Tag matching ignores case, colour codes and spaces. The aliases list lets you change language without
invalidating signs your players already placed.

**Accented characters work in container names.** The rule was `a-zA-Z0-9`, which rejected every accent,
so players on a Spanish, French, German or Portuguese server could not use their own language. The
default is now Unicode-aware and the whole rule is configurable, along with the forbidden word list.

**English and Spanish are bundled**, in `plugins/PrivateChest/lang/`:

```yaml
language: en              # which translation seeds messages.yml and supplies new keys
use-player-locale: false  # or match each player by their Minecraft language
```

Previously an update always injected English into a translated file. With `use-player-locale`, missing
entries fall back to `messages.yml`, so a partial translation is safe. Copy a file in `lang/` to add a
language.

Nothing changes for a single-language server: `messages.yml` is still your file and your edits stand.

---

## Known gap: copper golems

Copper golems (1.21.9+) carry items out of copper chests on their own. If your server reports that as a
normal item transfer, existing protection already blocks it, since transfers involving a protected
container are cancelled whatever starts them. If it is implemented purely as mob behaviour, there is no
event to intercept and no plugin can stop it.

This could not be verified, so it is not claimed as covered. Test it before relying on it.

**Also not included:** a native Bedrock input form for passwords. Bedrock players use every feature by
typing commands, and `[Private]` signs need no command at all.

---

## New configuration

`security`, `containers`, `container-names`, `language`, `use-player-locale`, `save-interval-seconds`,
and an expanded `auto-cleanup`. Every option is documented inline in `config.yml`.

## New message keys

Added automatically, your existing wording kept:

```
sign_tag, sign_tag_aliases, sign_tag_locked, unknown_player, limit_unlimited,
container_generic_name, container_type_*, block_name_*, too_many_attempts,
limit_exceeded_type, sign_limit_exceeded_type, clean_skipped_unloaded,
cleanup_already_running, usage_privatechest, usage_migrate, migrate_*, save_*, status_*
```

Removed, because they could never be reached: `limit_error`, `sign_not_your_chest`.

Reworded defaults, which your file keeps unless you delete the key: `locked`, `not_your_chest`,
`sign_chest_locked`, the `limit_*` messages, and the access and break notices, all of which now support
`{container}` and `{owner}`.

---

## Compatibility

| | |
|:---|:---|
| Minecraft | 1.16.5 – 26.2 |
| Java | 8 or newer |
| Single-threaded | CraftBukkit, Spigot, Paper, Purpur, Pufferfish, forks |
| Regionised | Folia, Luminol, LightingLuminol, LeafMC, Kaiiju, forks |
| Cross-platform | Java and Bedrock, via Geyser and Floodgate |
