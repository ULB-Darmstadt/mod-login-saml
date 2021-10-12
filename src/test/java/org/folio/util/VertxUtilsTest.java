package org.folio.util;

import static io.restassured.RestAssured.given;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.utils.NetworkUtils;
import org.folio.util.VertxUtils.DummySessionStore;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.pac4j.core.context.session.SessionStore;
import org.pac4j.vertx.VertxWebContext;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;

@RunWith(VertxUnitRunner.class)
public class VertxUtilsTest {

  public static final Logger logger = LogManager.getLogger(VertxUtilsTest.class);

  public static final String KEY = "key";
  public static final String VALUE = "foo";
  public static final String VALUE2 = "bar";

  private static Vertx vertx;
  private static int port;
  private static HttpServer server;

  @BeforeClass
  public static void beforeClass(TestContext context) {
    vertx = Vertx.vertx();
    port = NetworkUtils.nextFreePort();
    
    Router router = Router.router(vertx);
    server = vertx.createHttpServer();

    router.route("/foo")
      .handler(VertxUtilsTest::handle);

    server.requestHandler(router)
      .listen(port, result -> {
        if (result.failed()) {
          logger.error("Error starting server", result.cause());
        }
        
        context.asyncAssertSuccess().handle(
          result.map(server -> ((Object)server))
        );
      });
  }

  @AfterClass
  public static void afterClass(TestContext context) {
    
    server.close(result -> {
      if (result.failed()) {
        logger.error("Error closing server", result.cause());
      }
      // Always close vertx.
      vertx.close(context.asyncAssertSuccess());
    });
    
  }

  @Test
  public void testVertxUtils(TestContext context) {
    given().get("http://localhost:" + port + "/foo")
      .then()
      .statusCode(200).log().ifValidationFails();
  }

  private static void handle(RoutingContext rc) {
    try {
      Session session = new SharedDataSessionImpl(new PRNG(vertx));
      session.put(KEY, VALUE);

      SessionStore<VertxWebContext> sessionStore = new DummySessionStore(vertx, null);

      VertxWebContext ctx = VertxUtils.createWebContext(rc);
      assertTrue(sessionStore.get(ctx, KEY).isEmpty());
      assertEquals("", sessionStore.getOrCreateSessionId(ctx));

      Optional<SessionStore<VertxWebContext>> optSessionStore = sessionStore.buildFromTrackableSession(ctx, session);
      assertTrue(optSessionStore.isPresent());

      sessionStore = optSessionStore.get();

      assertEquals(VALUE, sessionStore.get(ctx, KEY).get());

      sessionStore.set(ctx, KEY, VALUE2);
      assertEquals(VALUE2, sessionStore.get(ctx, KEY).get());

      assertNotNull(sessionStore.getOrCreateSessionId(ctx));

      assertTrue(sessionStore.renewSession(ctx));

      assertTrue(sessionStore.destroySession(ctx));
      assertTrue(sessionStore.getTrackableSession(ctx).isEmpty());

      rc.response()
        .setStatusCode(200)
        .end();
    } catch (Exception t) {
      logger.error("Unexpected Error", t);
      rc.response()
        .setStatusCode(500)
        .end(t.getMessage());
    }
  }

}
