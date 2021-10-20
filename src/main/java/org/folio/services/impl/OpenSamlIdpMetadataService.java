/**
 * 
 */
package org.folio.services.impl;

import java.io.InputStream;
import java.util.Collections;

import javax.validation.constraints.NotNull;

import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.services.IdpMetadataService;
import org.folio.util.ErrorHandlingUtil;
import org.folio.util.SamlMetadataUtil;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.metadata.resolver.index.impl.RoleMetadataIndex;
import org.pac4j.saml.util.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import net.shibboleth.utilities.java.support.xml.ParserPool;

/**
 * @author Steve Osguthorpe
 *
 */
public class OpenSamlIdpMetadataService implements IdpMetadataService {
  
  private static ParserPool _parserPool = null;
  private ParserPool getParserPool() {
    if (_parserPool == null) {
      _parserPool = Configuration.getParserPool();
    }
    return _parserPool;
  }

  private Future<SamlValidateResponse> parse (@NotNull Resource url) {

    return ErrorHandlingUtil.checkedFuture((Promise<SamlValidateResponse> handler) -> {
      final DOMMetadataResolver resolver;

      // Always close the stream.
      try (InputStream in = url.getInputStream()) {
        
        // Grab the MD.
        final Document inCommonMDDoc = Configuration.getParserPool().parse(in);
        final Element metadataRoot = inCommonMDDoc.getDocumentElement();
        resolver = new DOMMetadataResolver(metadataRoot);
        try {
          resolver.setIndexes(Collections.singleton(new RoleMetadataIndex()));
          resolver.setParserPool(getParserPool());
          resolver.setFailFastInitialization(true);
          resolver.setRequireValidMetadata(true);
          resolver.setMetadataFilter(SamlMetadataUtil.IDPOnlyFilter);
          resolver.setId(resolver.getClass().getCanonicalName());
          resolver.initialize();
          
          // If we get this far the file is likely valid.
          SamlValidateResponse resp = new SamlValidateResponse()
            .withValid(true)
            .withIdps(SamlMetadataUtil.extractIDPList(resolver).getIdps());
          
          handler.complete(resp);
        } finally {
          // Destroy to clean up.
          resolver.destroy();
        }
      }
      
    }).recover(throwable -> Future.succeededFuture(
      new SamlValidateResponse()
        .withValid(false)
        .withError(throwable.getMessage())
    ));
  }

  @Override
  public Future<SamlValidateResponse> parse (@NotNull String url) {
    return ErrorHandlingUtil.checkedFuture( (Promise<Resource> handler) -> {
      
      handler.complete(new UrlResource(url));
      
    }).compose(this::parse, throwable -> Future.succeededFuture(
        new SamlValidateResponse()
        .withValid(false)
        .withError(throwable.getMessage())
    ));
  }

}
