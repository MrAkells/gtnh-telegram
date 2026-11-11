package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns quest completions into Telegram messages (HTML parse mode). */
final class QuestMessages {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\w+)\\}");

    private QuestMessages() {
    }

    /**
     * One message for one completed quest.
     *
     * @param pluralTemplate used instead of {@code template} when several players completed the quest; may be empty
     */
    static String single(String template, String pluralTemplate, QuestCompletion completion) {
        boolean plural = completion.playerUuids().size() > 1 && pluralTemplate != null && !pluralTemplate.trim().isEmpty();

        return fill(plural ? pluralTemplate : template, placeholders(completion));
    }

    /**
     * One summary for many quests completed at once by the same players, split
     * into several messages when it does not fit into one.
     *
     * @param template header with a {@code {quests}} placeholder where the list goes
     */
    static List<String> digest(String template, List<QuestCompletion> completions, int maxLength) {
        Map<String, String> values = placeholders(completions.get(0));
        values.put("count", String.valueOf(completions.size()));
        // Quest placeholders make no sense in a header that covers several quests.
        for (String name : new String[] {"quest", "questName", "questId", "questUrl", "chapter"}) {
            values.put(name, "");
        }

        int split = template.indexOf("{quests}");
        String header = fill(split < 0 ? template + "\n" : template.substring(0, split), values);
        String footer = split < 0 ? "" : fill(template.substring(split + "{quests}".length()), values);

        List<String> messages = new ArrayList<String>();
        StringBuilder current = new StringBuilder(header);
        boolean firstLine = true;

        for (QuestCompletion completion : completions) {
            String line = "\u2022 " + questLink(completion)
                    + (completion.chapter().isEmpty() ? "" : " [" + escapeHtml(completion.chapter()) + "]");

            if (!firstLine && current.length() + 1 + line.length() + footer.length() > maxLength) {
                messages.add(current.toString().trim());
                current = new StringBuilder();
                firstLine = true;
            }

            if (!firstLine) {
                current.append('\n');
            }
            current.append(line);
            firstLine = false;
        }

        current.append(footer);
        messages.add(current.toString().trim());

        return messages;
    }

    static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String questLink(QuestCompletion completion) {
        String name = escapeHtml(completion.quest());

        return completion.questUrl().isEmpty()
                ? name
                : "<a href=\"" + escapeHtml(completion.questUrl()) + "\">" + name + "</a>";
    }

    private static Map<String, String> placeholders(QuestCompletion completion) {
        Map<String, String> values = new HashMap<String, String>();
        values.put("player", escapeHtml(join(completion.playerNames())));
        values.put("playerUuid", escapeHtml(join(completion.playerUuids())));
        values.put("team", escapeHtml(completion.teamName()));
        values.put("quest", questLink(completion));
        values.put("questName", escapeHtml(completion.quest()));
        values.put("questId", escapeHtml(completion.questId()));
        values.put("questUrl", escapeHtml(completion.questUrl()));
        values.put("chapter", escapeHtml(completion.chapter()));
        return values;
    }

    /** Replaces known placeholders in one pass, so a value can never be mistaken for a placeholder. */
    private static String fill(String template, Map<String, String> values) {
        Matcher matcher = PLACEHOLDER.matcher(template.replace("\\n", "\n"));
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String join(List<String> values) {
        StringBuilder joined = new StringBuilder();

        for (String value : values) {
            if (joined.length() > 0) {
                joined.append(", ");
            }
            joined.append(value);
        }

        return joined.toString();
    }
}
