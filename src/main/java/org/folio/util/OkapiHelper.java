package org.folio.util;

import io.vertx.ext.web.RoutingContext;
import org.folio.util.model.OkapiHeaders;

import java.util.Map;

/**
 * Okapi utils
 *
 * @author rsass
 */
public class OkapiHelper {


  private static final String CACHE_KEY = "OKAPI_HEADERS";
  
  /**
   * Extract Okapi specific headers from current routing context
   */
  public static OkapiHeaders okapiHeaders( RoutingContext routingContext ) {

    // Grab from request cache.
    OkapiHeaders headers = routingContext.get(CACHE_KEY);
    if (headers != null) return headers;
    
    // Not present yet, lets create it.
    headers = new OkapiHeaders();

    headers.setUrl(routingContext.request().getHeader(OkapiHeaders.OKAPI_URL_HEADER));
    headers.setTenant(routingContext.request().getHeader(OkapiHeaders.OKAPI_TENANT_HEADER));
    headers.setToken(routingContext.request().getHeader(OkapiHeaders.OKAPI_TOKEN_HEADER));
    headers.setPermissions(routingContext.request().getHeader(OkapiHeaders.OKAPI_PERMISSIONS_HEADER));
    
    // Stash in the request safe context.
    routingContext.put(CACHE_KEY, headers);
    return headers;

  }

  public static OkapiHeaders okapiHeaders(Map<String, String> parsedHeaders) {

    OkapiHeaders headers = new OkapiHeaders();

    headers.setUrl(parsedHeaders.get(OkapiHeaders.OKAPI_URL_HEADER));
    headers.setTenant(parsedHeaders.get(OkapiHeaders.OKAPI_TENANT_HEADER));
    headers.setToken(parsedHeaders.get(OkapiHeaders.OKAPI_TOKEN_HEADER));
    headers.setPermissions(parsedHeaders.get(OkapiHeaders.OKAPI_PERMISSIONS_HEADER));

    return headers;

  }
}
