# AnyPlugin — User Guide

Plugin version: 1.0.2 · Paper, Purpur and Folia 26.3 · Java 25

[Русская версия](USER_GUIDE_RU.md)

## Contents

1. [What it is](#what-it-is)
2. [Installation](#installation)
3. [First server setup](#first-server-setup)
4. [Modules](#modules)
5. [Permissions](#permissions)
6. [Commands](#commands)
7. [`config.yml` settings](#configyml-settings)
8. [Admin panel](#admin-panel)
9. [Placeholders](#placeholders)
10. [Folia](#folia)
11. [FAQ](#faq)
12. [Building](#building)

## What it is

AnyPlugin is one plugin with everything a small friends server needs: permissions and groups, chat and private messages, homes, warps, teleports, tab list and name tags, player utilities, moderation, maintenance mode, scheduled restarts and a starter kit. It replaces EssentialsX + LuckPerms (the basics) + TAB + LiteBans.

- Minecraft **26.3**, Java 25, Paper, Purpur or Folia.
- One `AnyPlugin-<version>.jar`, no other plugins required. Vault, PlaceholderAPI and LuckPerms are picked up when installed.
- Data lives in `plugins/AnyPlugin/data.db` (SQLite in WAL mode). Writes run on a separate thread and never slow the tick.
- Every module can be turned off if you prefer another plugin for that job.

## Installation

1. Put `build/libs/AnyPlugin-1.0.2.jar` into `plugins/` and start the server.
2. `config.yml`, `groups.yml` and `messages_ru.yml` appear in `plugins/AnyPlugin/`. Set `language: en` in `config.yml` for English texts (`messages_en.yml`).
3. Stand at the spawn and run `/setspawn`, then create warps with `/setwarp`.
4. Give yourself a group: `/perm user <name> group set admin` (from the console or as an operator).

If EssentialsX, TAB or LiteBans is installed, remove it or turn off the matching AnyPlugin module in `config.yml` (`modules`); otherwise their commands fight over the same names.

## First server setup

1. Stop the server and remove plugins that do the same jobs: EssentialsX, TAB, LiteBans. Or turn off the matching AnyPlugin modules in `config.yml` → `modules`.
2. Start the server with AnyPlugin. From the console, give yourself the admin group: `perm user <your_name> group set admin`.
3. Join the game, stand at the spawn point and run `/setspawn`.
4. Create warps: `/setwarp shop`, `/setwarp farm`. You can add an icon and a description right away: `/setwarp shop emerald Market by the spawn`.
5. Adjust the groups: `/perm groups`, `/perm group friend prefix <green>[Friend] `. The defaults are `default`, `friend`, `moder` and `admin`.
6. Open `plugins/AnyPlugin/messages_<language>.yml` and change the tab header, MOTD, rules and announcements. Then run `/ap reload`.
7. Check the result: `/ap modules` lists the enabled modules, `/ap log` shows the staff action log.

Default groups:

| Group | Weight | Prefix | Permissions |
|---|---|---|---|
| `default` | 0 | grey name | `anyplugin.player` |
| `friend` | 10 | `[Друг]` | everything from default, 5 homes, crafting table, ender chest, `/hat`, nicknames, chat colors |
| `moder` | 50 | `[Модер]` | everything from friend, `anyplugin.moderator`, 10 homes |
| `admin` | 100 | `[Админ]` | `*`, every permission |

The default prefixes are in Russian; change them with `/perm group <group> prefix <text>`.

## Modules

Each module can be turned off in `config.yml` → `modules`. Turning a module on or off needs a server restart; everything else applies with `/ap reload`.

| Module | What it does |
|---|---|
| `permissions` | Groups, inheritance, weights, prefixes, temporary permissions and groups, `/perm`, Vault providers. With LuckPerms installed the module steps aside and prefixes come from Vault |
| `chat` | Chat format per group, colors by permission, `@name` mentions with a sound, `[item]`, anti-spam, `/msg`, `/r`, `/ignore`, join and leave messages, death coordinates |
| `teleport` | Spawn, `/back`, homes, warps with a menu, `/tpa`, `/rtp`, admin teleports. Warm-up is cancelled by movement and damage, cooldowns per kind |
| `tab` | Tab header and footer, name with prefix, sorting by group weight, name tags above heads, the server line in the server list |
| `utility` | Game modes, flight, speed, god mode, heal, workstations, ender chest, nicknames, AFK, `/list`, `/whois`, `/seen`, `/playtime` and more |
| `moderation` | Bans, IP bans, mutes, warnings with automatic punishments, kicks, history, vanish, freeze, inventory view, spying, staff chat, reports, log |
| `world` | Spawn protection, PvP, creeper explosions, endermen, fire, phantoms, sleeping percentage |
| `maintenance` | Maintenance mode: only players with the permission or on the whitelist can join, separate MOTD |
| `restart` | Restart with a chat countdown and a boss bar, daily schedule |
| `starter-kit` | A stone kit on the very first join |
| `info` | `/rules`, `/motd`, `/help`, `/serverinfo`, link commands, automatic announcements |
| `admin` | The `anyplugin:admin` channel for the AnyPlugin Admin client mod: a panel on F6 or `/apgui` |

## Permissions

`plugin.yml` has three bundles:

- `anyplugin.player` (everyone by default): spawn, homes, warps, tpa, rtp, back, private messages, ignore, afk, list, ping, seen, playtime, rules, reports, trash, `[item]` in chat.
- `anyplugin.moderator` (operators by default): everything from player, plus bans, mutes, warnings, kick, history, vanish, invsee, freeze, spying, staff chat, reports, flight, heal, survival, adventure and spectator modes.
- `anyplugin.admin` (operators by default): everything from moderator, plus `/perm`, `/ap`, spawn and warp setup, maintenance, restart, item giving, time and weather, god mode, speed, workstations, nicknames, creative.

Single permissions: `anyplugin.homes.<number>` and `anyplugin.homes.unlimited` (home limit), `anyplugin.chat.color` (chat colors), `anyplugin.nick.color`, `anyplugin.punish.exempt` (protection from punishments), `anyplugin.vanish.see`, `anyplugin.teleport.bypass-warmup` and `anyplugin.teleport.bypass-cooldown`, `anyplugin.spawnprotection.bypass`, `anyplugin.maintenance.bypass`, `anyplugin.warp.<name>` (when `warps.per-warp-permission` is on), and the vanilla `minecraft.command.<command>`.

### Groups

`groups.yml` contains `default`, `friend`, `moder` and `admin` by default. A group has a weight (higher means higher in the tab list and a more important prefix), a prefix and suffix in MiniMessage, a name tag color (`name-color`, one of the 16 Minecraft colors), parents and a permission list. `-permission` denies, `prefix.*` and `*` are wildcards.

```
/perm groups
/perm group friend set anyplugin.rtp false
/perm group friend parent add default
/perm group moder prefix <aqua>[Mod]
/perm user Vasya group add friend 30d
/perm user Vasya set anyplugin.fly true 2h
/perm user Vasya prefix <gold>[Builder]
/perm check Vasya anyplugin.fly
/perm reload
```

Player permissions are stored in the database, groups in `groups.yml`. In-game changes are written to the file at once.

Wildcards expand over the permissions registered on the server (every plugin and the vanilla commands). If a plugin checks a permission it never registered, `*` will not cover it: grant that permission explicitly.

## Commands

| Command | What it does | Permission |
|---|---|---|
| `/spawn [name]`, `/setspawn` | Spawn | `spawn`, `spawn.others`, `setspawn` |
| `/back` | Return to the previous place or the death point | `back`, `back.death` |
| `/sethome [name]`, `/home [name\|bed\|player:name]`, `/delhome <name>`, `/homes [player]` | Homes | `sethome`, `home`, `delhome`, `home.others` |
| `/warp [name] [player]`, `/warps`, `/setwarp <name> [icon] [description]`, `/delwarp <name>` | Warps, menu | `warp`, `warp.others`, `setwarp`, `delwarp` |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/tptoggle` | Teleport requests | `tpa`, `tpahere`, `tptoggle` |
| `/rtp [player]` | A random safe location | `rtp`, `rtp.others` |
| `/tphere`, `/tpall`, `/tppos x y z [world] [player]`, `/top` | Admin teleports (vanilla `/tp` is left alone) | `tphere`, `tpall`, `tppos`, `top` |
| `/msg`, `/r`, `/ignore`, `/msgtoggle`, `/socialspy` | Private messages | `msg`, `ignore`, `msgtoggle`, `socialspy` |
| `/broadcast`, `/clearchat` | Announcement, clear chat | `broadcast`, `clearchat` |
| `/gm <mode> [player]`, `/gms`, `/gmc`, `/gma`, `/gmsp` | Game mode (replaces `/gamemode`) | `gamemode`, `gamemode.<mode>`, `gamemode.others` |
| `/fly`, `/speed`, `/god`, `/heal`, `/feed` | Player state | same names and `.others` |
| `/repair [all]`, `/hat`, `/suicide`, `/trash`, `/enderchest [player]` | Items | same names |
| `/craft`, `/anvil`, `/stonecutter`, `/grindstone`, `/smithing`, `/loom`, `/cartography` | Workstations without a block | same names |
| `/i <item> [amount]`, `/more`, `/skull [player]` | Item giving (vanilla `/give` is untouched) | `item`, `more`, `skull` |
| `/day`, `/night`, `/sun`, `/rain`, `/thunder` | Time and weather | `time`, `weather` |
| `/list`, `/near`, `/ping`, `/seen`, `/whois`, `/playtime [player\|top]` | Player info | same names |
| `/nick <name\|off> [player]`, `/realname` | Display name | `nick`, `nick.color`, `nick.others`, `realname` |
| `/afk` | Away (automatic AFK on a timer) | `afk` |
| `/ban`, `/tempban`, `/ipban`, `/unban` | Bans, the duration is optional: `/ban Vasya 7d griefing` | `ban`, `ipban`, `unban` |
| `/mute`, `/tempmute`, `/unmute` | Mutes | `mute`, `unmute` |
| `/warn`, `/unwarn <#>`, `/warnings [player]` | Warnings, automatic punishment from `moderation.warn-actions` | `warn`, `unwarn`, `warnings` |
| `/kick`, `/history <player>` | Kick, punishment history | `kick`, `history` |
| `/vanish`, `/invsee`, `/freeze`, `/commandspy`, `/sc [text]` | Staff tools | same names, `staffchat` |
| `/report <player> <text>`, `/reports [all\|close\|tp]` | Reports | `report`, `reports` |
| `/maintenance on\|off`, `/restart <time\|now\|cancel>` | Server | `maintenance`, `restart` |
| `/perm ...`, `/ap reload\|modules\|log` | Plugin management | `perm`, `manage` |
| `/rules`, `/motd`, `/help`, `/serverinfo`, `/discord` | Information | `rules`, `motd`, `help`, `serverinfo` |
| `/apgui` | Open the admin panel (needs the AnyPlugin Admin mod) | `gui` |

Every permission starts with `anyplugin.`. Durations look like `30m`, `2h`, `7d`, `1d12h` (Russian `7д` works too). Reason templates from `moderation.templates` are used with a hash: `/ban Vasya #grief`.

## `config.yml` settings

| Key | Default | Meaning |
|---|---|---|
| `language` | `ru` | Text language: `ru` or `en` (`messages_<language>.yml`) |
| `chat.format` | `<prefix><name><suffix> » <message>` | Chat format when the group has no `chat-format` of its own in `groups.yml` |
| `chat.cooldown-ms`, `chat.block-repeats`, `chat.max-caps-percent` | 1000, true, 70 | Anti-spam; bypassed with `anyplugin.chat.bypass-spam` |
| `chat.mentions`, `chat.item-tag`, `chat.death-coordinates` | true | Mentions, `[item]`, death coordinates sent privately to the player |
| `staff-chat.prefix` | `#` | A message starting with this goes to staff chat |
| `teleport.warmup-seconds` | 3 | Teleport warm-up |
| `teleport.cooldowns.<kind>` | tpa 10, rtp 300 | Cooldown in seconds for spawn, home, warp, tpa, back, rtp |
| `spawn.respawn-at-spawn`, `spawn.teleport-on-first-join` | true | Respawn at the spawn (without a bed) and spawn on first join |
| `homes.default-limit` | 3 | Home limit without `anyplugin.homes.<n>` |
| `rtp.min-radius`, `rtp.max-radius`, `rtp.worlds` | 300, 5000, all | Random teleport radii and worlds |
| `tab.name-format`, `tab.sort-by-weight` | | Tab name and sorting by group weight |
| `nametags.enabled`, `nametags.prefix`, `nametags.suffix` | true | Name tags above heads |
| `server-list.max-players` | -1 | Slot count shown in the server list (-1 is the real one) |
| `afk.auto-seconds`, `afk.kick-seconds` | 300, 0 | Automatic AFK and kick for inactivity (0 is off) |
| `moderation.broadcast-to-all` | false | Everyone sees punishments, not only staff |
| `moderation.warn-actions` | 3 → mute 1 h, 5 → ban 1 d, 7 → ban | What happens at N active warnings |
| `world.sleep-percentage` | 30 | Percentage of sleepers to skip the night (-1 leaves it alone) |
| `world.spawn-protection.radius` | 16 | Protection around `/setspawn`, 0 turns it off |
| `world.pvp`, `world.creeper-block-damage`, `world.enderman-grief`, `world.fire-spread`, `world.phantoms` | true | World rule switches |
| `restart.daily` | `[]` | Daily restarts, for example `["05:00"]` |
| `starter-kit.items` | stone tools and sword, 16 bread | First-join kit: `item [amount]` |
| `announcer.interval-seconds` | 900 | Automatic announcements from `announcements` in the messages file |
| `links` | `discord` | Each key becomes a command (`/discord`), the value is MiniMessage |

Every text, the tab header, the server list MOTD, the rules and the announcements live in `messages_<language>.yml` (MiniMessage). Keys added by plugin updates are taken from the jar until they appear in your file.

## Admin panel

The AnyPlugin Admin client mod (Fabric 26.3, a separate project, not published yet) opens a panel on F6 or `/apgui`: players, groups and permissions, warps, `config.yml` settings and server control. Only players with `anyplugin.gui` (part of `anyplugin.admin`, operators by default) can open it; for everyone else the command does not exist.

- Messages travel as UTF-8 JSON over the `anyplugin:admin` channel; incoming ones are limited to 32 KB and 20 messages per second per player.
- Almost every panel action becomes a normal plugin command run as the player (`performCommand`), so permissions, the staff log and chat replies are the same as when typing by hand. Arguments are checked against patterns (name, group, permission, duration) before the command is built.
- Settings change through a separate `config.set` operation, only with `anyplugin.manage` and only for keys that exist in the jar's `config.yml` (except `moderation.templates`, `moderation.warn-actions` and `links`); the value type is checked against the original. Setting labels come from `setting-labels` and `setting-sections` in the messages file, so the mod does not need an update for new keys.

## Placeholders

With PlaceholderAPI installed: `%anyplugin_prefix%`, `%anyplugin_suffix%`, `%anyplugin_group%`, `%anyplugin_nick%`, `%anyplugin_afk%`, `%anyplugin_vanished%`, `%anyplugin_playtime%`, `%anyplugin_playtime_hours%`, `%anyplugin_homes%`, `%anyplugin_muted%`, `%anyplugin_online%`, `%anyplugin_unique%`. Other plugins' placeholders work in the tab header and footer.

The tab header and MOTD accept the tags `<online>`, `<max>`, `<unique>`, `<ping>`, `<tps>`, `<mspt>`, `<world>`, `<player>`, `<name>`, `<group>`, `<time>`, `<uptime>`, `<playtime>`.

## Folia

- Everything that touches a player runs on that player's scheduler; world work runs on the global or region scheduler, and teleports use `teleportAsync`.
- The scoreboard API does not work on Folia, so name tags are sent to clients as team packets directly, and the tab order uses `setPlayerListOrder`. If the server build does not match the packet code, name tags turn off with a warning in the log and everything else keeps working.
- On Folia, TPS and MSPT in `/serverinfo` and in the tab list are those of the player's region.
- `/invsee` and `/enderchest <player>` for a player in another region open a read-only snapshot.
- Tested on Folia 26.3 build 3 (2026-10-20): the plugin enables and console commands work. From the console TPS shows a dash: Folia has no global TPS, only per region.

## FAQ

**Commands clash with another plugin.** AnyPlugin takes over `msg`, `list`, `ban`, `kick`, `help` and `gamemode`. If EssentialsX, TAB or LiteBans is installed, turn off the matching AnyPlugin modules or remove those plugins.

**LuckPerms is installed — what happens to permissions?** The `permissions` module steps aside: LuckPerms handles permissions and groups, and AnyPlugin reads chat and tab prefixes through Vault.

**How do I grant a permission for a limited time?** Add a duration at the end: `/perm user Vasya set anyplugin.fly true 2h` or `/perm user Vasya group add friend 30d`.

**`*` did not grant a permission from another plugin.** Wildcards expand only over registered permissions. If a plugin checks an unregistered one, grant it explicitly.

**Name tags do not show.** Check the startup log: if the server build does not match the packet code, name tags turn off with a warning and everything else keeps working.

**How do I restore a default text?** Delete the key from the messages file and run `/ap reload`: missing keys are taken from the jar.

**Where is the database and how do I back it up?** Everything is in `plugins/AnyPlugin/data.db`. To back it up, stop the server and copy the file together with `data.db-wal` and `data.db-shm` if they exist.

## Building

JDK 25 is required.

```powershell
git clone https://github.com/ServChan/AnyPlugin.git
cd AnyPlugin
.\gradlew.bat build
```

The jar is `build/libs/AnyPlugin-<version>.jar`. The build uses paperweight-userdev (Paper dev bundle `26.3.build.140-beta`, no reobfuscation) because name tags use server classes. The SQLite driver comes from the server (Paper, Purpur and Folia ship `sqlite-jdbc`).

Test server: `.\gradlew.bat runServer` (Paper 26.3) or `.\gradlew.bat runFolia`.
