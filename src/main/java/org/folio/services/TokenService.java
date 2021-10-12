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
  public Future<String> create( String subject, String userID, Map<String, String> headers);
}
