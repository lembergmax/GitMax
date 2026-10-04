package de.lembergmax.gitmax.provider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Winziger HTTP-Server für Tests der Anbieter-Clients, nur auf {@link ServerSocket} gebaut: Der
 * Unit-Test-Compiler sieht {@code com.sun.net.httpserver} nicht. Antworten werden je Pfad
 * registriert; jede Anfrage wird mitgeschrieben. Jede Verbindung wird nach einer Antwort geschlossen.
 */
final class FakeServer implements AutoCloseable {

    /** Eine vorbereitete Antwort. */
    record Reply(
            int status,
            String body,
            Map<String, String> headers
    ) {

        static Reply ok(
                final String body
        ) {
            return new Reply(200, body, Map.of());
        }

        static Reply ok(
                final String body,
                final Map<String, String> headers
        ) {
            return new Reply(200, body, headers);
        }

        static Reply status(
                final int status,
                final String body
        ) {
            return new Reply(status, body, Map.of());
        }

        static Reply status(
                final int status,
                final String body,
                final Map<String, String> headers
        ) {
            return new Reply(status, body, headers);
        }
    }

    /** Eine eingegangene Anfrage. */
    record Recorded(
            String method,
            String pathAndQuery,
            Map<String, String> headers,
            String body
    ) {

        String header(
                final String name
        ) {
            for (final Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(name)) {
                    return entry.getValue();
                }
            }
            return null;
        }
    }

    private final ServerSocket socket;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "fake-server");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, Reply> replies = Collections.synchronizedMap(new HashMap<>());
    private final List<Recorded> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = true;

    FakeServer() throws IOException {
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        executor.execute(this::acceptLoop);
    }

    /** Basis-URL, z. B. {@code http://127.0.0.1:54321}. */
    String url() {
        return "http://127.0.0.1:" + socket.getLocalPort();
    }

    /** Registriert die Antwort für einen Pfad; mit {@code ?query} gilt sie nur für genau diese Anfrage. */
    FakeServer on(
            final String pathAndOptionalQuery,
            final Reply reply
    ) {
        replies.put(pathAndOptionalQuery, reply);
        return this;
    }

    List<Recorded> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            socket.close();
        } catch (final IOException alreadyClosed) {
            // beim Aufräumen eines Tests nicht relevant
        }
        executor.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                final Socket connection = socket.accept();
                executor.execute(() -> serve(connection));
            } catch (final IOException closed) {
                return;
            }
        }
    }

    private void serve(
            final Socket connection
    ) {
        try (connection) {
            final BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.ISO_8859_1));
            final String requestLine = reader.readLine();
            if (requestLine == null) {
                return;
            }
            final String[] parts = requestLine.split(" ");
            final Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                final int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
            final String pathAndQuery = parts.length > 1 ? parts[1] : "/";
            requests.add(new Recorded(parts[0], pathAndQuery, headers, readBody(reader, headers)));
            write(connection.getOutputStream(), replyFor(pathAndQuery));
        } catch (final IOException failure) {
            // Der Client hat die Verbindung vorzeitig geschlossen; für den Test ohne Bedeutung.
        }
    }

    /** Liest den Rumpf einer Anfrage nach {@code Content-Length}; Zeichen entsprechen hier Bytes (ISO-8859-1). */
    private static String readBody(
            final BufferedReader reader,
            final Map<String, String> headers
    ) throws IOException {
        int length = 0;
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Length")) {
                length = Integer.parseInt(entry.getValue());
            }
        }
        final char[] buffer = new char[length];
        int read = 0;
        while (read < length) {
            final int count = reader.read(buffer, read, length - read);
            if (count < 0) {
                break;
            }
            read += count;
        }
        return new String(new String(buffer, 0, read).getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
    }

    private Reply replyFor(
            final String pathAndQuery
    ) {
        final Reply exact = replies.get(pathAndQuery);
        if (exact != null) {
            return exact;
        }
        final int question = pathAndQuery.indexOf('?');
        final String path = question < 0 ? pathAndQuery : pathAndQuery.substring(0, question);
        return replies.getOrDefault(path, Reply.status(404, "{\"message\":\"Not Found\"}"));
    }

    private static void write(
            final OutputStream out,
            final Reply reply
    ) throws IOException {
        final byte[] body = reply.body().getBytes(StandardCharsets.UTF_8);
        final StringBuilder head = new StringBuilder();
        head.append("HTTP/1.1 ").append(reply.status()).append(' ').append(reason(reply.status())).append("\r\n");
        reply.headers().forEach((name, value) -> head.append(name).append(": ").append(value).append("\r\n"));
        head.append("Content-Type: application/json\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String reason(
            final int status
    ) {
        switch (status) {
            case 200:
                return "OK";
            case 201:
                return "Created";
            case 400:
                return "Bad Request";
            case 422:
                return "Unprocessable Entity";
            case 401:
                return "Unauthorized";
            case 403:
                return "Forbidden";
            case 404:
                return "Not Found";
            case 429:
                return "Too Many Requests";
            case 502:
                return "Bad Gateway";
            default:
                return "Status";
        }
    }
}
