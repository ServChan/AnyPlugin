# AnyPlugin

[![Minecraft Version](https://img.shields.io/badge/Minecraft-26.3-brightgreen?style=flat-square&logo=minecraft)](README.md)
[![Platform](https://img.shields.io/badge/Platform-Paper%20%7C%20Purpur%20%7C%20Folia-blue?style=flat-square)](README.md)
[![Java Target](https://img.shields.io/badge/Java-25-orange?style=flat-square&logo=openjdk)](README.md)
[![Plugin Version](https://img.shields.io/badge/Version-1.0.2-purple?style=flat-square)](README.md)
[![License](https://img.shields.io/badge/License-MIT-yellow?style=flat-square)](LICENSE)

Everything a small friends server needs in one plugin: permissions and groups, chat, homes, warps, teleports, tab list and name tags, moderation, maintenance and scheduled restarts. Paper, Purpur and Folia 26.3.

**Руководство / User guide:** [Русский](docs/USER_GUIDE_RU.md) · [English](docs/USER_GUIDE_EN.md)

## Русский

### Что это

Один плагин со всем базовым для небольшого сервера друзей. Заменяет связку EssentialsX + LuckPerms (в базовом объёме) + TAB + LiteBans. Каждый модуль выключается, если для этой задачи у вас другой плагин.

### Возможности

- **Права и группы**: наследование, веса, префиксы и суффиксы в MiniMessage, временные права и группы, Vault. С LuckPerms модуль уступает ему.
- **Чат**: формат по группам, упоминания `@ник` со звуком, `[item]`, антиспам, личные сообщения, игнор, чат персонала.
- **Телепорты**: спавн, `/back`, дома, варпы с меню, `/tpa`, `/rtp`, прогрев и кулдауны.
- **Таб**: шапка и подвал, сортировка по весу группы, таблички над головой, строка в списке серверов.
- **Утилиты**: режимы игры, полёт, лечение, станки без блока, ники, AFK, `/seen`, `/playtime`.
- **Модерация**: баны (и по IP), муты, предупреждения с автонаказанием, история, ваниш, заморозка, просмотр инвентаря, жалобы, журнал.
- **Сервер**: защита спавна и правила мира, техработы, перезапуск по расписанию, стартовый набор, правила и автообъявления.
- Данные в SQLite (WAL), запись в отдельном потоке. Работает на Folia.
- PlaceholderAPI: `%anyplugin_prefix%`, `%anyplugin_group%`, `%anyplugin_playtime%` и другие.

### Быстрый старт

1. Положите `AnyPlugin-1.0.2.jar` в `plugins/` и запустите сервер.
2. Из консоли: `perm user <ваш_ник> group set admin`.
3. В игре: `/setspawn`, затем `/setwarp <имя>`.
4. Поправьте тексты в `plugins/AnyPlugin/messages_ru.yml` и выполните `/ap reload`.

Если на сервере есть EssentialsX, TAB или LiteBans, уберите их или выключите соответствующие модули в `config.yml` → `modules`.

Все модули, команды, права и настройки — в [руководстве](docs/USER_GUIDE_RU.md).

### Требования

Paper, Purpur или Folia 26.3, Java 25. Vault, PlaceholderAPI и LuckPerms — по желанию.

### Сборка

```powershell
.\gradlew.bat build
```

Готовый файл: `build/libs/AnyPlugin-1.0.2.jar`. Тестовый сервер: `.\gradlew.bat runServer` или `.\gradlew.bat runFolia`.

---

## English

### What It Is

One plugin with everything a small friends server needs. It replaces EssentialsX + LuckPerms (the basics) + TAB + LiteBans. Each module can be turned off if you use another plugin for that job.

### Features

- **Permissions and groups**: inheritance, weights, MiniMessage prefixes and suffixes, temporary permissions and groups, Vault. With LuckPerms installed the module steps aside.
- **Chat**: per-group format, `@name` mentions with a sound, `[item]`, anti-spam, private messages, ignore, staff chat.
- **Teleports**: spawn, `/back`, homes, warps with a menu, `/tpa`, `/rtp`, warm-up and cooldowns.
- **Tab list**: header and footer, sorting by group weight, name tags above heads, the server list line.
- **Utilities**: game modes, flight, heal, workstations without a block, nicknames, AFK, `/seen`, `/playtime`.
- **Moderation**: bans (IP too), mutes, warnings with automatic punishments, history, vanish, freeze, inventory view, reports, log.
- **Server**: spawn protection and world rules, maintenance mode, scheduled restarts, starter kit, rules and automatic announcements.
- SQLite (WAL) storage written on a separate thread. Runs on Folia.
- PlaceholderAPI: `%anyplugin_prefix%`, `%anyplugin_group%`, `%anyplugin_playtime%` and more.

### Quick Start

1. Put `AnyPlugin-1.0.2.jar` into `plugins/` and start the server.
2. From the console: `perm user <your_name> group set admin`.
3. In game: `/setspawn`, then `/setwarp <name>`.
4. Set `language: en` in `config.yml` for English texts, edit `plugins/AnyPlugin/messages_en.yml` and run `/ap reload`.

If EssentialsX, TAB or LiteBans is installed, remove it or turn off the matching modules in `config.yml` → `modules`.

Every module, command, permission and setting is in the [user guide](docs/USER_GUIDE_EN.md).

### Requirements

Paper, Purpur or Folia 26.3, Java 25. Vault, PlaceholderAPI and LuckPerms are optional.

### Building

```powershell
.\gradlew.bat build
```

Output: `build/libs/AnyPlugin-1.0.2.jar`. Test server: `.\gradlew.bat runServer` or `.\gradlew.bat runFolia`.

## Лицензия / License

[MIT](LICENSE).
