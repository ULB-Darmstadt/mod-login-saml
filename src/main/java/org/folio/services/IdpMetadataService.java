/**
 * 
 */
package org.folio.services;

import java.util.List;

import org.folio.rest.jaxrs.model.SamlValidateResponse;

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
  public Future<SamlValidateResponse> parse( String url, List<String> langs);
}
