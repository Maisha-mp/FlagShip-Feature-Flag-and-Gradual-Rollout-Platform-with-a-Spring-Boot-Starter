package com.flagship.starter;

import com.flagship.core.model.Environment;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Listens to the server's push stream (SSE) and runs onSignal whenever the server says
 * "something changed" (and once on every (re)connect, to catch up on anything missed).
 * If the connection fails or goes quiet, it reconnects with a growing wait. It never throws.
 */
public final class SseChangeClient {

    private static final System.Logger LOG = System.getLogger(SseChangeClient.class.getName());

    private final URI uri;
    private final HttpClient client;
    private final Duration idleTimeout;
    private final Backoff backoff;
    private final Runnable onSignal;
    private final ScheduledExecutorService watchdog;

    private volatile boolean running;
    private volatile boolean connected;
    private volatile long lastActivityNanos;
    private volatile InputStream currentStream;
    private volatile Thread thread;
    private boolean connectionWorked;   // only touched by the loop thread

    /**
     * @param idleTimeout reconnect if nothing (not even a ping) arrives for this long
     * @param onSignal    what to do when the server reports a change (the starter passes store::refresh)
     */
    public SseChangeClient(String serverUrl, String project, Environment environment,
                           Duration connectTimeout, Duration idleTimeout,
                           Backoff backoff, Runnable onSignal) {
        if (project == null || project.isBlank()) {
            throw new IllegalArgumentException("flagship.project must be set");
        }
        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("idle timeout must be greater than zero");
        }
        String base = serverUrl.endsWith("/")
                ? serverUrl.substring(0, serverUrl.length() - 1)
                : serverUrl;
        this.uri = URI.create(base + "/api/projects/"
                + URLEncoder.encode(project, StandardCharsets.UTF_8)
                + "/stream?env=" + environment.name());
        this.client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        this.idleTimeout = idleTimeout;
        this.backoff = backoff;
        this.onSignal = onSignal;
        this.watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "flagship-sse-watchdog");
            t.setDaemon(true);
            return t;
        });
    }

    public synchronized void start() {
        if (running || thread != null) {
            return;
        }
        running = true;
        Thread t = new Thread(this::runLoop, "flagship-sse-client");
        t.setDaemon(true);   // never keeps the application alive on its own
        thread = t;
        t.start();
    }

    public void close() {
        running = false;
        closeQuietly(currentStream);
        Thread t = thread;
        if (t != null) {
            t.interrupt();   // wakes it from a wait between attempts
        }
        watchdog.shutdownNow();
    }

    /** True while a stream is open and the server has said hello. */
    public boolean isConnected() {
        return connected;
    }

    // ---------- the loop ----------

    private void runLoop() {
        int failures = 0;
        while (running) {
            connectionWorked = false;
            try {
                connectAndRead();
            } catch (IOException | RuntimeException e) {
                if (running) {
                    LOG.log(System.Logger.Level.WARNING,
                            "FlagShip: push connection lost ({0}); will reconnect", describe(e));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!running) {
                return;
            }

            failures = connectionWorked ? 0 : failures + 1;
            long wait = backoff.delay(failures).toMillis();
            wait = Math.max(1, (long) (wait * (0.8 + 0.4 * ThreadLocalRandom.current().nextDouble())));   // a little jitter
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void connectAndRead() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))   // how long to wait for the server's answer to START
                .header("Accept", "text/event-stream")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        InputStream in = response.body();
        if (response.statusCode() != 200) {
            closeQuietly(in);
            throw new IOException("Server answered " + response.statusCode() + " for the push stream");
        }

        currentStream = in;
        lastActivityNanos = System.nanoTime();
        long period = Math.max(25, idleTimeout.toMillis() / 4);
        ScheduledFuture<?> guard = watchdog.scheduleWithFixedDelay(() -> {
            if (System.nanoTime() - lastActivityNanos > idleTimeout.toNanos()) {
                LOG.log(System.Logger.Level.WARNING,
                        "FlagShip: nothing received on the push stream for {0} ms; reconnecting",
                        idleTimeout.toMillis());
                lastActivityNanos = System.nanoTime();   // warn once per silence
                closeQuietly(in);                         // unblocks the read below
            }
        }, period, period, TimeUnit.MILLISECONDS);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            SseLineParser parser = new SseLineParser();
            String line;
            while (running && (line = reader.readLine()) != null) {
                lastActivityNanos = System.nanoTime();
                Optional<SseLineParser.Event> event = parser.feed(line);
                if (event.isEmpty()) {
                    continue;
                }
                String name = event.get().name();
                if (name.equals("hello")) {
                    connectionWorked = true;
                    connected = true;
                    signal();   // refresh now, in case a change happened while we were disconnected
                } else if (name.equals("change")) {
                    signal();
                }
            }
        } finally {
            connected = false;
            currentStream = null;
            guard.cancel(false);
        }
    }

    private void signal() {
        try {
            onSignal.run();
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "FlagShip: refresh after a push event failed: {0}", e.getMessage());
        }
    }

    private static String describe(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException e) {
                // nothing useful to do
            }
        }
    }
}