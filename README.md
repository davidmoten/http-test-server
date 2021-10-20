# http-test-server
<a href="https://github.com/davidmoten/http-test-server/actions/workflows/ci.yml"><img src="https://github.com/davidmoten/http-test-server/actions/workflows/ci.yml/badge.svg"/></a><br/>
[![codecov](https://codecov.io/gh/davidmoten/http-test-server/branch/master/graph/badge.svg?token=ZB09DXBFXM)](https://codecov.io/gh/davidmoten/http-test-server)<br/>
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/http-test-server/badge.svg?style=flat)](https://maven-badges.herokuapp.com/maven-central/com.github.davidmoten/http-test-server)<br/>

A very simple http server that returns responses in order that you specify (especially for unit testing).

**Status**: *deployed to Maven Central*

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
Note that `Content-Length` header is automatically returned in a response if not present already.

## Getting started
Add this dependency to your pom.xml:
```xml
<dependency>
  <groupId>com.github.davidmoten</groupId>
  <artifactId>http-test-server</artifactId>
  <version>VERSION_HERE</version>
</dependency>
```

## Demonstration
Run 
```bash
mvn exec:java
```
and you will see a line of output like 
```
http://127.0.0.1:40027/
```
Copy that url to a browser like Chrome and you will see the word "sometimes". Refresh the browser and you will see "something else", then "hi there". Hit refresh again and the page will sit loading forever because there is no response on the queue for that request. If you use the Inspect function in Chrome you will see the response headers set by the code for this session. 

The code we are running is [ServerMain.java](https://github.com/davidmoten/http-test-server/blob/master/src/test/java/com/github/davidmoten/http/test/server/ServerMain.java).
