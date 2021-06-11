package com.github.davidmoten.http.test.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public final class Server implements AutoCloseable {

    private static final byte[] CRLF = bytes("\r\n");
    private final BlockingQueue<Response> queue;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocket ss;
    private volatile boolean keepGoing = true;
    private final CountDownLatch latch = new CountDownLatch(1);

    private Server() {
        this.queue = new LinkedBlockingQueue<Response>();
    }

    public static Server start() {
        Server server = new Server();
        server.startServer();
        return server;
    }

    public Builder response() {
        return new Builder(this, queue);
    }

    public static final class Builder {

        private final BlockingQueue<Response> queue;
        private int statusCode = 200;
        private String reason;
        private final Map<String, List<String>> headers = new HashMap<>();
        private byte[] body = new byte[0];
        private final Server server;

        Builder(Server server, BlockingQueue<Response> queue) {
            this.server = server;
            this.queue = queue;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        public Builder header(String name, String value) {
            List<String> list = headers.get(name);
            if (list == null) {
                list = new ArrayList<>();
                headers.put(name, list);
            }
            list.add(value);
            return this;
        }

        public Builder body(String body) {
            return body(bytes(body));
        }

        public Builder body(byte[] body) {
            this.body = body;
            return header("Content-Length", "" + body.length);
        }

        public Builder body(InputStream body) {
            try {
                return body(readAllAndClose(body));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public Server add() {
            if (reason == null) {
                reason = StatusCode.reason(statusCode);
            }
            Response r = new Response(statusCode, reason, headers, body);
            queue.offer(r);
            return server;
        }

        public Builder response() {
            return add().response();
        }
    }

    private Server startServer() {
        try {
            this.ss = new ServerSocket(0);
            executor.submit(() -> listen(ss));
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + ss.getLocalPort() + "/";
    }

    private void listen(ServerSocket ss) {
        try {
            try {
                ss.setSoTimeout(1000);
            } catch (SocketException e1) {
                return;
            }
            while (keepGoing) {
                try {
                    Socket socket = ss.accept();
                    InputStream in = socket.getInputStream();
                    while (keepGoing) {
                        // read request line
                        readLine(in);
                        Optional<Long> contentLength = Optional.empty();
                        {
                            List<String> headers = new ArrayList<>();
                            String line;
                            while ((line = readLine(in)).length() > 2) {
                                headers.add(line.substring(0, line.length() - 2));
                                String lower = line.toLowerCase(Locale.ENGLISH);
                                if (lower.startsWith("content-length: ")) {
                                    try {
                                        contentLength = Optional.of(Long.parseLong(
                                                lower.substring(lower.indexOf(':') + 1)));
                                    } catch (NumberFormatException e) {
                                        // do nothing
                                    }
                                }
                            }
                        }
                        if (contentLength.isPresent()) {
                            // read body
                            readAll(new LimitingInputStream(in, contentLength.get()));
                        }
                        while (keepGoing) {
                            try {
                                Response response = queue.poll(100, TimeUnit.MILLISECONDS);
                                if (response != null) {
                                    try (OutputStream out = socket.getOutputStream()) {

                                        // HTTP/1.1 200 OK
                                        // headerName: headerValue
                                        // empty line
                                        // body

                                        out.write(bytes("HTTP/1.1 " + response.statusCode() + " "
                                                + response.reason()));
                                        out.write(CRLF);
                                        for (Entry<String, List<String>> header : response.headers()
                                                .entrySet()) {
                                            String line = header.getKey() + ": " + header.getValue()
                                                    .stream().collect(Collectors.joining(","));
                                            out.write(bytes(line));
                                            out.write(CRLF);
                                        }
                                        out.write(CRLF);
                                        out.flush();
                                        if (response.body().length > 0) {
                                            out.write(response.body());
                                            out.flush();
                                        }
                                        break;
                                    }
                                }
                            } catch (InterruptedException e) {
                                // do nothing
                            }
                        }
                    }
                } catch (SocketTimeoutException | SocketException e) {
                    // that's ok go round again
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            latch.countDown();
        }
    }

//    private static void copy(InputStream in, OutputStream out) throws IOException {
//        byte[] buffer = new byte[8192];
//        int n;
//        while ((n = in.read(buffer)) != -1) {
//            out.write(buffer, 0, n);
//        }
//    }

    private static final byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    static final byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
        return out.toByteArray();
    }

    static final byte[] readAllAndClose(InputStream in) throws IOException {
        try {
            return readAll(in);
        } finally {
            in.close();
        }
    }

    static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int b;
        int previous = -1;
        while ((b = in.read()) != -1) {
            out.write(b);
            if (previous == 13 && b == 10) {
                break;
            }
            previous = b;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        this.keepGoing = false;
        try {
            this.ss.close();
        } catch (IOException e) {
            // ignore
        }
        executor.shutdown();
        try {
            latch.await(1, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            // do nothing
        }
    }
}
