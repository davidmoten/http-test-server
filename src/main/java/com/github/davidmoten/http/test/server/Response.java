package com.github.davidmoten.http.test.server;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class Response implements AutoCloseable {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final InputStream body;
    private String reason;

    public Response(int statusCode, String reason, Map<String, List<String>> headers,
            InputStream body) {
        this.statusCode = statusCode;
        this.reason = reason;
        this.headers = headers;
        this.body = body;
    }

    public int statusCode() {
        return statusCode;
    }

    public Map<String, List<String>> headers() {
        return headers;
    }

    public InputStream body() {
        return body;
    }

    @Override
    public void close() throws Exception {
        if (body != null) {
            body.close();
        }
    }

    public String reason() {
        return reason;
    }
}
