# http-test-server

A very simple http server that returns responses in order that you specify (in a unit test).

```java
try (Server server = 
    Server.start()
          .response() // default is status 200, no body, no headers
          .response() 
              .header("Accept", "application/json")
              .body("{}")
              .statusCode(201)
          .response().body("an error occurred").statusCode(500)
          .add()) {
    URL url = new URL(server.baseUrl() + "thing?state=joy");
    // hit the url a couple of times and do your asserts
}

```
