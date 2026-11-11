package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decides which Telegram messages a batch of quest completions turns into. */
final class TelegramNotifier {
    /** Telegram accepts up to 4096 characters per message; the margin leaves room for HTML entities. */
    private static final int MAX_MESSAGE_LENGTH = 3800;

    private final TelegramClient client;
    private final String messageFormat;
    private final String pluralMessageFormat;
    private final String digestFormat;
    private final int digestThreshold;

    /**
     * @param digestThreshold from how many quests completed at once by the same players
     *                        a single summary is sent instead of separate messages; 0 turns summaries off
     */
    TelegramNotifier(TelegramClient client, String messageFormat, String pluralMessageFormat, String digestFormat, int digestThreshold) {
        this.client = client;
        this.messageFormat = messageFormat;
        this.pluralMessageFormat = pluralMessageFormat;
        this.digestFormat = digestFormat;
        this.digestThreshold = digestThreshold;
    }

    void publish(List<QuestCompletion> completions) {
        for (String message : messages(completions)) {
            client.send(message);
        }
    }

    List<String> messages(List<QuestCompletion> completions) {
        // A pack update or a team sync can complete dozens of quests in one tick.
        Map<String, List<QuestCompletion>> byPlayers = new LinkedHashMap<String, List<QuestCompletion>>();

        for (QuestCompletion completion : completions) {
            String group = completion.playerUuids() + "|" + completion.teamId();

            if (!byPlayers.containsKey(group)) {
                byPlayers.put(group, new ArrayList<QuestCompletion>());
            }
            byPlayers.get(group).add(completion);
        }

        List<String> messages = new ArrayList<String>();

        for (List<QuestCompletion> group : byPlayers.values()) {
            if (digestThreshold > 0 && group.size() >= digestThreshold) {
                messages.addAll(QuestMessages.digest(digestFormat, group, MAX_MESSAGE_LENGTH));
                continue;
            }

            for (QuestCompletion completion : group) {
                messages.add(QuestMessages.single(messageFormat, pluralMessageFormat, completion));
            }
        }

        return messages;
    }
}
