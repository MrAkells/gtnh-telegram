# GTNH Telegram Achievements

Small server-side Forge 1.7.10 mod for GT New Horizons servers.

It listens for BetterQuesting quest completions and

- sends a Telegram message when a quest is completed, and
- optionally keeps the most recent completions and the current teams in a
  JSON file that a website can read.

## Features

- one notification per completed quest, even when several teammates are online
- team (BetterQuesting party) name available in messages and in the feed
- a single summary message when many quests are completed at once
- configurable message templates
- optional Telegram forum topic targeting
- retries on Telegram rate limits and temporary failures, off the server thread
- clickable quest links for `gtnhquestsbook.top`
- repeatable quests, hidden quests and `Trigger:` helper quests are ignored

## Requirements

- GTNH `2.9.0`
- Minecraft Forge `1.7.10-10.13.4.1614`
- GTNH BetterQuesting `3.8.92-GTNH`
- Java 8 or newer
- outbound HTTPS access to `https://api.telegram.org` (for notifications)

Clients do not need the mod.

## Build

```sh
./gradlew build
```

Gradle runs on a modern JDK and downloads the Java 8 toolchain it compiles
with. The build also runs the unit tests. The mod jar is written to
`build/libs/gtnh-telegram-<version>.jar`; the `-dev` jar is for development
environments only.

The version is set once, as `modVersion` in `gradle.properties`.

## Install

Copy the jar to the server `mods/` directory and remove the previous version.
The config file `config/gtnhtelegram.cfg` is created on first start. Changes to
it take effect after a server restart.

## Config

```ini
enabled=true

sendQuestNotifications=true
sendRepeatableQuestNotifications=false
botToken=123456789:YOUR_BOT_TOKEN
chatId=-1001234567890
messageThreadId=0
questMessageFormat=[Minecraft] {player} completed quest: {quest}
questMessageFormatPlural=
questDigestFormat=[Minecraft] {player} completed {count} quests:\n{quests}
questDigestThreshold=4
questBaseUrl=https://gtnhquestsbook.top/?id=
connectTimeoutMs=5000
readTimeoutMs=5000

writeQuestFeed=false
questFeedFile=gtnhtelegram/quests.json
questFeedSize=50
```

`enabled` switches the whole mod. Telegram notifications and the quest feed
work independently of each other.

### Message templates

| Placeholder    | Value                                                    |
| -------------- | -------------------------------------------------------- |
| `{player}`     | players who were online and got the quest, comma-separated |
| `{team}`       | team name, empty for a solo player                       |
| `{quest}`      | quest name as an HTML link to the quest book             |
| `{questName}`  | quest name                                               |
| `{questId}`    | BetterQuesting quest id                                  |
| `{questUrl}`   | quest book URL                                           |
| `{chapter}`    | quest line the quest belongs to                          |
| `{playerUuid}` | UUIDs of the players, comma-separated                    |
| `{count}`      | number of quests (summary only)                          |
| `{quests}`     | list of quests (summary only)                            |

Messages are sent with Telegram `parse_mode=HTML`, so templates may contain
HTML tags. Write `\n` for a line break.

- `questMessageFormat` is used for one completed quest.
- `questMessageFormatPlural` replaces it when several teammates completed the
  quest together, for languages where the verb changes. Empty means "use
  `questMessageFormat`".
- `questDigestFormat` is used when the same players complete
  `questDigestThreshold` or more quests at the same moment, which happens after
  a pack update. `questDigestThreshold=0` always sends separate messages.

## Teams

BetterQuesting credits a quest to every member of a party, online or not, and
announces it once for every online member. The mod waits a few seconds and
reports the quest once, naming the members who were online. A quest that
reaches a player later through a team sync is not reported again.

## Quest feed

With `writeQuestFeed=true` the mod rewrites a JSON file after every completion:

```json
{
  "version": 2,
  "updated": 1794361996,
  "teams": [
    { "name": "smak", "members": ["Steve", "Alex"] }
  ],
  "quests": [
    {
      "time": 1794361996,
      "quest": "Automated Coke Oven",
      "questId": "5c004ad6-5942-4e86-ba28-a705e59ebd18",
      "questUrl": "https://gtnhquestsbook.top/?id=XABK1llCToa6KKcF5Z69GA==",
      "chapter": "Tier 0 - Stone Age",
      "team": "smak",
      "players": ["Steve"]
    }
  ]
}
```

- `quests` holds the last `questFeedSize` completions, newest first. `team` is
  empty for a solo player.
- `teams` lists the parties with at least two members. It is refreshed when the
  server starts and after every completion.
- `time` and `updated` are Unix timestamps in seconds.
- `questFeedFile` is resolved against the server directory unless it is
  absolute. The file is replaced atomically and loaded again on startup, so the
  feed survives restarts.

Point `questFeedFile` at a directory that is served over HTTP (for example
`dynmap/web/quests.json`) to make the feed available to a website.

## Telegram

Create a bot with `@BotFather` and put the token in `botToken`.

To get `chatId`, send a message to the bot or group, then inspect the result of
Telegram Bot API `getUpdates`. Group and supergroup ids are usually negative.

To send into a specific forum topic, set `messageThreadId` to that topic's
`message_thread_id` from `getUpdates`. Leave it as `0` for the main chat.

Delivery problems are written to the server log (`fml-server-latest.log` and
the console) with the logger name `gtnhtelegram`. An invalid token or chat id
is reported together with the setting to check.

## Updating GTNH

Check the BetterQuesting version bundled with the new GTNH release and update
`betterQuestingVersion` and `gtnhVersion` in `gradle.properties`.
