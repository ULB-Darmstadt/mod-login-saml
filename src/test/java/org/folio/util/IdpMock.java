package org.folio.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class IdpMock extends AbstractVerticle {
  
  private final String getJsonData() {
    return new JsonObject()
      .put("validjson", true)
      .put("nested", new JsonObject()
        .put("name", "Nested Obj")  
      )
    .encodePrettily();
  }
  
  public void start(Promise<Void> promise) {
    final int port = context.config().getInteger("http.port");

    Router router = Router.router(vertx);
    HttpServer server = vertx.createHttpServer();

    router.route("/xml").handler(XMLContentHandler("application/xml"));
    router.route("/json").handler(JSONContentHandler("application/json"));
    router.route("/xml-incorrect-header").handler(XMLContentHandler("text/html"));
    router.route("/json-incorrect-header").handler(JSONContentHandler("application/xml"));
    router.route("/").handler(this::handleNoContentType);
    
    System.out.println("Running IdpMock on port " + port);
    server.requestHandler(router).listen(port, result -> {
      if (result.failed()) {
        promise.fail(result.cause());
      } else {
        promise.complete();
      }
    });
  }

  private void handleNoContentType(RoutingContext context) {
    handle(context, getJsonData(), null);
  }

  private void handle(RoutingContext context, String data, String contentType) {
    try {
      context.response()
        .setStatusCode(200)
        .putHeader("Content-Type", contentType)
        .end(data);
    } catch (Exception e) {
      context.response()
        .setStatusCode(500)
        .end(e.getMessage());
    }
  }
  
  private Handler<RoutingContext> XMLContentHandler(final String contentType) {
    return (RoutingContext context) -> {
      handleXMLContent(context, contentType);
    };
  }
  
  private Handler<RoutingContext> JSONContentHandler(final String contentType) {
    return (RoutingContext context) -> {
      handleJsonContent(context, contentType);
    };
  }
  
  private void handleXMLContent(RoutingContext context, String contentType) {
    try {
      handle(context, readMockData(), contentType);
    } catch (Exception e) {
      context.response()
        .setStatusCode(500)
        .end(e.getMessage());
    }
  }
  
  private void handleJsonContent(RoutingContext context, String contentType) {
    try {
      handle(context, getJsonData(), contentType);
    } catch (Exception e) {
      context.response()
        .setStatusCode(500)
        .end(e.getMessage());
    }
  }

  private String readMockData() throws IOException {
    Path path = Paths.get("src/test/resources/meta-idp.xml");
    return new String(Files.readAllBytes(path));
  }
}
