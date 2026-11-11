package dev.mrakells.gtnhtelegram;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.Logger;

/**
 * Sends messages through the Telegram Bot API on its own thread, so a slow or
 * unreachable Telegram never blocks the server.
 */
final class TelegramClient {
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_QUEUED_MESSAGES = 200;
    private static final long MAX_RETRY_DELAY_MS = 30000L;

    private final String botToken;
    private final String chatId;
    private final int messageThreadId;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final Logger logger;
    // When Telegram is down for long, the oldest queued messages are dropped instead of growing without bound.
    private final ExecutorService executor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<Runnable>(MAX_QUEUED_MESSAGES),
            QuestTracker.daemonThreads("gtnhtelegram-sender"),
            new ThreadPoolExecutor.DiscardOldestPolicy()
    );

    TelegramClient(String botToken, String chatId, int messageThreadId, int connectTimeoutMs, int readTimeoutMs, Logger logger) {
        this.botToken = botToken;
        this.chatId = chatId;
        this.messageThreadId = messageThreadId;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.logger = logger;
    }

    /** Queues a message (HTML parse mode) for delivery. */
    void send(final String html) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                deliver(html);
            }
        });
    }

    /** Waits until the messages queued so far are delivered, or the timeout passes. */
    void awaitIdle(long timeoutMs) {
        Future<?> marker = executor.submit(new Runnable() {
            @Override
            public void run() {
            }
        });

        try {
            marker.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.warn("Some Telegram notifications were not delivered before shutdown");
        }
    }

    private void deliver(String html) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            long retryDelayMs;

            try {
                retryDelayMs = post(html);
            } catch (IOException e) {
                logger.warn("Telegram is unreachable (attempt " + attempt + " of " + MAX_ATTEMPTS + "): " + redact(e.toString()));
                retryDelayMs = 2000L * attempt;
            }

            if (retryDelayMs < 0 || attempt == MAX_ATTEMPTS) {
                return;
            }

            try {
                Thread.sleep(Math.min(retryDelayMs, MAX_RETRY_DELAY_MS));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * @return a negative number when the message needs no further attempts
     *         (delivered or rejected for good), otherwise the delay before the next attempt
     */
    private long post(String html) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.telegram.org/bot" + botToken + "/sendMessage").openConnection();

        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            String body = "chat_id=" + encode(chatId)
                    + "&text=" + encode(html)
                    + "&parse_mode=HTML"
                    + "&disable_web_page_preview=true"
                    + (messageThreadId > 0 ? "&message_thread_id=" + messageThreadId : "");

            OutputStream output = connection.getOutputStream();
            try {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            } finally {
                output.close();
            }

            int status = connection.getResponseCode();

            if (status < 400) {
                read(connection.getInputStream());
                return -1;
            }

            String response = read(connection.getErrorStream());

            if (status == 429) {
                long delayMs = retryAfterSeconds(response) * 1000L;
                logger.warn("Telegram rate limit reached, retrying in " + delayMs / 1000L + " s");
                return delayMs;
            }

            if (status >= 500) {
                logger.warn("Telegram API returned HTTP " + status + ": " + redact(response));
                return 2000L;
            }

            logger.error(explain(status) + " Telegram API returned HTTP " + status + ": " + redact(response));
            return -1;
        } finally {
            connection.disconnect();
        }
    }

    private static String explain(int status) {
        switch (status) {
            case 401:
            case 404:
                return "Telegram rejected the bot token. Get a valid token from @BotFather and set botToken in config/gtnhtelegram.cfg.";
            case 400:
            case 403:
                return "Telegram refused the message. Check chatId and messageThreadId in config/gtnhtelegram.cfg, and that the bot is a member of the chat.";
            default:
                return "Telegram refused the message.";
        }
    }

    private static long retryAfterSeconds(String response) {
        try {
            JsonObject parameters = new JsonParser().parse(response).getAsJsonObject().getAsJsonObject("parameters");
            return Math.max(1L, parameters.get("retry_after").getAsLong());
        } catch (RuntimeException e) {
            return 5L;
        }
    }

    /** The bot token is part of the request URL, which Java puts into some error messages. */
    private String redact(String text) {
        return text == null ? "" : text.replace(botToken, "<token>");
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static String read(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int length;

            while ((length = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, length);
            }

            return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            stream.close();
        }
    }
}
