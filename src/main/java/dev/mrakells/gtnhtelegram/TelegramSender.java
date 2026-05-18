package dev.mrakells.gtnhtelegram;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class TelegramSender {
    private static final Charset UTF_8 = StandardCharsets.UTF_8;
    private static final AtomicBoolean MISSING_CONFIG_WARNING_SHOWN = new AtomicBoolean(false);
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "gtnhtelegram-sender");
            thread.setDaemon(true);
            return thread;
        }
    });

    private static volatile String botToken = "";
    private static volatile String chatId = "";
    private static volatile int messageThreadId = 0;
    private static volatile int connectTimeoutMs = 5000;
    private static volatile int readTimeoutMs = 5000;

    private TelegramSender() {
    }

    public static void configure(String token, String targetChatId, int targetMessageThreadId, int connectTimeout, int readTimeout) {
        botToken = token == null ? "" : token.trim();
        chatId = targetChatId == null ? "" : targetChatId.trim();
        messageThreadId = targetMessageThreadId;
        connectTimeoutMs = connectTimeout;
        readTimeoutMs = readTimeout;
        MISSING_CONFIG_WARNING_SHOWN.set(false);
    }

    public static void send(final String text) {
        if (isBlank(botToken) || isBlank(chatId)) {
            if (MISSING_CONFIG_WARNING_SHOWN.compareAndSet(false, true)) {
                GTNHTelegramMod.LOGGER.warn("Telegram notifications skipped because botToken or chatId is not configured");
            }
            return;
        }

        EXECUTOR.submit(new Runnable() {
            @Override
            public void run() {
                postMessage(text);
            }
        });
    }

    private static void postMessage(String text) {
        HttpURLConnection connection = null;

        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");

            String body = "chat_id=" + encode(chatId)
                    + "&text=" + encode(text)
                    + "&parse_mode=" + encode("HTML")
                    + "&disable_web_page_preview=true";
            if (messageThreadId > 0) {
                body += "&message_thread_id=" + messageThreadId;
            }
            byte[] payload = body.getBytes(UTF_8);

            OutputStream outputStream = connection.getOutputStream();
            try {
                outputStream.write(payload);
                outputStream.flush();
            } finally {
                outputStream.close();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode >= 400) {
                GTNHTelegramMod.LOGGER.error(
                        "Telegram API returned HTTP {}: {}",
                        Integer.valueOf(responseCode),
                        readResponse(connection.getErrorStream())
                );
            } else {
                readResponse(connection.getInputStream());
            }
        } catch (Exception e) {
            GTNHTelegramMod.LOGGER.error("Failed to send Telegram notification", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String encode(String value) throws IOException {
        return URLEncoder.encode(value, UTF_8.name());
    }

    private static String readResponse(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8));
        StringBuilder builder = new StringBuilder();

        try {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } finally {
            reader.close();
        }

        return builder.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
