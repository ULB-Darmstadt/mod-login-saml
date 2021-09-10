package org.folio.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.validation.constraints.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.client.impl.WebClientInternal;

public class WebClientFactory {
  
  private static final Logger log = LogManager.getLogger(WebClientFactory.class);
  
  public static final int DEFAULT_TIMEOUT = 5000;
  public static final boolean DEFAULT_KEEPALIVE = false;
  
  private static final Map<Vertx, WebClient> clients = new ConcurrentHashMap<>();
  
  public static WebClient getWebClient() {
    try {
      return getWebClient(Vertx.currentContext().owner());
    } catch (NullPointerException ex) {
      throw new UnsupportedOperationException("Call to getWebClient() outside of vertx context. Try passing in a vertx instance instead.");
    }
  }

  /**
   * Returns or Initializes and returns a Future WebClient for the provided Vertx.
   * Ensures we keep 1 client per Vertx instance to benefit from pooling etc.
   *
   * This method is blocking, however, only the first invocation does the asynchronous
   * fetching of the config. Any subsequent invocations should succeed immediately
   * as the future is cached and returned for immediate success.
   *
   * @param vertx The vertx instance is used as a cache key for the Client Pool 
   */
  public static WebClient getWebClient(@NotNull Vertx vertx) {
    WebClient client;
    synchronized(clients) {
      client = clients.get(vertx);
      if ( client == null ) {
        try {
          client = init(vertx);
          clients.put(vertx, client);
        } catch (Exception e) {
          log.error("Failed to get a web client", e);
        }
      }
    }

    return client;
  }

  private WebClientFactory() {
  }

  private static WebClient init(Vertx vertx) throws InterruptedException, ExecutionException {

    ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    Future<WebClient> wcFuture = configRetriever.getConfig().compose(conf -> {
      
      // Initialize with default object.
      final WebClientOptions options = new WebClientOptions()
        .setKeepAlive( DEFAULT_KEEPALIVE )
        .setConnectTimeout( DEFAULT_TIMEOUT )
        .setIdleTimeout( DEFAULT_TIMEOUT );
      
      if (conf != null) {        
        options
          .setKeepAlive(conf.getBoolean("webclient.keepAlive", DEFAULT_KEEPALIVE))
          .setConnectTimeout(conf.getInteger("webclient.connectTimeout", DEFAULT_TIMEOUT))
          .setIdleTimeout(conf.getInteger("webclient.idleTimeout",DEFAULT_TIMEOUT));
        
        final String pAdd = conf.getString("webclient.proxyAddress");
        if (pAdd != null) {
          try {
            URI proxyAddress = new URI(pAdd);
            
            log.info("Proxying traffic using proxy address {}", pAdd);
            options.setProxyOptions(new ProxyOptions()
              .setType(ProxyType.valueOf(proxyAddress.getScheme().toUpperCase()))
              .setPort(proxyAddress.getPort())
              .setHost(proxyAddress.getHost()
            ));
          } catch (URISyntaxException e) {
            log.error("Cannot set proxy details. Error parsing proxyAddress {}", pAdd);
          }
        }
      }

      final boolean sslDisabledEnv = "true".equalsIgnoreCase(
        System.getenv("TRUST_ALL_CERTIFICATES")
      );
      
      options.setTrustAll( options.isTrustAll() || sslDisabledEnv);

      options.setVerifyHost( !(options.isVerifyHost() && sslDisabledEnv));
      
      WebClientInternal cli = (WebClientInternal)WebClient.create(vertx, options);
      
      return Future.succeededFuture(cli);
    });
    
    try {
      return wcFuture.toCompletionStage().toCompletableFuture().get(10, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
      throw new RuntimeException("Could not create web client", e);
    }
  }
}
