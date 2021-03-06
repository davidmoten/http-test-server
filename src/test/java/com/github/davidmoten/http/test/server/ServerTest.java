package com.github.davidmoten.http.test.server;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
        try (Server server = Server.start() //
                .response().statusCode(200) //
                .response().statusCode(500) //
                .add()) {
            URL url = new URL(server.baseUrl() + "thing?state=joy");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.addRequestProperty("Accept", "application-json");
            assertEquals(200, c.getResponseCode());
            Map<String, List<String>> h = c.getHeaderFields();
            assertEquals(Arrays.asList("HTTP/1.1 200 OK"), h.get(null));
            assertEquals(Arrays.asList("0"), h.get("Content-Length"));
            assertEquals(2, c.getHeaderFields().size());
            // can read empty input stream because connection figures out there is nothing 
            // there from Content-Length header = 0
            Util.readAll(c.getInputStream());
            c.disconnect();
        }
    }

    @Test
    public void testServerReturnsBody() throws Exception {
        try (Server server = Server.start() //
                .response().body("hi there").statusCode(200) //
                .response().statusCode(500) //
                .add()) {
            URL url = new URL(server.baseUrl() + "thing?state=joy");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setRequestMethod("GET");
            c.addRequestProperty("Accept", "application-json");
            c.setDoOutput(false);
            c.setDoInput(true);
            assertEquals("hi there",
                    new String(Util.readAll(c.getInputStream()), StandardCharsets.UTF_8));
            assertEquals(200, c.getResponseCode());
            assertEquals("8", c.getHeaderField("Content-Length"));
            c.disconnect();
        }
    }

    @Test
    public void testServerPut() throws Exception {
        try (Server server = Server.start() //
                .response().add()) {
            URL url = new URL(server.baseUrl() + "thing?state=joy");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setDoOutput(true);
            c.setDoInput(true);
            c.setRequestMethod("PUT");
            assertEquals(200, c.getResponseCode());
            c.disconnect();
        }
    }

}
