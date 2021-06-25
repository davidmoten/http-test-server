package com.github.davidmoten.http.test.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

final class Util {

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
}
