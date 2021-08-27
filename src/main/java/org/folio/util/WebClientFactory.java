package org.folio.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
  
  private static final Map<Vertx, Future<WebClient>> clients = new ConcurrentHashMap<>();

  /**
   * Returns or Initializes and returns a Future WebClient for the provided Vertx.
   * Ensures we keep 1 client per Vertx instance to benefit from pooling etc.
   *
   * @param vertx
   */
  public static Future<WebClient> getWebClient(@NotNull Vertx vertx) {
    Future<WebClient> client;
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

  private static Future<WebClient> init(Vertx vertx) {

    ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    return configRetriever.getConfig().compose(conf -> {
      
      // Initialize with default object.
      WebClientOptions options;
      
      if (conf == null) {
        options = new WebClientOptions()
            .setKeepAlive( DEFAULT_KEEPALIVE )
            .setConnectTimeout( DEFAULT_TIMEOUT )
            .setIdleTimeout( DEFAULT_TIMEOUT );
      } else {        
        options = new WebClientOptions()
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
            log.error("Error parsing proxyAddress {}", pAdd);
          }
        }
      }
      WebClientInternal cli = (WebClientInternal)WebClient.create(vertx, options);
      return Future.succeededFuture(cli);
    });
  }
}
