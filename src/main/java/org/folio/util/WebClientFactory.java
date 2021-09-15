package org.folio.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
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

/**
 * Factory class to centralize the WebClient creation to provide singleton access
 * 
 * @author Steve Osguthorpe
 *
 */
public class WebClientFactory {
  
  private static final Logger log = LogManager.getLogger(WebClientFactory.class);
  
  public static final int DEFAULT_TIMEOUT = 5000;
  public static final boolean DEFAULT_KEEPALIVE = false;
  
  private static final Map<Vertx, WebClient> clients = new ConcurrentHashMap<>();
  
  /**
   * Create a client grabbing the owning vertx instance from the current context.
   * 
   * @see {@link #getWebClient(Vertx) getWebClient(Vertx vertx)}
   */
  public static WebClient getWebClient() {
    try {
      return getWebClient(Vertx.currentContext().owner());
    } catch (NullPointerException ex) {
      throw new UnsupportedOperationException("Call to getWebClient() outside of vertx context. Try passing in a vertx instance instead.");
    }
  }

  /**
   * Returns or Initializes and returns a WebClient for the provided Vertx instance.
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

  private static WebClient init(Vertx vertx) throws Exception {
    
    ConfigRetriever configRetriever = ConfigRetriever.create(vertx);
    Future<WebClient> wcFuture = configRetriever.getConfig().compose(conf -> {
      
      // Initialize with default object.
      final WebClientOptions options = new WebClientOptions()
        .setKeepAlive( DEFAULT_KEEPALIVE )
        .setConnectTimeout( DEFAULT_TIMEOUT )
        .setIdleTimeout( DEFAULT_TIMEOUT );
      
      options
        .setKeepAlive(conf.getBoolean("http.keepAlive", DEFAULT_KEEPALIVE))
        .setConnectTimeout(conf.getInteger("http.connectTimeout", DEFAULT_TIMEOUT))
        .setIdleTimeout(conf.getInteger("http.idleTimeout",DEFAULT_TIMEOUT))
        .setMaxPoolSize(conf.getInteger("http.maxConnections", options.getMaxPoolSize()));
      
      final String httpAgent = conf.getString("http.agent");
      if (StringUtils.isNotBlank(httpAgent)) {
        options.setUserAgentEnabled(true)
          .setUserAgent(httpAgent);
      }
      
      final String httpsProtocols = conf.getString("https.protocols");
      if (StringUtils.isNotBlank(httpsProtocols)) {
        Set<String> protocolSet = new HashSet<String>();
        protocolSet.addAll(Arrays.asList(httpsProtocols.split(",")));
        options.setEnabledSecureTransportProtocols(protocolSet);
      }
      
      final String[] ciphers = conf.getString("https.cipherSuites", "").split(",");
      for (String cipher : ciphers) {
        if (StringUtils.isNotBlank(cipher)) {
          options.addEnabledCipherSuite(cipher);
        }
      }
      
      // Handle the various proxy routes.
      String pAdd = conf.getString("http.proxyHost");
      if (pAdd != null) {
        pAdd = "http://" + pAdd + ":" + conf.getInteger("http.proxyPort", 80);
      }
      
      // Allow a specific override for application traffic only.
      pAdd = conf.getString("vertx.webClient.proxyAddress", pAdd);
      if (pAdd != null) {
        try {
          URI proxyAddress = new URI(pAdd);
          
          log.info("Proxying traffic using proxy address {}", pAdd);
          options.setProxyOptions(new ProxyOptions()
            .setType(ProxyType.HTTP)
            .setPort(proxyAddress.getPort())
            .setHost(proxyAddress.getHost())
          );
        } catch (URISyntaxException e) {
          log.error("Cannot set proxy details. Error parsing proxy address {}", pAdd);
        }
      }

      // Ensure we trust certs when the trust all certs flag is passed in.
      final boolean sslDisabledEnv = conf.getBoolean("TRUST_ALL_CERTIFICATES", false);
      options.setTrustAll( options.isTrustAll() || sslDisabledEnv);
      options.setVerifyHost( !(options.isVerifyHost() && sslDisabledEnv));
      
      WebClientInternal cli = (WebClientInternal)WebClient.create(vertx, options);
      
      return Future.succeededFuture(cli);
    });
    
    try {
      return AsyncUtil.blocking(wcFuture);
      
    } catch (TimeoutException e) {
      throw new RuntimeException("Could not create web client", e);
    }
  }
}
