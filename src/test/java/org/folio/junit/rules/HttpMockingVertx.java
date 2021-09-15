package org.folio.junit.rules;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.folio.rest.RestVerticle;
import org.folio.rest.tools.utils.NetworkUtils;

import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Options;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.extension.responsetemplating.ResponseTemplateTransformer;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

/**
 * Provides and easy integration between WireMock and the verticle. The proxy
 * server instance (WireMock) is started and appropriate settings for the
 * verticle are used to enable internal proxying. Wherever we use the WebClientFactory,
 * those settings should apply. This means that if we internally request settings, we
 * are now able to mock that as part of our tests. This rule should be reusable in
 * tandem with the WebclientFactory. Declarative mocking in this way improves
 * testing legibility and removes canned test responses from run-time production
 * code.
 * 
 * @author Steve Osguthorpe
 */
public class HttpMockingVertx extends WireMockRule {

  public final int mockServerPort;
  public int vertxPort = -1;
  private final int suppliedVertxPort; 
  public Vertx vertx;
  
  public HttpMockingVertx () {
    this(NetworkUtils.nextFreePort(), -1); // Defer the vertx setting next free port till later.
  }
  
  public HttpMockingVertx (final int mockServerPort) {
    this(mockServerPort, -1);
  }

  public HttpMockingVertx(final int mockServerPort, final int vertxPort) {
    super(
      wireMockConfig()
        .port(mockServerPort)
        .enableBrowserProxying(true)
        .notifier(new ConsoleNotifier(true))
        .extensions(new ResponseTemplateTransformer(false, getHelpers()))
      , false);
    this.mockServerPort = mockServerPort;
    this.suppliedVertxPort = vertxPort;
  }

  @Override
  public void stop () {
    try {
      super.stop();
    } finally {
      final CompletableFuture<Void> wait = new CompletableFuture<>();
      vertx.close(h -> { wait.complete(null); });
      try {
        wait.get();
      } catch (Exception e) { 
        e.printStackTrace();
      }
    }
  }

  @Override
  public void start () {
    super.start();
    
    if (suppliedVertxPort == -1 && vertxPort == -1) {
      vertxPort = NetworkUtils.nextFreePort();
    } else {
      vertxPort = suppliedVertxPort;
    }

    // Augment the properties if not set.
    final Properties props = System.getProperties();
    props.put("mock.httpclient", "true");
    props.put("http.proxyHost", "localhost");
    props.put("http.proxyPort", "" + mockServerPort);
    
//    if (!props.containsKey("webclient.proxyAddress")) {
//      props.put("http.proxyAddress", "http://localhost:" + mockServerPort);
//    }
    
    vertx = Vertx.vertx(new VertxOptions()
        .setBlockedThreadCheckInterval(1000*60*60));
    
    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put("http.port", vertxPort)
      );
    final CompletableFuture<Boolean> wait = new CompletableFuture<>();
    vertx.deployVerticle(new RestVerticle(), options, res -> { wait.complete(res.succeeded()); });
    try {
      wait.get();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static Map<String, Helper<?>> getHelpers() {
    Map<String, Helper<?>> helpers = new HashMap<>();
    helpers.put("set-value", setValue);
    helpers.put("json", jsonString);
    
    return helpers;
    
  }
  
  private static Helper<Map<String,Object>> setValue = new Helper<Map<String,Object>>() {

    @Override
    public Object apply (Map<String,Object> context, Options options) throws IOException {
      
      final String key = options.param(0);
      final Object value = options.param(1);
      context.put(key, value);
      return null;
    }
  };
  
  private static Helper<Object> jsonString = new Helper<Object>() {

    @Override
    public Object apply (Object context, Options options) throws IOException {
      return Json.encode(context);
    }
  };
}
