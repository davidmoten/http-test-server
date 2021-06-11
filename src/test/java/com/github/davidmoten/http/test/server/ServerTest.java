package com.github.davidmoten.http.test.server;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class ServerTest {

    @Test
    public void test() throws IOException {
        try (InputStream in = new ByteArrayInputStream(
                "GET thing\r\nboo".getBytes(StandardCharsets.UTF_8))) {
            String line = Server.readLine(in);
            assertEquals("GET thing\r\n", line);
        }
    }

    @Test
    public void testServer() throws Exception {
        try (Server server = Server.start().response().statusCode(200).add()) {
            URL url = new URL(server.baseUrl() + "thing?state=joy");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.addRequestProperty("Accept", "application-json");
            c.setDoOutput(false);
            c.setDoInput(true);
            Server.readAll(c.getInputStream());
            assertEquals(200, c.getResponseCode());
            assertEquals(0, c.getHeaderFields().size());
        }
    }

}
