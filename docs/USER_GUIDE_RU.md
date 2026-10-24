# AnyPlugin — руководство

Версия плагина: 1.0.2 · Paper, Purpur и Folia 26.3 · Java 25

[English version](USER_GUIDE_EN.md)

## Содержание

1. [Что это](#что-это)
2. [Установка](#установка)
3. [Первая настройка сервера](#первая-настройка-сервера)
4. [Модули](#модули)
5. [Права](#права)
6. [Команды](#команды)
7. [Настройки `config.yml`](#настройки-configyml)
8. [Админ-панель](#админ-панель)
9. [Плейсхолдеры](#плейсхолдеры)
10. [Folia](#folia)
11. [Частые вопросы](#частые-вопросы)
12. [Сборка](#сборка)

## Что это

AnyPlugin — один плагин со всем базовым для небольшого сервера друзей: права и группы, чат и личные сообщения, дома, варпы, телепорты, таб и таблички над головой, утилиты игрока, модерация, техработы, перезапуск по расписанию и стартовый набор. Он заменяет связку EssentialsX + LuckPerms (в базовом объёме) + TAB + LiteBans.

- Minecraft **26.3**, Java 25, Paper, Purpur или Folia.
- Один файл `AnyPlugin-<версия>.jar`, сторонние плагины не нужны. Vault, PlaceholderAPI и LuckPerms подхватываются, если стоят.
- Данные хранятся в `plugins/AnyPlugin/data.db` (SQLite, режим WAL). Запись идёт в отдельном потоке и не тормозит тик.
- Каждый модуль можно выключить, если вам нужен другой плагин для этой задачи.

## Установка

1. Положите `build/libs/AnyPlugin-1.0.2.jar` в `plugins/` и запустите сервер.
2. В `plugins/AnyPlugin/` появятся `config.yml`, `groups.yml` и `messages_ru.yml`.
3. Встаньте на спавн и выполните `/setspawn`, затем создайте варпы через `/setwarp`.
4. Выдайте себе группу: `/perm user <ник> group set admin` (из консоли или будучи оператором).

Если на сервере стоит EssentialsX, TAB или LiteBans, уберите их или выключите соответствующий модуль AnyPlugin в `config.yml` (`modules`), иначе команды будут перехватывать друг друга.

## Первая настройка сервера

1. Остановите сервер и уберите плагины, которые делают то же самое: EssentialsX, TAB, LiteBans. Либо выключите в `config.yml` → `modules` модули AnyPlugin, которые им соответствуют.
2. Запустите сервер с AnyPlugin. Из консоли выдайте себе группу администратора: `perm user <ваш_ник> group set admin`.
3. Зайдите в игру, встаньте на место спавна и выполните `/setspawn`.
4. Создайте варпы: `/setwarp shop`, `/setwarp farm`. Иконку и описание можно указать сразу: `/setwarp shop emerald Рынок у спавна`.
5. Поправьте группы под себя: `/perm groups`, `/perm group friend prefix <green>[Друг] `. По умолчанию есть `default`, `friend`, `moder`, `admin`.
6. Откройте `plugins/AnyPlugin/messages_ru.yml` и поменяйте шапку таба, MOTD, правила и объявления. Затем `/ap reload`.
7. Проверьте, что всё работает: `/ap modules` показывает включённые модули, `/ap log` — журнал действий персонала.

Группы по умолчанию:

| Группа | Вес | Префикс | Права |
|---|---|---|---|
| `default` | 0 | серый ник | `anyplugin.player` |
| `friend` | 10 | `[Друг]` | всё из default, 5 домов, верстак, эндер-сундук, `/hat`, ники, цвета в чате |
| `moder` | 50 | `[Модер]` | всё из friend, `anyplugin.moderator`, 10 домов |
| `admin` | 100 | `[Админ]` | `*` — все права |

## Модули

Каждый модуль выключается в `config.yml` → `modules`. Включение и выключение модуля требует перезапуска сервера, остальное применяется командой `/ap reload`.

| Модуль | Что делает |
|---|---|
| `permissions` | Группы, наследование, веса, префиксы, временные права и группы, `/perm`, провайдеры Vault. Если стоит LuckPerms, модуль уступает ему, а префиксы берутся из Vault |
| `chat` | Формат чата по группам, цвета по праву, упоминания `@ник` со звуком, `[item]`, антиспам, `/msg`, `/r`, `/ignore`, сообщения входа и выхода, координаты смерти |
| `teleport` | Спавн, `/back`, дома, варпы с меню, `/tpa`, `/rtp`, админские телепорты. Прогрев отменяется при движении и уроне, кулдауны по видам |
| `tab` | Шапка и подвал таба, ник с префиксом, сортировка по весу группы, таблички над головой, строка сервера в списке серверов |
| `utility` | Режимы игры, полёт, скорость, бессмертие, лечение, станки, эндер-сундук, ники, AFK, `/list`, `/whois`, `/seen`, `/playtime` и другое |
| `moderation` | Баны, баны по IP, муты, предупреждения с автонаказанием, кики, история, ваниш, заморозка, просмотр инвентаря, слежка, чат персонала, жалобы, журнал |
| `world` | Защита спавна, PvP, взрывы криперов, эндермены, огонь, фантомы, процент спящих |
| `maintenance` | Режим техработ: вход только с правом или из белого списка, отдельный MOTD |
| `restart` | Перезапуск с отсчётом в чате и полосой босса, ежедневное расписание |
| `starter-kit` | Каменный набор при самом первом входе на сервер |
| `info` | `/rules`, `/motd`, `/help`, `/serverinfo`, команды-ссылки, автообъявления |
| `admin` | Канал `anyplugin:admin` для клиентского мода AnyPlugin Admin: панель по F6 или `/apgui` |

## Права

В `plugin.yml` три набора:

- `anyplugin.player` (по умолчанию у всех): спавн, дома, варпы, tpa, rtp, back, личные сообщения, ignore, afk, list, ping, seen, playtime, правила, жалобы, мусорка, `[item]` в чате.
- `anyplugin.moderator` (по умолчанию у операторов): всё из player, плюс баны, муты, варны, кик, история, ваниш, invsee, заморозка, слежка, чат персонала, жалобы, полёт, лечение, режимы survival, adventure и spectator.
- `anyplugin.admin` (по умолчанию у операторов): всё из moderator, плюс `/perm`, `/ap`, спавн и варпы, техработы, перезапуск, выдача предметов, время и погода, бессмертие, скорость, станки, ники, creative.

Отдельные права: `anyplugin.homes.<число>` и `anyplugin.homes.unlimited` (лимит домов), `anyplugin.chat.color` (цвета в чате), `anyplugin.nick.color`, `anyplugin.punish.exempt` (защита от наказаний), `anyplugin.vanish.see`, `anyplugin.teleport.bypass-warmup` и `anyplugin.teleport.bypass-cooldown`, `anyplugin.spawnprotection.bypass`, `anyplugin.maintenance.bypass`, `anyplugin.warp.<имя>` (если включено `warps.per-warp-permission`), ванильные `minecraft.command.<команда>`.

### Группы

`groups.yml` по умолчанию содержит `default`, `friend`, `moder`, `admin`. У группы есть вес (чем больше, тем выше в табе и тем важнее префикс), префикс и суффикс в MiniMessage, цвет ника над головой (`name-color`, 16 цветов Minecraft), родители и список прав. `-право` запрещает, `префикс.*` и `*` — маски.

```
/perm groups
/perm group friend set anyplugin.rtp false
/perm group friend parent add default
/perm group moder prefix <aqua>[Модер]
/perm user Vasya group add friend 30d
/perm user Vasya set anyplugin.fly true 2h
/perm user Vasya prefix <gold>[Строитель]
/perm check Vasya anyplugin.fly
/perm reload
```

Права игроков хранятся в базе, группы — в `groups.yml`. Изменения в игре сразу записываются в файл.

Маски раскрываются по правам, зарегистрированным на сервере (все плагины и ванильные команды). Если плагин проверяет право, которое нигде не зарегистрировано, маска `*` его не покроет: выдайте такое право явно.

## Команды

| Команда | Что делает | Право |
|---|---|---|
| `/spawn [ник]`, `/setspawn` | Спавн | `spawn`, `spawn.others`, `setspawn` |
| `/back` | Вернуться на прошлое место или к месту смерти | `back`, `back.death` |
| `/sethome [имя]`, `/home [имя\|bed\|ник:имя]`, `/delhome <имя>`, `/homes [ник]` | Дома | `sethome`, `home`, `delhome`, `home.others` |
| `/warp [имя] [ник]`, `/warps`, `/setwarp <имя> [иконка] [описание]`, `/delwarp <имя>` | Варпы, меню | `warp`, `warp.others`, `setwarp`, `delwarp` |
| `/tpa`, `/tpahere`, `/tpaccept`, `/tpdeny`, `/tpacancel`, `/tptoggle` | Запросы на телепорт | `tpa`, `tpahere`, `tptoggle` |
| `/rtp [ник]` | Случайная безопасная точка | `rtp`, `rtp.others` |
| `/tphere`, `/tpall`, `/tppos x y z [мир] [ник]`, `/top` | Админские телепорты (обычный `/tp` остаётся ванильным) | `tphere`, `tpall`, `tppos`, `top` |
| `/msg`, `/r`, `/ignore`, `/msgtoggle`, `/socialspy` | Личные сообщения | `msg`, `ignore`, `msgtoggle`, `socialspy` |
| `/broadcast`, `/clearchat` | Объявление, очистка чата | `broadcast`, `clearchat` |
| `/gm <режим> [ник]`, `/gms`, `/gmc`, `/gma`, `/gmsp` | Режим игры (заменяет `/gamemode`) | `gamemode`, `gamemode.<режим>`, `gamemode.others` |
| `/fly`, `/speed`, `/god`, `/heal`, `/feed` | Состояние игрока | одноимённые и `.others` |
| `/repair [all]`, `/hat`, `/suicide`, `/trash`, `/enderchest [ник]` | Предметы | одноимённые |
| `/craft`, `/anvil`, `/stonecutter`, `/grindstone`, `/smithing`, `/loom`, `/cartography` | Станки без блока | одноимённые |
| `/i <предмет> [кол-во]`, `/more`, `/skull [ник]` | Выдача предметов (ванильный `/give` не тронут) | `item`, `more`, `skull` |
| `/day`, `/night`, `/sun`, `/rain`, `/thunder` | Время и погода | `time`, `weather` |
| `/list`, `/near`, `/ping`, `/seen`, `/whois`, `/playtime [ник\|top]` | Сведения об игроках | одноимённые |
| `/nick <ник\|off> [игрок]`, `/realname` | Отображаемый ник | `nick`, `nick.color`, `nick.others`, `realname` |
| `/afk` | Отошёл (авто-AFK по таймеру) | `afk` |
| `/ban`, `/tempban`, `/ipban`, `/unban` | Баны, срок необязателен: `/ban Vasya 7d гриф` | `ban`, `ipban`, `unban` |
| `/mute`, `/tempmute`, `/unmute` | Муты | `mute`, `unmute` |
| `/warn`, `/unwarn <№>`, `/warnings [ник]` | Предупреждения, автонаказание по `moderation.warn-actions` | `warn`, `unwarn`, `warnings` |
| `/kick`, `/history <ник>` | Кик, история наказаний | `kick`, `history` |
| `/vanish`, `/invsee`, `/freeze`, `/commandspy`, `/sc [текст]` | Инструменты персонала | одноимённые, `staffchat` |
| `/report <ник> <текст>`, `/reports [all\|close\|tp]` | Жалобы | `report`, `reports` |
| `/maintenance on\|off`, `/restart <срок\|now\|cancel>` | Сервер | `maintenance`, `restart` |
| `/perm ...`, `/ap reload\|modules\|log` | Управление плагином | `perm`, `manage` |
| `/rules`, `/motd`, `/help`, `/serverinfo`, `/discord` | Информация | `rules`, `motd`, `help`, `serverinfo` |
| `/apgui` | Открыть админ-панель (нужен мод AnyPlugin Admin) | `gui` |

Все права начинаются с `anyplugin.`. Сроки пишутся как `30m`, `2h`, `7d`, `1d12h` (можно и `7д`). Шаблоны причин из `moderation.templates` используются через решётку: `/ban Vasya #grief`.

## Настройки `config.yml`

| Ключ | По умолчанию | Смысл |
|---|---|---|
| `language` | `ru` | Язык текстов: `ru` или `en` (`messages_<язык>.yml`) |
| `chat.format` | `<prefix><name><suffix> » <message>` | Формат чата, если у группы нет своего `chat-format` в `groups.yml` |
| `chat.cooldown-ms`, `chat.block-repeats`, `chat.max-caps-percent` | 1000, true, 70 | Антиспам; обходится правом `anyplugin.chat.bypass-spam` |
| `chat.mentions`, `chat.item-tag`, `chat.death-coordinates` | true | Упоминания, `[item]`, координаты смерти лично игроку |
| `staff-chat.prefix` | `#` | Сообщение с этим началом уходит в чат персонала |
| `teleport.warmup-seconds` | 3 | Прогрев телепорта |
| `teleport.cooldowns.<вид>` | tpa 10, rtp 300 | Кулдаун в секундах для spawn, home, warp, tpa, back, rtp |
| `spawn.respawn-at-spawn`, `spawn.teleport-on-first-join` | true | Возрождение на спавне (если нет кровати) и спавн при первом входе |
| `homes.default-limit` | 3 | Лимит домов без прав `anyplugin.homes.<n>` |
| `rtp.min-radius`, `rtp.max-radius`, `rtp.worlds` | 300, 5000, все | Радиусы и миры случайного телепорта |
| `tab.name-format`, `tab.sort-by-weight` | | Ник в табе и сортировка по весу группы |
| `nametags.enabled`, `nametags.prefix`, `nametags.suffix` | true | Таблички над головой |
| `server-list.max-players` | -1 | Показываемое число слотов (-1 — настоящее) |
| `afk.auto-seconds`, `afk.kick-seconds` | 300, 0 | Авто-AFK и кик за бездействие (0 — выключен) |
| `moderation.broadcast-to-all` | false | Наказания видят все, а не только персонал |
| `moderation.warn-actions` | 3 → мут 1 ч, 5 → бан 1 д, 7 → бан | Что делать при N активных предупреждениях |
| `world.sleep-percentage` | 30 | Процент спящих для пропуска ночи (-1 — не трогать) |
| `world.spawn-protection.radius` | 16 | Защита вокруг `/setspawn`, 0 — выключена |
| `world.pvp`, `world.creeper-block-damage`, `world.enderman-grief`, `world.fire-spread`, `world.phantoms` | true | Переключатели правил мира |
| `restart.daily` | `[]` | Ежедневные перезапуски, например `["05:00"]` |
| `starter-kit.items` | каменные инструменты и меч, 16 хлеба | Набор при первом входе: `предмет [кол-во]` |
| `announcer.interval-seconds` | 900 | Автообъявления из `announcements` в файле сообщений |
| `links` | `discord` | Каждый ключ становится командой (`/discord`), значение — MiniMessage |

Все тексты, шапка таба, MOTD списка серверов, правила и объявления лежат в `messages_ru.yml` (MiniMessage). Новые ключи из обновлений плагина подставляются из jar, пока их нет в файле.

## Админ-панель

Клиентский мод AnyPlugin Admin (Fabric 26.3, отдельный проект, пока не опубликован) открывает панель по F6 или `/apgui`: игроки, группы и права, варпы, настройки `config.yml`, управление сервером. Открыть её может только игрок с правом `anyplugin.gui` (набор `anyplugin.admin`, по умолчанию у операторов); у остальных команда не видна вовсе.

- Сообщения идут JSON в UTF-8 по каналу `anyplugin:admin`; входящие ограничены 32 КБ и 20 сообщениями в секунду на игрока.
- Почти каждое действие панели превращается в обычную команду плагина и выполняется от имени игрока (`performCommand`), поэтому права, журнал персонала и ответы в чат те же, что при ручном вводе. Аргументы проверяются по шаблонам (ник, группа, право, срок) до сборки команды.
- Настройки меняются отдельной операцией `config.set` только с правом `anyplugin.manage` и только для ключей, которые есть в `config.yml` из jar (кроме `moderation.templates`, `moderation.warn-actions`, `links`); тип значения сверяется с исходным. Подписи настроек берутся из `setting-labels` и `setting-sections` файла сообщений, поэтому мод не нужно обновлять ради новых ключей.

## Плейсхолдеры

Если стоит PlaceholderAPI: `%anyplugin_prefix%`, `%anyplugin_suffix%`, `%anyplugin_group%`, `%anyplugin_nick%`, `%anyplugin_afk%`, `%anyplugin_vanished%`, `%anyplugin_playtime%`, `%anyplugin_playtime_hours%`, `%anyplugin_homes%`, `%anyplugin_muted%`, `%anyplugin_online%`, `%anyplugin_unique%`. Плейсхолдеры других плагинов работают в шапке и подвале таба.

В шапке таба и MOTD доступны теги `<online>`, `<max>`, `<unique>`, `<ping>`, `<tps>`, `<mspt>`, `<world>`, `<player>`, `<name>`, `<group>`, `<time>`, `<uptime>`, `<playtime>`.

## Folia

- Все действия с игроком идут через его планировщик, с миром — через глобальный планировщик или планировщик региона, телепорт — через `teleportAsync`.
- API скорбордов на Folia не работает, поэтому таблички над головой отправляются пакетами команд напрямую клиенту, а порядок в табе задаётся `setPlayerListOrder`. Если сборка сервера не совпадает с кодом пакетов, таблички выключаются с предупреждением в логе, остальное работает.
- TPS и MSPT в `/serverinfo` и в табе на Folia показываются для региона игрока.
- `/invsee` и `/enderchest <ник>` для игрока в другом регионе открывают снимок только для чтения.
- Проверено на Folia 26.3 build 3 (2026-10-20): плагин включается, команды из консоли работают. Из консоли TPS показывается прочерком: у Folia нет общего TPS, только у региона.

## Частые вопросы

**Команды конфликтуют с другим плагином.** AnyPlugin перекрывает `msg`, `list`, `ban`, `kick`, `help`, `gamemode`. Если на сервере есть EssentialsX, TAB или LiteBans, выключите соответствующие модули AnyPlugin или уберите эти плагины.

**Стоит LuckPerms — что будет с правами?** Модуль `permissions` уступает LuckPerms: права и группы ведёт LuckPerms, а префиксы для чата и таба AnyPlugin берёт через Vault.

**Как дать игроку право только на время?** Добавьте срок в конце: `/perm user Vasya set anyplugin.fly true 2h` или `/perm user Vasya group add friend 30d`.

**Маска `*` не дала право из другого плагина.** Маски раскрываются только по зарегистрированным правам. Если плагин проверяет незарегистрированное право, выдайте его явно.

**Таблички над головой не появились.** Посмотрите лог при запуске: если сборка сервера не совпала с кодом пакетов, таблички выключаются с предупреждением, а остальное продолжает работать.

**Как вернуть текст по умолчанию?** Удалите ключ из `messages_ru.yml` и выполните `/ap reload`: недостающие ключи берутся из jar.

**Где база и как её сохранить?** Всё лежит в `plugins/AnyPlugin/data.db`. Для резервной копии остановите сервер и скопируйте файл вместе с `data.db-wal` и `data.db-shm`, если они есть.

## Сборка

Нужна JDK 25.

```powershell
git clone https://github.com/ServChan/AnyPlugin.git
cd AnyPlugin
.\gradlew.bat build
```

Jar — `build/libs/AnyPlugin-<версия>.jar`. Сборка идёт через paperweight-userdev (dev bundle Paper `26.3.build.140-beta`, без переобфускации), потому что таблички над головой используют классы сервера. SQLite-драйвер берётся из сервера (Paper, Purpur и Folia поставляют `sqlite-jdbc`).

Тестовый сервер: `.\gradlew.bat runServer` (Paper 26.3) или `.\gradlew.bat runFolia`.
