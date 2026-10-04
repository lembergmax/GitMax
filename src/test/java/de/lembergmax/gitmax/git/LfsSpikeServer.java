package de.lembergmax.gitmax.git;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Winziger Git-LFS-Server für den Spike: Batch-Schnittstelle plus Objektspeicher, verlangt Basic-Anmeldung für die
 * Batch-Anfrage. Nur auf {@link ServerSocket} gebaut, wie {@code FakeServer}. Jede Anfrage wird mitgeschrieben.
 */
final class LfsSpikeServer implements AutoCloseable {

    /** Eine eingegangene Anfrage. */
    record Request(
            String method,
            String path,
            Map<String, String> headers,
            byte[] body
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
    private final String user;
    private final String password;
    private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
        final Thread thread = new Thread(runnable, "lfs-spike-server");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, byte[]> objects = Collections.synchronizedMap(new HashMap<>());
    private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean running = true;

    LfsSpikeServer(
            final String user,
            final String password
    ) throws IOException {
        this.user = user;
        this.password = password;
        socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        executor.execute(this::acceptLoop);
    }

    /** Wurzel der LFS-Schnittstelle, so wie sie in {@code lfs.url} steht. */
    String lfsUrl() {
        return "http://127.0.0.1:" + socket.getLocalPort() + "/max/alpha.git/info/lfs";
    }

    /** Legt ein Objekt ab und liefert dessen SHA-256 (die LFS-Kennung). */
    String put(
            final byte[] content
    ) {
        final String oid = sha256(content);
        objects.put(oid, content);
        return oid;
    }

    boolean has(
            final String oid
    ) {
        return objects.containsKey(oid);
    }

    List<Request> requests() {
        synchronized (requests) {
            return new ArrayList<>(requests);
        }
    }

    static String sha256(
            final byte[] content
    ) {
        try {
            final byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            final StringBuilder hex = new StringBuilder();
            for (final byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (final java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
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
            final InputStream in = connection.getInputStream();
            final String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            final String[] parts = requestLine.split(" ");
            final Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                final int colon = line.indexOf(':');
                if (colon > 0) {
                    headers.put(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
                }
            }
            final OutputStream out = connection.getOutputStream();
            final Request request = new Request(parts[0], parts[1], headers, readBody(in, headers, out));
            requests.add(request);
            respond(out, request);
        } catch (final IOException failure) {
            // Der Client hat die Verbindung vorzeitig geschlossen; für den Test ohne Bedeutung.
        }
    }

    private void respond(
            final OutputStream out,
            final Request request
    ) throws IOException {
        final String path = request.path();
        if (path.endsWith("/info/lfs/objects/batch")) {
            if (!authorized(request)) {
                write(out, 401, "application/vnd.git-lfs+json",
                        "{\"message\":\"Credentials needed\"}".getBytes(StandardCharsets.UTF_8),
                        Map.of("LFS-Authenticate", "Basic realm=\"spike\""));
                return;
            }
            write(out, 200, "application/vnd.git-lfs+json", batchResponse(request), Map.of());
            return;
        }
        final int marker = path.indexOf("/objects/storage/");
        if (marker >= 0) {
            final String oid = path.substring(marker + "/objects/storage/".length());
            if ("PUT".equals(request.method())) {
                objects.put(oid, request.body());
                write(out, 200, "text/plain", new byte[0], Map.of());
                return;
            }
            final byte[] content = objects.get(oid);
            if (content == null) {
                write(out, 404, "text/plain", new byte[0], Map.of());
                return;
            }
            write(out, 200, "application/octet-stream", content, Map.of());
            return;
        }
        write(out, 404, "text/plain", new byte[0], Map.of());
    }

    private boolean authorized(
            final Request request
    ) {
        final String expected = "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        return expected.equals(request.header("Authorization"));
    }

    /** Antwortet auf die Batch-Anfrage: für jedes Objekt eine Lade- bzw. Hochlade-Adresse ohne eigene Anmeldung. */
    private byte[] batchResponse(
            final Request request
    ) {
        final String body = new String(request.body(), StandardCharsets.UTF_8);
        final boolean upload = body.contains("\"upload\"");
        final List<String[]> wanted = new ArrayList<>();
        final java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"oid\"\\s*:\\s*\"([0-9a-f]{64})\"\\s*,\\s*\"size\"\\s*:\\s*(\\d+)").matcher(body);
        while (matcher.find()) {
            wanted.add(new String[]{matcher.group(1), matcher.group(2)});
        }
        final StringBuilder json = new StringBuilder("{\"transfer\":\"basic\",\"objects\":[");
        for (int index = 0; index < wanted.size(); index += 1) {
            final String oid = wanted.get(index)[0];
            final String size = wanted.get(index)[1];
            final String href = lfsUrl() + "/objects/storage/" + oid;
            json.append(index == 0 ? "" : ",").append("{\"oid\":\"").append(oid).append("\",\"size\":").append(size);
            if (upload && objects.containsKey(oid)) {
                // Das Objekt ist schon da: keine Aktion nötig.
                json.append("}");
            } else if (upload) {
                json.append(",\"actions\":{\"upload\":{\"href\":\"").append(href).append("\"}}}");
            } else if (objects.containsKey(oid)) {
                json.append(",\"actions\":{\"download\":{\"href\":\"").append(href).append("\"}}}");
            } else {
                json.append(",\"error\":{\"code\":404,\"message\":\"Object does not exist\"}}");
            }
        }
        return json.append("]}").toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void write(
            final OutputStream out,
            final int status,
            final String contentType,
            final byte[] body,
            final Map<String, String> extraHeaders
    ) throws IOException {
        final StringBuilder head = new StringBuilder("HTTP/1.1 ").append(status).append(" X\r\n");
        head.append("Content-Type: ").append(contentType).append("\r\n");
        head.append("Content-Length: ").append(body.length).append("\r\n");
        for (final Map.Entry<String, String> entry : extraHeaders.entrySet()) {
            head.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
        out.flush();
    }

    private static String readLine(
            final InputStream in
    ) throws IOException {
        final ByteArrayOutputStream line = new ByteArrayOutputStream();
        int value;
        while ((value = in.read()) >= 0) {
            if (value == '\n') {
                final byte[] bytes = line.toByteArray();
                final int length = bytes.length > 0 && bytes[bytes.length - 1] == '\r' ? bytes.length - 1 : bytes.length;
                return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
            }
            line.write(value);
        }
        return line.size() == 0 ? null : line.toString(StandardCharsets.ISO_8859_1);
    }

    private static byte[] readBody(
            final InputStream in,
            final Map<String, String> headers,
            final OutputStream out
    ) throws IOException {
        int length = 0;
        boolean chunked = false;
        for (final Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("Content-Length")) {
                length = Integer.parseInt(entry.getValue());
            }
            if (entry.getKey().equalsIgnoreCase("Transfer-Encoding") && entry.getValue().equalsIgnoreCase("chunked")) {
                chunked = true;
            }
            if (entry.getKey().equalsIgnoreCase("Expect") && entry.getValue().equalsIgnoreCase("100-continue")) {
                out.write("HTTP/1.1 100 Continue\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
                out.flush();
            }
        }
        if (chunked) {
            final ByteArrayOutputStream collected = new ByteArrayOutputStream();
            while (true) {
                final String sizeLine = readLine(in);
                final int size = Integer.parseInt(sizeLine.split(";")[0].trim(), 16);
                if (size == 0) {
                    readLine(in);
                    return collected.toByteArray();
                }
                final byte[] chunk = in.readNBytes(size);
                collected.write(chunk);
                readLine(in);
            }
        }
        final byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            final int count = in.read(body, read, length - read);
            if (count < 0) {
                break;
            }
            read += count;
        }
        return body;
    }
}
