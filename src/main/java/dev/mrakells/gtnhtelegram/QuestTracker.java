package dev.mrakells.gtnhtelegram;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;

/**
 * Receives quest completions from the server thread and publishes them to the
 * quest feed and to Telegram from a worker thread.
 */
final class QuestTracker {
    private final CompletionCollector collector;
    private final QuestFeed feed;
    private final TelegramNotifier notifier;
    private final TelegramClient telegram;
    private final Logger logger;
    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(daemonThreads("gtnhtelegram-worker"));

    /**
     * @param feed     null when the quest feed is turned off
     * @param notifier null when Telegram notifications are turned off, together with {@code telegram}
     */
    QuestTracker(CompletionCollector collector, QuestFeed feed, TelegramNotifier notifier, TelegramClient telegram, Logger logger) {
        this.collector = collector;
        this.feed = feed;
        this.notifier = notifier;
        this.telegram = telegram;
        this.logger = logger;
    }

    /** Called on the server thread for every online player credited with a quest. */
    void report(QuestCompletion completion) {
        if (collector.report(completion, System.currentTimeMillis())) {
            worker.schedule(new Runnable() {
                @Override
                public void run() {
                    publish(collector.drain(System.currentTimeMillis()));
                }
            }, collector.windowMs() + 100L, TimeUnit.MILLISECONDS);
        }
    }

    void updateTeams(final List<Team> teams) {
        if (feed == null) {
            return;
        }

        worker.execute(new Runnable() {
            @Override
            public void run() {
                feed.setTeams(teams);
            }
        });
    }

    /** Publishes whatever is still being collected and waits briefly for the deliveries. Used when the server stops. */
    void flush() {
        Future<?> pending = worker.submit(new Runnable() {
            @Override
            public void run() {
                publish(collector.drainAll());
            }
        });

        try {
            pending.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warn("Quest completions were not fully published before shutdown", e);
        }

        if (telegram != null) {
            telegram.awaitIdle(5000L);
        }
    }

    private void publish(List<QuestCompletion> completions) {
        if (completions.isEmpty()) {
            return;
        }

        // A failure in one channel must not stop the other, nor kill the worker thread.
        if (feed != null) {
            try {
                feed.add(completions);
            } catch (RuntimeException e) {
                logger.error("Could not update the quest feed", e);
            }
        }

        if (notifier != null) {
            try {
                notifier.publish(completions);
            } catch (RuntimeException e) {
                logger.error("Could not queue Telegram notifications", e);
            }
        }
    }

    static ThreadFactory daemonThreads(final String name) {
        return new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, name);
                thread.setDaemon(true);
                return thread;
            }
        };
    }
}
