/**
 * 
 */
package org.folio.services;

import java.util.Map;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * Interface for an abstract token service.
 * 
 * @author Steve Osguthorpe
 *
 */
@ProxyGen
public interface TokenService {
  
  
  /**
   * Create a token for a user id.
   * 
   * 
   * @param subject Token subject
   * @param userID User ID
   * @param headers Headers that are forwarded
   * @return The created token, if applicable
   */
  public Future<String> create( String subject, String userID, Map<String, String> headers);
}
