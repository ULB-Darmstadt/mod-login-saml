package org.folio.util;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.validation.constraints.NotNull;

import org.folio.rest.tools.utils.NetworkUtils;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.impl.ClientPhase;
import io.vertx.ext.web.client.impl.HttpContext;
import io.vertx.ext.web.client.impl.HttpRequestImpl;
import io.vertx.ext.web.client.impl.WebClientInternal;
import io.vertx.ext.web.client.impl.predicate.ResponsePredicateResultImpl;
import io.vertx.ext.web.client.predicate.ErrorConverter;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

public class WebClientFactory {

  public static final int DEFAULT_TIMEOUT = 5000;
  public static final boolean DEFAULT_KEEPALIVE = false;
  private static Map<Vertx, WebClient> clients = new ConcurrentHashMap<>();

  /**
   * Returns or Initializes and returns a WebClient for the provided Vertx.
   * Ensures we keep 1 client per Vertx instance to benefit from pooling etc.
   *
   * @param vertx
   */
  public static WebClient getWebClient(@NotNull Vertx vertx) {
    WebClient client;
    synchronized(clients) {
      client = clients.get(vertx);
      if ( client == null ) {
        client = init(vertx);
        clients.put(vertx, client);
      }
    }

    return client;
  }

  private WebClientFactory() {
  }

  private static WebClient init(Vertx vertx) {    
    WebClientOptions options = new WebClientOptions();
    options.setKeepAlive(DEFAULT_KEEPALIVE);
    options.setConnectTimeout(DEFAULT_TIMEOUT);
    options.setIdleTimeout(DEFAULT_TIMEOUT);

    // We create and cast as the internal type as that's where we can add listeners.
    WebClientInternal cli = (WebClientInternal)WebClient.create(vertx, options);

    try {
      cli.addInterceptor(new MockInterceptor(mockHttpTraffic()));
    } catch (URISyntaxException e) {
      e.printStackTrace();
    }
    return cli;
  }

  private static ServerMock mockHttpTraffic() throws URISyntaxException {

    ServerMock sm = new ServerMock(NetworkUtils.nextFreePort());
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        sm.close();
      }
    });
    return sm;
  }

  private static final class MockInterceptor implements Handler<HttpContext<?>> {

    final ServerMock sm;
    MockInterceptor(ServerMock sm) {
      this.sm = sm;
    }
    
    @Override
    public void handle(HttpContext<?> httpContext) {

      if (httpContext.phase() == ClientPhase.CREATE_REQUEST) {

        // Grab the built request options...
        if (sm.shouldProxy(httpContext)) {
          final RequestOptions ro = httpContext.requestOptions();

          // Divert.
          ro.setSsl(false) // Always plain none ssl traffic.
          .setHost(sm.getUri().getHost())
          .setPort(sm.getUri().getPort());
        }
      }

      httpContext.next();
    }
  }
}
