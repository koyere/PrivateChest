# PrivateChest 3.0

> Work in progress. This file grows as the 3.0 work lands.
> Everything below is already in the build.

---

## Before you update

**Back up your `plugins/PrivateChest/` folder.** The plugin also does this for you: the first time 3.0
starts it copies your existing data aside before touching it.

- YAML users get `data.yml.v2-backup`
- SQLite users get `privatechest.db.v2-backup`, and the old table is kept inside the database as
  `privatechest_data_v2_backup`

Your data file format does not change, so nothing needs converting and no downtime is required. Drop
in the new jar and restart.

**One thing to check if you use SQLite:** trust relations and container names were never actually
being saved by the SQLite backend in 2.x. They were accepted and discarded, so they disappeared on
every restart. 3.0 stores them properly, but it cannot recover what earlier versions threw away. Your
players may need to run `/trust` again once.

---

## Storage and performance

### Saves no longer happen on every action

Previously, every lock, unlock, container break, `/trust` and rename rewrote the **entire** dataset
immediately, on the main server thread. On YAML that meant rewriting all of `data.yml`; on SQLite it
deleted every row and reinserted all of them. On a server with thousands of protected containers,
every single one of those actions was a visible stutter.

Changes are now collected in memory and written by a background thread on a fixed interval. Ten
players locking chests in the same second produce one write instead of ten full rewrites, and none of
it runs on a server thread.

New setting in `config.yml`:

```yaml
# How often, in seconds, pending changes are written to disk.
save-interval-seconds: 5
```

A clean shutdown or restart always writes everything, regardless of this value. A hard crash (power
loss, `kill -9`) can lose at most this many seconds of changes. You can force a write at any time
with `/privatechest save`.

### Writes can no longer corrupt data.yml

`data.yml` is now written to a temporary file and then moved into place. Before, an interrupted save
(crash, full disk, container killed mid-write) could leave a truncated file behind and destroy every
protection on the server.

### SQLite is now the faster option, as intended

- Only the rows that actually changed are written, inside a transaction
- Write-ahead logging is enabled
- The owner column is indexed, which is what makes per-player limit checks cheap
- Container names and trust relations are stored (see the note above)

If SQLite fails to open at startup, the plugin now falls back to YAML and keeps running instead of
starting up with no protection.

### Per-player limit checks stopped loading chunks

Checking a player's container count used to read every one of their recorded positions back out of
the world, forcing the server to load a chunk for each one. Counts are now answered from memory. The
container type is recorded when a container is locked, so per-type questions do not need the world
either.

`/clearchests` follows the same rule: it only examines positions in chunks that are already loaded,
and tells you how many it skipped rather than loading the whole world to check them.

---

## Data safety fixes

### Protections in unloaded worlds are no longer deleted

If a world was not loaded when the plugin started, every protection inside it was dropped from memory
and the next save wrote that loss to disk permanently. This affected anyone unmounting a world
temporarily, or using a world manager that loads worlds after plugins start.

Those records are now kept and applied if the world is loaded later. The startup log tells you how
many protections belong to worlds that are not currently mounted, and which worlds they are.

### Worlds with a dot in the name

A world named something like `world.old` had its protection records silently mangled, because the
world name was being treated as a path. Fixed.

### Damaged records are reported instead of ignored

Records that cannot be read are now counted and reported in the startup log with the reason, instead
of vanishing quietly.

### Legacy passwords containing a colon

A password stored in plain text by a pre-2.0 version that happened to contain a `:` was mistaken for
an already-hashed value. It was never upgraded and could never be verified again, permanently locking
the owner out of their own container. The stored format is now matched exactly.

### Half-locked double chests

If locking a double chest failed partway through, one half stayed protected and the other was left
open. The operation is now rolled back as a unit.

---

## Commands

`/privatechest` used to reload on any argument at all, including typos. It now has real subcommands
(all under `privatechest.admin`, with tab completion):

| Command | What it does |
|:---|:---|
| `/privatechest reload` | Reload `config.yml` and `messages.yml` |
| `/privatechest status` | Storage backend in use, protected container count, trust relation count, and whether anything is waiting to be written |
| `/privatechest save` | Write all pending changes to disk immediately |
| `/privatechest migrate <yaml\|sqlite>` | Move all data to the other backend and switch to it |

`/privatechest migrate` was documented in `config.yml` in 2.x but did not exist. It now works: it
writes everything to the target backend, switches over live, updates `storage-type` in your
`config.yml` for you, and leaves the old data files untouched as a backup. Your comments in
`config.yml` are preserved. If the migration fails at any point, the current backend stays in use and
is not modified.

### Small fixes

- Breaking your own `[Private]` sign told you it was an admin action. It now says the right thing.
- `/lockchest` and `/unlockchest` used a hardcoded English message when run from console. They use
  `console_only_player` from `messages.yml` like every other command.
- Player names that cannot be resolved in `/trust list` now use the new `unknown_player` message key
  instead of a hardcoded word.
- "Unlimited" in the limit status line is now the `limit_unlimited` message key instead of hardcoded
  English.

---

## New message keys

Added automatically to your `messages.yml` on first start; your existing wording is left alone.

```
unknown_player, limit_unlimited, container_generic_name, clean_skipped_unloaded,
cleanup_already_running, usage_privatechest, usage_migrate, migrate_started, migrate_success,
migrate_already_active, migrate_failed, save_success, save_failed, status_header, status_storage,
status_containers, status_trust, status_pending, status_pending_yes, status_pending_no
```

---

## Folia and threaded server support

2.x advertised Folia support and did not have it. `folia-supported: true` was declared in
`plugin.yml`, but several parts of the plugin used the standard Bukkit scheduler or read blocks from
the wrong thread, which either throws or corrupts state on a regionised server.

### What was broken

- **`/renamecontainer` did not work on Folia at all.** It saved through the standard async scheduler,
  which Folia does not support.
- **The automatic cleanup read blocks from an asynchronous timer.** That is unsafe on Paper and fails
  outright on Folia, and it meant protections could be deleted based on an inconsistent read of the
  world.
- **`/clearchests` read every recorded position from the command thread.** On Folia that is illegal as
  soon as a record belongs to another region.
- **Repeating tasks could not be cancelled on Folia**, so the cleanup timer kept running after the
  plugin was disabled or reloaded.

### What it does now

Cleanup runs in two stages. Choosing what to check only reads memory, so it stays off the server
threads. Actually looking at a block is grouped by chunk and handed to the thread that owns that
chunk: the region thread on Folia, the main thread in small batches everywhere else. Records in chunks
that are not loaded are never touched, on any platform.

`/clearchests` now uses that same engine. It reports its results when the work finishes, tells you how
many records it skipped because their chunks were not loaded, and refuses to pile up if a cleanup is
already running.

Two side benefits on regular servers as well:

- Cleanup no longer forces chunks to load, and is spread over several ticks instead of running in one
  block. On a large server the periodic pass is no longer a measurable hitch.
- Per-player limit checks never read the world. Nothing on a hot path does anymore.

### Supported software

Verified against the scheduler contracts of Folia and its forks (Luminol, LightingLuminol, LeafMC,
Kaiiju) as well as Paper, Purpur, Pufferfish and Spigot. The startup line tells you which one was
detected. If a Folia server ever presents a scheduler the plugin cannot use, it logs a clear warning
and falls back instead of failing silently.

---

## Tab completion

Every command and subcommand now completes, and completion respects permissions: a player who cannot
run a command is offered nothing for it.

| Command | Suggestions |
|:---|:---|
| `/privatechest` | `reload`, `status`, `save`, `migrate` — then `yaml` or `sqlite` for `migrate` |
| `/trust` | `list`, plus online players you can see, minus the ones you already trust |
| `/untrust` | `all`, plus the players you have actually trusted, online or not |
| `/renamecontainer` | `remove`, `clear`, `delete` |
| `/lockchest`, `/unlockchest` | nothing, deliberately |
| `/clearchests` | nothing, it takes no arguments |

Two details worth knowing:

- Passwords are never completed. Commands without a completer fall back to the server's default, which
  suggests online player names, so 2.x was offering your players' names as password candidates. Those
  commands now return an empty list on purpose.
- Player suggestions honour vanish. Anyone you cannot see is not listed, so tab completion cannot be
  used to detect hidden staff.

`/renamecontainer remove` also accepts `clear` and `delete`, and no longer treats those words as a
container name when combined with other words.

---

## Protection gaps closed

These were all exploitable on 2.3.1. If you run a public server, this is the section that matters.

### Pistons could move a locked container out of its protection

Barrels and shulker boxes can be pushed by a piston. Nothing stopped that, so anyone could shove a
locked barrel one block sideways: the protection record stayed at the old coordinates, and the
container came to rest unprotected and open to anyone. Both piston extend and retract are now blocked
when a protected container would be moved.

### Only right-clicking was checked

All access control ran through the right-click handler. Any other route into a container's contents
went straight through. The plugin now also checks the inventory open itself, which covers every other
path, including other plugins opening a container on a player's behalf.

### Fire and mobs could destroy locked containers

Burning and entity-caused block changes are now blocked for protected containers.

### Hoppers could still vacuum items in

Item pickup by hoppers and hopper minecarts is now covered as well, not just container-to-container
transfers.

### Passwords were hashed with a single pass

Passwords were stored as one round of salted SHA-256, which is fast enough to brute force offline if
the data file is ever exposed. They are now PBKDF2-HMAC-SHA256 with a configurable iteration count.

**No migration step and no downtime.** Old formats are still read, and each password is rewritten in
the new format the first time it is entered correctly. Passwords left in plain text by a pre-2.0
version are hashed at startup, since plain text on disk is worth removing immediately.

```yaml
security:
  password-hash-iterations: 20000
```

Raising this later strengthens existing passwords as players use them, again with no migration.
Measured cost at the default: about 9ms per `/unlockchest`.

### Password guessing was unlimited and invisible

```yaml
security:
  max-password-attempts: 5      # wrong passwords allowed
  attempt-window-seconds: 60    # before older attempts stop counting
  lockout-seconds: 300          # how long the player then waits
  log-failed-attempts: true     # log name and container position to console
```

### Every message was sent twice

Right-clicking fires once per hand, and the handler did not check which one. Players saw every access
message duplicated.

---

## Two deliberate behaviour changes

Both make the plugin stricter. Read these before updating a live server.

**Trusted players can no longer break your containers.** Trust granted access to the contents and, by
accident, permission to destroy the container. Trusted players can still open and use containers;
only the owner and staff with `privatechest.admin` can break them.

**Breaking half of a double chest no longer unlocks the other half.** It used to clear the protection
on both halves, leaving the surviving chest open. Only the record at the broken position is cleared
now.

One restriction was relaxed: you can place a hopper, dropper or dispenser next to a locked container
**you have access to**. 2.x refused next to any locked container, including your own, which broke
players' own sorting systems. It is still refused next to containers you have no claim to.

---

## Performance

`InventoryMoveItemEvent` fires for every item transfer on the whole server, and 2.x called
`Block#getState()` on both the source and the destination of each one. That builds a full snapshot of
the container including a copy of its inventory. It was the plugin's single largest per-tick cost.

The registry now keeps an index of which chunks contain protections, so every hot handler starts with
one map lookup and does no block inspection at all unless something protected is actually nearby.
Double chest detection reads block data instead of block state, which is the cheap operation.

Servers with no protected containers yet pay essentially nothing.

---

## New message keys and settings

Message keys added: `too_many_attempts`, `block_name_hopper`, `block_name_dropper`,
`block_name_dispenser`.

Config section added: `security`, described above. The `automated_block_placement_denied` wording was
updated to match the relaxed rule, and the hopper block name in it is now translatable rather than
being derived from the English material name.

`/privatechest reload` now also re-applies the security settings.

---

## Minecraft version support

### Copper chests can be protected

Copper chests arrived in Minecraft 1.21.9 and could not be protected at all by 2.x. Neither could
crafters, from 1.21. Both work now, including every oxidation stage and both waxed and unwaxed.

An oxidizing copper chest changes its block type as it weathers. The plugin treats every stage as the
same container, so a chest that goes from shiny to oxidized stays locked, stays yours, and does not
shift your container count.

### Containers are no longer a fixed list

2.x carried twenty container types written into the code. That is the reason copper chests were
unsupported: the list was compiled before they existed.

The list is now resolved when the server starts, and it is yours to change:

```yaml
containers:
  auto-discover: true               # protect containers added by future Minecraft versions
  discovery-patterns:               # how they are recognised
    - "*_CHEST"
    - "*_SHULKER_BOX"
  additional: []                    # e.g. FURNACE, BLAST_FURNACE, BREWING_STAND
  excluded: ["ENDER_CHEST"]         # never protectable
  disabled: []                      # stop new locks, keep existing ones
```

With `auto-discover` on, a container added by a future Minecraft update is protectable the day it
ships, without waiting for a plugin update. Turn it off if you want an exact, fixed list.

`additional` is how you protect things the plugin does not cover by default. Furnaces, blast furnaces,
smokers and brewing stands are all valid entries.

`disabled` stops new locks on a type without touching protections players already have. The cleanup
deliberately treats a disabled type as still being a container, so turning one off never deletes
existing data.

The startup log tells you how many container types are protectable and how many were found
automatically.

### `[Private]` signs now read both sides

Signs have had a writable back since Minecraft 1.20, and the plugin only ever read the front. A
`[Private]` tag on the back locked nothing, and breaking such a sign did not release the container it
appeared to protect. Both sides are read now, on any version.

### Per-family limits replace per-colour limits

Limits and permissions key off a container *family* instead of an exact material:

`chest`, `trapped_chest`, `copper_chest`, `barrel`, `shulker_box`, `crafter`, `other`

```
privatechest.limit.<family>.<number>
privatechest.limit.<family>.unlimited
```

All seventeen shulker box colours count as `shulker_box`. Every copper chest stage counts as
`copper_chest`. This is not cosmetic: a per-material limit can never work for a container whose
material changes on its own, which is exactly what copper chests do.

**If you configured the old per-colour limits** (`container-limits.types.white_shulker_box` and
friends), replace them with the single `shulker_box` entry. The old keys are ignored. The old
per-colour permission nodes are gone as well.

### Version compatibility

The build still targets the older API on purpose, so one jar covers **Minecraft 1.16.5 through 26.2**.
Everything version-specific is resolved at runtime: container types by name, sign sides by capability
check. Features your server is too old for are skipped quietly rather than logged as errors.

### Known gap: copper golems

A copper golem physically carries items out of copper chests and into wooden ones. If your server
software reports that as a normal item transfer, the existing protection already blocks it, since the
plugin cancels transfers involving a protected container regardless of what started them. If the
server implements it as pure mob behaviour instead, there is no event to intercept and no plugin can
stop it.

I could not verify which of the two it is without a 26.x server to test against, so I am not going to
claim it is covered. If you run copper golems near protected copper chests, test it on your setup
before relying on it.

---

## Small fixes

- `/lockchest` and `/unlockchest` now use ray tracing to find the container you are looking at, the
  same method `/renamecontainer` already used. The old method walked the line of sight block by block
  and could miss a container you were clearly aiming at.
- The public API's `isLockableContainer` had its own hardcoded container list, separate from the rest
  of the plugin. It now answers from the same registry as everything else.
- Container type names shown in messages are translatable (`container_type_chest` and friends) instead
  of being English strings inside the code.

---

## New message keys

`container_type_chest`, `container_type_trapped_chest`, `container_type_copper_chest`,
`container_type_barrel`, `container_type_shulker_box`, `container_type_crafter`,
`container_type_other`.

---

## Features that were configurable but did nothing

Three subsystems were fully written in 2.x and never connected to anything. If you had configured any
of them, nothing happened.

### Per-container-type limits now work

The whole `container-limits` section of config.yml and every per-type permission node had no effect,
because the code that read them was never called. Locking always used the overall limit.

Both `/lockchest` and `[Private]` signs now go through one shared limit check, so the two can no longer
disagree about what a player may lock.

### Container names are now visible

`/renamecontainer` stored a name that was never displayed anywhere, so naming a container had no
observable effect. Names now appear in the access and denial messages, and these keys accept
`{container}` and `{owner}`:

```
not_your_chest, owner_access_notice, admin_access_notice, trusted_access_notice,
chest_break_warning, admin_chest_break_notice, locked, sign_chest_locked, limit_status
```

`{container}` is the custom name if the container has one, and the container type name otherwise. The
default wording was updated to use them, for example `not_your_chest` now reads
"This chest belongs to Steve" instead of a generic refusal.

### Bedrock message adaptation now runs

The whole Bedrock adaptation layer was dead code: not one message ever went through it, so Bedrock
players received exactly the same text as Java players. Every message the plugin sends now passes
through it.

Three bugs had to be fixed to make it usable:

- It truncated messages at 100 characters. Several of the plugin's own messages are longer than that
  once a prefix and a placeholder are filled in, so switching it on as written would have cut them off
  mid-sentence. Long messages are wrapped at word boundaries now, carrying the colour onto each line.
- Floodgate was consulted through fresh reflection on every call. It is resolved once at startup.
- Symbol replacements were extended to cover the characters the plugin's own default messages use, so
  Bedrock players stop seeing missing-glyph boxes.

On a server with no Bedrock bridge installed, the whole path is skipped after a single check.

**Not included: Floodgate forms.** A native input form for entering passwords would be a real
improvement for Bedrock players, and it is not in this release. Building it needs the Cumulus API, which
I could not add as a build dependency in this environment, and I was not willing to ship unverifiable
reflection into a protection plugin. Bedrock players can use every feature today by typing the commands,
and `[Private]` signs need no command at all.

---

## Limit fixes

### A limit permission now beats the default

`default-chest-limit` was applied first and then the larger of the two was taken, so a player granted
`privatechest.limit.1` on a server with a default of 5 was given **5**. Any permission below the default
could not restrict anyone, which is the opposite of what those nodes are for.

A limit permission now always wins. The default applies only to players who have none.

### Any number works in a limit permission

Only eleven specific values were ever checked (1, 3, 5, 10, 15, 20, 25, 50, 100, 500, 1000), so
`privatechest.limit.7` silently did nothing. The player's actual permissions are read now, so any number
works, and the most generous one wins.

---

## Cleanup no longer deletes trust lists

2.x deleted a player's entire trusted list on every cleanup pass if they owned no containers at that
moment. That quietly destroyed real data: a player who ran `/trust` before locking their first container
lost the list within half an hour, and so did anyone who temporarily broke all of their containers.

This is now **off by default** and has to be asked for:

```yaml
auto-cleanup:
  remove-unused-trust: false
```

The rest of the cleanup is configurable for the first time, rather than fixed in code:

```yaml
auto-cleanup:
  startup-enabled: true
  startup-delay-seconds: 10     # give world managers time to mount worlds
  periodic-enabled: true
  interval-minutes: 30          # 0 disables periodic passes
  max-chunks-per-pass: 200      # spread a large dataset over several passes
  chunks-per-tick: 20           # gentler on single-threaded servers
```

---

## Other fixes

- Missing message keys are now reported in the console with the key name and shown to the player as
  `[key_name]`, instead of the literal text "Message not found: key". An admin can actually act on that.
- `/privatechest status` also reports how many container types are protectable on your server.
- Two message keys that could never be reached were removed: `limit_error` and `sign_not_your_chest`.
  The second was unreachable in 2.x as well, because the check above it always returned first.

---

## New message keys

`limit_exceeded_type`, `sign_limit_exceeded_type`, `status_container_types`.

Reworded defaults, which your existing files keep unless you delete the key: `locked`,
`not_your_chest`, `sign_chest_locked`, `limit_exceeded`, `sign_limit_exceeded`, `limit_warning`,
`limit_reached`, `limit_status`, `owner_access_notice`, `admin_access_notice`,
`trusted_access_notice`, `chest_break_warning`, `admin_chest_break_notice`.

---

## Still to come in 3.0

Full translatability: the remaining hardcoded strings, a translatable `[Private]` sign tag, and
per-language message files. Then documentation and tests.
