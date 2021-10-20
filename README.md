# http-test-server
<a href="https://github.com/davidmoten/http-test-server/actions/workflows/ci.yml"><img src="https://github.com/davidmoten/http-test-server/actions/workflows/ci.yml/badge.svg"/></a><br/>
[![codecov](https://codecov.io/gh/davidmoten/http-test-server/branch/master/graph/badge.svg?token=ZB09DXBFXM)](https://codecov.io/gh/davidmoten/http-test-server)<br/>
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/http-test-server/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/http-test-server)<br/>

A very simple http server that returns responses in order that you specify (in a unit test).

```java
try (Server server = 
    Server.start()
          .response() // default is status 200, no body, no headers
          .response() 
              .header("Accept", "application/json")
              .body("{}")
              .statusCode(201)
          .response()
              .body("an error occurred")
              .statusCode(500)
          .add()) {
    URL url = new URL(server.baseUrl() + "thing?state=joy");
    // hit the url a couple of times and do your asserts
}

```
