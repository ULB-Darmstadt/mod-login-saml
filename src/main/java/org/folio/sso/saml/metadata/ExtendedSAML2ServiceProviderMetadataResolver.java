package org.folio.sso.saml.metadata;

import java.util.Objects;

import org.opensaml.saml.common.SAMLObjectBuilder;
import org.opensaml.saml.ext.idpdisco.DiscoveryResponse;
import org.opensaml.saml.saml2.metadata.Extensions;
import org.opensaml.saml.saml2.metadata.IndexedEndpoint;
import org.opensaml.saml.saml2.metadata.SPSSODescriptor;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.crypto.CredentialProvider;
import org.pac4j.saml.metadata.SAML2MetadataGenerator;
import org.pac4j.saml.metadata.SAML2ServiceProviderMetadataResolver;
import org.pac4j.saml.metadata.SAMLMetadataGenerator;

public class ExtendedSAML2ServiceProviderMetadataResolver extends SAML2ServiceProviderMetadataResolver {

  public ExtendedSAML2ServiceProviderMetadataResolver (SAML2Configuration configuration,
      String callbackUrl, CredentialProvider credentialProvider) {
    super(configuration, callbackUrl, credentialProvider);
  }
  
  @Override
  protected SAMLMetadataGenerator buildMetadataGenerator() {
    final ExtendedSAML2MetadataGenerator metadataGenerator = new ExtendedSAML2MetadataGenerator();
    super.fillSAML2MetadataGenerator(metadataGenerator);
    addDiscoInfo(metadataGenerator);
    return metadataGenerator;
  }
  
  private void addDiscoInfo (ExtendedSAML2MetadataGenerator metadataGenerator) {
    // Add the discovery service.
    metadataGenerator.setDiscoUrl("https://local.disco.service");
  }

  private static class ExtendedSAML2MetadataGenerator extends SAML2MetadataGenerator {

    private String discoUrl = null;

    public void setDiscoUrl (String discoUrl) {
      this.discoUrl = discoUrl;
    }

    @Override
    protected SPSSODescriptor buildSPSSODescriptor() {
      final SPSSODescriptor ed = super.buildSPSSODescriptor();
      
      // Add the disco response if we have a disco URL set.
      if (discoUrl != null) {
        
        // Grab the current extensions.
        final Extensions ext = ed.getExtensions();
        ext.getNamespaceManager().registerAttributeName(DiscoveryResponse.DEFAULT_ELEMENT_NAME);
        
        // Grab a builder for our element.
        @SuppressWarnings("unchecked")
        final SAMLObjectBuilder<IndexedEndpoint> discoBuilder = (SAMLObjectBuilder<IndexedEndpoint>)
          this.builderFactory.getBuilder(DiscoveryResponse.DEFAULT_ELEMENT_NAME);
        
        // Build and add the element to the metadata.
        
        // Bug in SAML mod uses default metadata path instead of disco. Specify
        // parameters to the buildObject to prevent hitting this.
        final IndexedEndpoint discoResp = Objects.requireNonNull(discoBuilder).buildObject(
            DiscoveryResponse.DEFAULT_ELEMENT_NAME.getNamespaceURI(),
            DiscoveryResponse.DEFAULT_ELEMENT_NAME.getLocalPart(),
            DiscoveryResponse.DEFAULT_ELEMENT_NAME.getPrefix()
        );
        discoResp.setLocation(discoUrl);
        discoResp.setBinding(DiscoveryResponse.DEFAULT_ELEMENT_NAME.getNamespaceURI());
        
        ext.getUnknownXMLObjects().add(discoResp);
      }
      
      return ed;
    }
  }
}
