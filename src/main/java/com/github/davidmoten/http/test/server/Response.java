package com.github.davidmoten.http.test.server;

import java.util.List;
import java.util.Map;

public final class Response {

    private final int statusCode;
    private final Map<String, List<String>> headers;
    private final byte[] body;
    private String reason;

    Response(int statusCode, String reason, Map<String, List<String>> headers, byte[] body) {
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

    public byte[] body() {
        return body;
    }

    public String reason() {
        return reason;
    }
}
