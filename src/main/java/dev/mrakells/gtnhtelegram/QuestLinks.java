package dev.mrakells.gtnhtelegram;

import java.nio.ByteBuffer;
import java.util.Base64;
import java.util.UUID;

/** Builds links to an online quest book such as gtnhquestsbook.top. */
final class QuestLinks {
    private QuestLinks() {
    }

    /**
     * @return the base URL followed by the Base64 form of the quest id, or an empty string when links are disabled
     */
    static String questUrl(String baseUrl, UUID questId) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "";
        }

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(questId.getMostSignificantBits());
        buffer.putLong(questId.getLeastSignificantBits());

        return baseUrl.trim() + Base64.getEncoder().encodeToString(buffer.array());
    }
}
