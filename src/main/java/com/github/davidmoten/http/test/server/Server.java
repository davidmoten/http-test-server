package com.github.davidmoten.http.test.server;

import java.io.ByteArrayInputStream;
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
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class Server implements AutoCloseable {

    private static final byte[] CRLF = bytes("\r\n");
    private final BlockingQueue<Response> queue;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ServerSocket ss;
    private volatile boolean keepGoing = true;

    public Server() {
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
        private InputStream body = new ByteArrayInputStream(new byte[0]);
        private final Server server;

        Builder(Server server, BlockingQueue<Response> queue) {
            this.server = server;
            this.queue = queue;
        }

        Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        Builder reason(String reason) {
            this.reason = reason;
            return this;
        }

        Builder header(String name, String value) {
            List<String> list = headers.get(name);
            if (list == null) {
                list = new ArrayList<>();
                headers.put(name, list);
            }
            list.add(value);
            return this;
        }

        Builder body(String body) {
            return body(bytes(body));
        }

        Builder body(byte[] body) {
            return body(new ByteArrayInputStream(body));
        }

        Builder body(InputStream body) {
            this.body = body;
            return this;
        }

        public Server add() {
            Response r = new Response(statusCode, reason, headers, body);
            queue.offer(r);
            return server;
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
            ss.setSoTimeout(1000);
        } catch (SocketException e1) {
            throw new RuntimeException(e1);
        }
        while (keepGoing) {
            try {
                System.out.println("accepting");
                Socket socket = ss.accept();
                System.out.println("accepted " + socket);
                InputStream in = socket.getInputStream();
                {
                    List<String> lines = new ArrayList<>();
                    String line;
                    while ((line = readLine(in)).length() > 2) {
                        System.out.print(line);
                        lines.add(line.substring(0, line.length() - 2));
                    }
                }
                System.out.println("reading body");
                // read body
                readAll(in);
                while (keepGoing) {
                    try {
                        System.out.println("polling response queue");
                        Response response = queue.poll(100, TimeUnit.MILLISECONDS);
                        if (response != null) {
                            System.out.println("response=" + response);
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
                                copy(response.body(), out);
                                break;
                            }
                        }
                    } catch (InterruptedException e) {
                        // do nothing
                    }
                }
            } catch (SocketTimeoutException e) {
                // that's ok go round again
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int n;
        while ((n = in.read(buffer)) != -1) {
            out.write(buffer, 0, n);
        }
    }

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
        return out.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() throws Exception {
        this.keepGoing = false;
        this.ss.close();
        executor.shutdown();
    }
}
