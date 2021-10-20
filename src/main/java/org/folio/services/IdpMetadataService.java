/**
 * 
 */
package org.folio.services;

import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.springframework.core.io.Resource;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * Interface for an abstract token service.
 * 
 * @author Steve Osguthorpe
 *
 */
@ProxyGen
public interface IdpMetadataService {
  public Future<SamlValidateResponse> parse( String url );
}
