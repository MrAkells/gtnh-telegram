# GTNH Telegram Achievements

Small Forge 1.7.10 mod for GT New Horizons servers.

It listens for BetterQuesting completion events and sends a Telegram message
when a player completes a quest.

## Features

- BetterQuesting `QuestEvent.COMPLETED` listener
- asynchronous Telegram Bot API delivery
- configurable message template
- optional Telegram forum topic/thread targeting
- optional repeatable quest filtering
- clickable quest links for `gtnhquestsbook.top`

## Requirements

- Minecraft Forge `1.7.10-10.13.4.1614`
- GTNH BetterQuesting
- Java 8
- outbound HTTPS access to `https://api.telegram.org`

Builds should be done with Java 8. Old ForgeGradle/Groovy tooling does not run
reliably on modern JDKs.

## Build

```sh
./gradlew build
```

The mod jar is written to:

```text
build/libs/
```

## Install

Copy the built jar to the server `mods/` directory.

The config file is created on first start:

```text
config/gtnhtelegram.cfg
```

## Config

Minimal config:

```ini
enabled=true
sendQuestNotifications=true
sendRepeatableQuestNotifications=false
botToken=123456789:YOUR_BOT_TOKEN
chatId=-1001234567890
messageThreadId=0
questMessageFormat=[Minecraft] {player} completed quest: {quest}
questBaseUrl=https://gtnhquestsbook.top/?id=
connectTimeoutMs=5000
readTimeoutMs=5000
```

Supported placeholders:

```text
{player}
{playerUuid}
{quest}
{questName}
{questId}
{questUrl}
{chapter}
```

`{quest}` is an HTML link. Messages are sent with Telegram `parse_mode=HTML`.

## Telegram

Create a bot with `@BotFather` and put the token in `botToken`.

To get `chatId`, send a message to the bot or group, then inspect the result of
Telegram Bot API `getUpdates`. Group and supergroup ids are usually negative.

To send into a specific Telegram forum topic, set `messageThreadId` to that
topic's `message_thread_id` from `getUpdates`. Leave it as `0` for the main
chat.

## Notes

- The mod is compiled against the GTNH BetterQuesting dev jar in `libs/`.
- Only BetterQuesting quest completion events are sent.
- Hidden trigger quests with names starting with `Trigger:` are ignored.
- Telegram failures are logged and do not block the server tick thread.
