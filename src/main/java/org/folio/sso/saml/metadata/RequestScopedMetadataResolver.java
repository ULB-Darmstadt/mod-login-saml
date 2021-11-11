/**
 * 
 */
package org.folio.sso.saml.metadata;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.saml.metadata.SAML2MetadataResolver;
import org.pac4j.saml.util.Configuration;

import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

/**
 * @author Steve Osguthorpe
 *
 */
public class RequestScopedMetadataResolver implements SAML2MetadataResolver {

  private static final Logger log = LogManager.getLogger(RequestScopedMetadataResolver.class);
  private final SAML2MetadataResolver fullMetadata;
  private final EntityDescriptor entityDescriptor;
  
  public RequestScopedMetadataResolver(SAML2MetadataResolver fullMetadata, final WebContext webContext) {
    this.fullMetadata = fullMetadata;
    
    final String requestIdpId = webContext.getRequestParameter("entityID").orElseThrow(() -> {
      return new TechnicalException("Metadata cannot be retrieved because entityID is null");
    });

    try {
      entityDescriptor = fullMetadata.resolve().resolveSingle(new CriteriaSet(new EntityIdCriterion(requestIdpId)));
    } catch (ResolverException e) {
      throw new TechnicalException("Metadata cannot be retrieved because entityID could not be matched against known IDP entities", e);
    }
  }

  @Override
  public MetadataResolver resolve () {
    return fullMetadata.resolve();
  }

  @Override
  public String getEntityId () {
    return entityDescriptor.getEntityID();
  }

  @Override
  public String getMetadata() {
    return Configuration.serializeSamlObject(getEntityDescriptorElement()).toString();
  }

  @Override
  public XMLObject getEntityDescriptorElement () {
    return entityDescriptor;
  }

}
