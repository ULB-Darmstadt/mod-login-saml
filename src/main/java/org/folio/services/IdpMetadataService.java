/**
 * 
 */
package org.folio.services;

import java.util.List;

import org.folio.rest.jaxrs.model.SamlValidateResponse;

import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.Future;

/**
 * Interface for an abstract IDP metadata service.
 * 
 * @author Steve Osguthorpe
 *
 */
@ProxyGen
public interface IdpMetadataService {
  
  
  /**
   * Take in a string URL, parse and verify as a valid metadata file.
   * Return the list of available IDPs inline with the response to
   * allow configuration for multiple IDPs from a federation etc.
   * The IDP list includes any information it could extract from the
   * UI guidance elements and sets the items at root using the language
   * preferences supplied as part of the request, if any
   * (using the Accept-Language header.)
   * 
   * 
   * @param url The URL of the metadata
   * @param langs
   * @return SamlValidateResponse representing the parse/validate results
   */
  public Future<SamlValidateResponse> parse( String url, List<String> langs);
}
