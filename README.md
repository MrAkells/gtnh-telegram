# GTNH Telegram Achievements

Forge-мод для Minecraft 1.7.10 / GTNH, который отправляет в Telegram сообщение, когда игрок завершает квест BetterQuesting.

## Что делает

- ловит завершение квестов BetterQuesting в GTNH через `QuestEvent.COMPLETED`;
- формирует текст сообщения по шаблону;
- отправляет уведомление в Telegram Bot API;
- читает `bot token` и `chat id` из конфига.

## Требования

- `Java 8`
- `Gradle 4.4.1+`
- доступ сервера Minecraft к `https://api.telegram.org`

Важно:

- Forge 1.7.10 обычно не собирается на современных JDK вроде `Java 25`. Для сборки и запуска dev-окружения используй именно `Java 8`.
- В проекте используется форк `ForgeGradle 1.2` от `anatawa12`, потому что он заметно практичнее для сборки старых 1.7.10-модов на современной инфраструктуре.

## Сборка

Используй wrapper, который уже лежит в проекте:

```bash
./gradlew build
```

Готовый jar появится в `build/libs/`.

## Настройка

После первого запуска мода будет создан файл:

```text
config/gtnhtelegram.cfg
```

Заполни в нем:

```ini
enabled=true
sendQuestNotifications=true
sendRepeatableQuestNotifications=false
botToken=123456789:YOUR_BOT_TOKEN
chatId=-1001234567890
questMessageFormat=[Minecraft] {player} завершил квест: {quest}
questBaseUrl=https://gtnhquestsbook.top/?id=
```

## Как получить `botToken`

1. Открой Telegram и найди `@BotFather`.
2. Выполни `/newbot`.
3. Сохрани выданный токен.

## Как получить `chatId`

- Для личного чата удобно написать боту и получить `chat_id` через Bot API `getUpdates`.
- Для группы добавь туда бота, отправь сообщение в группу и посмотри `chat.id` в ответе `getUpdates`.
- У групп и супергрупп `chat_id` обычно отрицательный.

## Проверка

1. Собери мод.
2. Положи jar в `mods/`.
3. Запусти сервер или клиент с интегрированным сервером.
4. Заверши любой квест BetterQuesting.
5. Проверь, пришло ли сообщение в Telegram.

## Ограничения

- BetterQuesting-поддержка рассчитана на GTNH-форк BetterQuesting и собирается против `BetterQuesting-3.7.15-GTNH-dev.jar`.
- Мод отправляет только события завершения квестов BetterQuesting.
- `{quest}` в Telegram теперь подставляется как кликабельная HTML-ссылка на онлайн-квестбук, а сам `id` кодируется из BetterQuesting UUID в тот же base64-формат, который использует сайт.
