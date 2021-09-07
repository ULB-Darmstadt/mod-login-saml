package org.folio.util;

import java.net.URI;
import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;

import org.folio.util.model.OkapiHeaders;

import io.vertx.ext.web.RoutingContext;

/**
 * Okapi utils
 *
 * @author rsass
 * @author Steve Osguthorpe
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
  
  public static String toOkapiUrl( @NotNull final String okapiLocation, @NotNull final String path) {
    final UriBuilder target = UriBuilder.fromUri(okapiLocation);
    final URI call = UriBuilder.fromUri(path).build();
    
    // Merge the OKAPI path to the target.
    target
      .path(call.getPath()) // Append the path.
      .replaceQuery(call.getQuery())
    ;
    final String okapiUri = target.build().toString();
    return okapiUri;
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
