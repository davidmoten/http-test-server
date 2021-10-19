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
    private static final int SOCKET_ACCEPT_WAIT_MS = 1000;

    private final BlockingQueue<Response> queue;
    private final ExecutorService listenerExecutor = Executors.newSingleThreadExecutor();
    private ServerSocket ss;
    private volatile boolean keepGoing = true;
    private final CountDownLatch closeLatch = new CountDownLatch(1);
    private final long serverThreadStartAwaitMs;
    private final long serverThreadPauseForAcceptMs;

    private Server(long serverThreadStartAwaitMs, long serverThreadPauseForAcceptMs) {
        this.serverThreadStartAwaitMs = serverThreadStartAwaitMs;
        this.serverThreadPauseForAcceptMs = serverThreadPauseForAcceptMs;
        this.queue = new LinkedBlockingQueue<Response>();
    }

    public static Server start() {
        return builder().start();
    }

    public static ServerBuilder builder() {
        return new ServerBuilder();
    }

    public static final class ServerBuilder {

        long serverThreadStartAwaitMs = 10000;
        long serverThreadPauseForAcceptMs = 100;

        /**
         * Sets time in ms to wait for server thread to start (pre-accept). Default
         * value is 10000.
         * 
         * @param valueMs time in ms to wait for server thread to start (pre-accept).
         * @return this
         */
        public ServerBuilder serverThreadStartAwaitMs(long valueMs) {
            this.serverThreadStartAwaitMs = valueMs;
            return this;
        }

        /**
         * Sets time in ms to pause after the the server thread has started to ensure
         * that accept method has been called. Default value is 100ms.
         * 
         * @param valueMs time in ms to pause after the the server thread has started to
         *                ensure that accept method has been called
         * @return this
         */
        public ServerBuilder serverThreadPauseForAcceptMs(long valueMs) {
            this.serverThreadPauseForAcceptMs = valueMs;
            return this;
        }

        @SuppressWarnings("resource")
        public Server start() {
            return new Server(serverThreadStartAwaitMs, serverThreadPauseForAcceptMs).startServer();
        }

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
                return body(Util.readAllAndClose(body));
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
            try {
                ss.setSoTimeout(SOCKET_ACCEPT_WAIT_MS);
            } catch (SocketException e) {
                throw new UncheckedIOException(e);
            }
            // do what we can to ensure that server has started
            CountDownLatch latch = new CountDownLatch(1);
            listenerExecutor.submit(() -> listen(ss, latch));
            latch.await(serverThreadStartAwaitMs, TimeUnit.MILLISECONDS);
            // thread has started, give a small amount of time
            // for accept method to be reached
            Thread.sleep(serverThreadPauseForAcceptMs);
            return this;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + ss.getLocalPort() + "/";
    }

    private void listen(ServerSocket ss, CountDownLatch latch) {
        try {
            latch.countDown();
            while (keepGoing) {
                Socket socket = null;
                try {
                    socket = ss.accept();
                    Socket sck = socket;
                    handleSocket(sck);
                } catch (SocketTimeoutException | SocketException e) {
                    // that's ok go round again
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            closeLatch.countDown();
        }
    }

    private void handleSocket(Socket socket) {
        try {
            InputStream in = socket.getInputStream();
            // read request line
            readLine(in);
            Optional<Long> contentLength = readHeaders(in);
            if (contentLength.isPresent()) {
                // read body
                Util.readAll(new LimitingInputStream(in, contentLength.get()));
            }

            // don't close the output stream after this interaction
            // the client behaves better if you close the socket instead
            OutputStream out = socket.getOutputStream();
            while (keepGoing) {
                try {
                    Response response = queue.poll(100, TimeUnit.MILLISECONDS);
                    if (response != null) {
                        writeResponse(out, response);
                        break;
                    }
                    // if no response ready then loop again
                } catch (InterruptedException e) {
                    // do nothing
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // note we rely on the client to close the socket. If we close it now then
        // the client may not have finished reading the output sent to it yet and fail.
    }

    private static Optional<Long> readHeaders(InputStream in) throws IOException {
        List<String> headers = new ArrayList<>();
        String line;
        while ((line = readLine(in)).length() > 2) {
            headers.add(line.substring(0, line.length() - 2));
            String lower = line.toLowerCase(Locale.ENGLISH);
            if (lower.startsWith("content-length: ")) {
                try {
                    return Optional.of(Long.parseLong(lower.substring(lower.indexOf(':') + 1)));
                } catch (NumberFormatException e) {
                    // do nothing
                }
            }
        }
        return Optional.empty();
    }

    private static void writeResponse(OutputStream out, Response response)
            throws IOException, InterruptedException {
        // Example
        // HTTP/1.1 200 OK<CRLF>
        // headerName: headerValue<CRLF>
        // <CRLF>
        // body bytes

        out.write(bytes("HTTP/1.1 " + response.statusCode() + " " + response.reason()));
        out.write(CRLF);
        for (Entry<String, List<String>> header : response.headers().entrySet()) {
            String line = header.getKey() + ": "
                    + header.getValue().stream().collect(Collectors.joining(","));
            out.write(bytes(line));
            out.write(CRLF);
        }
        out.write(CRLF);
        if (response.body().length > 0) {
            out.write(response.body());
        }
        // hint to push to client
        out.flush();
    }

    private static final byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
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
        try {
            listenerExecutor.shutdownNow();
            listenerExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
        try {
            closeLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // do nothing
        }
    }
}
