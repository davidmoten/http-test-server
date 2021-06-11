package com.github.davidmoten.http.test.server;

public class ServerMain {

    public static void main(String[] args) throws InterruptedException {
        Server server = Server.start() //
                .response() //
                .statusCode(404) //
                .body("something") //
                .header("greeting", "hello") //
                .header("Content-Type", "text/plain") //
                .response() //
                .statusCode(202) //
                .body("something else") //
                .header("greeting", "hello") //
                .header("Content-Type", "text/plain") //
                .response()
                .body("hi there") //
                .add();
        System.out.println(server.baseUrl());
        Thread.sleep(100000);
    }

}
