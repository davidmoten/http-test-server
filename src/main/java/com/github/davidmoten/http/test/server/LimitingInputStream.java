package com.github.davidmoten.http.test.server;

import java.io.IOException;
import java.io.InputStream;

final class LimitingInputStream extends InputStream {

    private final InputStream in;
    private long limit;

    LimitingInputStream(InputStream in, long limit) {
        this.in = in;
        this.limit = limit;
    }

    @Override
    public int read() throws IOException {
        limit--;
        if (limit >= 0) {
            return in.read();
        } else {
            return -1;
        }
    }

}
