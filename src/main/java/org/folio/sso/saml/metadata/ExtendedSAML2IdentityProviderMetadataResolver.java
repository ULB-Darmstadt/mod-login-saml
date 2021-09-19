package org.folio.sso.saml.metadata;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import javax.annotation.Nullable;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.xml.namespace.QName;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClients;
import org.opensaml.core.criterion.EntityIdCriterion;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.metadata.resolver.MetadataResolver;
import org.opensaml.saml.metadata.resolver.filter.impl.EntityRoleFilter;
import org.opensaml.saml.metadata.resolver.impl.FileBackedHTTPMetadataResolver;
import org.opensaml.saml.metadata.resolver.index.impl.RoleMetadataIndex;
import org.opensaml.saml.saml2.metadata.EntitiesDescriptor;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;
import org.pac4j.core.exception.TechnicalException;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.exceptions.SAMLException;
import org.pac4j.saml.metadata.SAML2MetadataResolver;
import org.pac4j.saml.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import net.shibboleth.utilities.java.support.component.ComponentInitializationException;
import net.shibboleth.utilities.java.support.resolver.CriteriaSet;
import net.shibboleth.utilities.java.support.resolver.ResolverException;

public class ExtendedSAML2IdentityProviderMetadataResolver implements SAML2MetadataResolver {

  //Ensure we only take into account the IDP listings by using a filter.
  private static final EntityRoleFilter IDPOnlyFilter = new EntityRoleFilter(
      Arrays.asList(new QName[] {IDPSSODescriptor.DEFAULT_ELEMENT_NAME}));
  
  protected final Logger logger = LoggerFactory.getLogger(getClass());

  private final Resource idpMetadataResource;
  private String idpEntityId;
  private FileBackedHTTPMetadataResolver idpMetadataProvider;
  
  private final String namePrefix;

  public ExtendedSAML2IdentityProviderMetadataResolver(final SAML2Configuration conf, final String namePrefix) {
    this(conf.getIdentityProviderMetadataResource(), conf.getServiceProviderEntityId(), namePrefix);
  }

  public ExtendedSAML2IdentityProviderMetadataResolver(final Resource idpMetadataResource, @Nullable final String idpEntityId, final String namePrefix) {
    CommonHelper.assertNotNull("idpMetadataResource", idpMetadataResource);
    CommonHelper.assertNotNull("namePrefix", namePrefix);
    this.namePrefix = namePrefix;
    this.idpMetadataResource = idpMetadataResource;
    this.idpEntityId = idpEntityId;
  }

  public void init() {
    assert (idpMetadataResource instanceof UrlResource);
    this.idpMetadataProvider = buildMetadata();
  }

  @Override
  public final MetadataResolver resolve() {
    return idpMetadataProvider;
  }

  protected FileBackedHTTPMetadataResolver buildMetadata() {
    try {      
      final FileBackedHTTPMetadataResolver resolver;
      try {
        final File tmpHandle = File.createTempFile(this.namePrefix, ".xml");
        
        final HttpClient client = HttpClients.custom()
          .useSystemProperties()
          .setSSLHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
          .setSSLContext(SSLContext.getDefault())
        .build();
        
        final String metaDataUrl = idpMetadataResource.getURI().toASCIIString();
        resolver = new FileBackedHTTPMetadataResolver(
            client, metaDataUrl, tmpHandle.getAbsolutePath());
        
        resolver.setIndexes(Collections.singleton(new RoleMetadataIndex()));
        resolver.setParserPool(Configuration.getParserPool());
        resolver.setFailFastInitialization(true);
        resolver.setRequireValidMetadata(true);
        resolver.setMetadataFilter(IDPOnlyFilter);
        resolver.setId(resolver.getClass().getCanonicalName());
        resolver.setInitializeFromBackupFile(false);
        resolver.initialize();
      } catch (final FileNotFoundException e) {
        throw new TechnicalException("Error loading idp Metadata");
      }
      // If no idpEntityId declared, select first EntityDescriptor entityId as our IDP entityId
      if (this.idpEntityId == null) {
        final Iterator<EntityDescriptor> it = resolver.iterator();

        while (it.hasNext()) {
          final EntityDescriptor entityDescriptor = it.next();
          if (this.idpEntityId == null) {
            this.idpEntityId = entityDescriptor.getEntityID();
          }
        }
      }

      if (this.idpEntityId == null) {
        throw new SAMLException("No idp entityId found");
      }

      return resolver;

    } catch (ResolverException e) {
      throw new SAMLException("Error initializing idpMetadataProvider", e);
    } catch (final ComponentInitializationException e) {
      throw new SAMLException("Error initializing idpMetadataProvider", e);
    } catch (final NoSuchAlgorithmException | IOException e) {
      throw new TechnicalException("Error getting idp Metadata resource", e);
    }
  }

  @Override
  public String getEntityId() {
    final XMLObject md = getEntityDescriptorElement();
    if (md instanceof EntitiesDescriptor) {
      return ((EntitiesDescriptor) md).getEntityDescriptors().get(0).getEntityID();
    } else if (md instanceof EntityDescriptor) {
      return ((EntityDescriptor) md).getEntityID();
    }
    throw new SAMLException("No idp entityId found");
  }

  @Override
  public String getMetadata() {
    if (getEntityDescriptorElement() != null) {
      return Configuration.serializeSamlObject(getEntityDescriptorElement()).toString();
    }
    throw new TechnicalException("Metadata cannot be retrieved because entity descriptor is null");
  }

  @Override
  public XMLObject getEntityDescriptorElement() {
    try {
      return resolve().resolveSingle(new CriteriaSet(new EntityIdCriterion(this.idpEntityId)));
    } catch (final ResolverException e) {
      throw new SAMLException("Error initializing IDPMetadata resolver", e);
    }
  }
}
