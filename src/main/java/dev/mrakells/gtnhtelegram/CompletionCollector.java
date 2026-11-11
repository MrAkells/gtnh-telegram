package dev.mrakells.gtnhtelegram;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges the reports of one quest completion into a single {@link QuestCompletion}.
 *
 * BetterQuesting announces a completed quest separately for every online
 * member of a team, up to a few seconds apart. Reports are held for a short
 * window so that the team gets one notification and one feed entry listing
 * everyone who was online.
 */
final class CompletionCollector {
    private final long windowMs;
    private final Map<String, Pending> pending = new LinkedHashMap<String, Pending>();

    CompletionCollector(long windowMs) {
        this.windowMs = windowMs;
    }

    long windowMs() {
        return windowMs;
    }

    /**
     * @return true when this is the first report of the completion, so the caller should schedule a drain
     */
    synchronized boolean report(QuestCompletion completion, long nowMs) {
        Pending existing = pending.get(completion.key());

        if (existing != null) {
            existing.add(completion);
            return false;
        }

        pending.put(completion.key(), new Pending(completion, nowMs));
        return true;
    }

    /** Removes and returns the completions whose window has passed, oldest first. */
    synchronized List<QuestCompletion> drain(long nowMs) {
        List<QuestCompletion> ready = new ArrayList<QuestCompletion>();

        for (Iterator<Pending> iterator = pending.values().iterator(); iterator.hasNext();) {
            Pending entry = iterator.next();

            if (nowMs - entry.firstSeenMs >= windowMs) {
                ready.add(entry.merged());
                iterator.remove();
            }
        }

        return ready;
    }

    /** Removes and returns everything, for shutdown. */
    synchronized List<QuestCompletion> drainAll() {
        return drain(Long.MAX_VALUE);
    }

    private static final class Pending {
        private QuestCompletion merged;
        private final long firstSeenMs;

        Pending(QuestCompletion first, long firstSeenMs) {
            this.merged = first;
            this.firstSeenMs = firstSeenMs;
        }

        void add(QuestCompletion completion) {
            merged = merged.mergedWith(completion);
        }

        QuestCompletion merged() {
            return merged;
        }
    }
}
