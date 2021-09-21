package org.folio.sso.saml.metadata;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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


/**
 * Pac4j's internal implementations have a 1:1 relationship with the SamlClient
 * and the IDP metadata resolver. This means if we cache the clients we'll end
 * up caching the IDP MD provider too. Given we want to support multiple IDPs
 * then we need to be able to instantiate a new Resolver every request, which in
 * turn means we need to instantiate a client every time.
 * 
 * This class is a replacement designed to be instantiated every request, whilst
 * maintaining a cache of the internal OpenSaml Metadata provider. This enables
 * us to still keep the expensive operations around remote metadata kept in the
 * background while also allowing the relatively inexpensive instances of the
 * client created every time.
 * 
 * @author Steve Osguthorpe
 *
 */
public class DiscoAwareIdentityProviderMetadataResolver implements SAML2MetadataResolver {
  
  //Ensure we only take into account the IDP listings by using a filter.
  private static final EntityRoleFilter IDPOnlyFilter = new EntityRoleFilter(
      Arrays.asList(new QName[] {IDPSSODescriptor.DEFAULT_ELEMENT_NAME}));

  private static final Map<String,FileBackedHTTPMetadataResolver> RESOLVER_CACHE =
      new ConcurrentHashMap<>();
  
  public static void clearCache( final String key ) {
    FileBackedHTTPMetadataResolver res;
    
    synchronized (RESOLVER_CACHE) {
      res = RESOLVER_CACHE.remove(key);
    }
    
    if (res != null) {
      res.destroy();
    }
  }
  
  public static void clearCache() {
    synchronized (RESOLVER_CACHE) {
      Set<String> keysCopy = new HashSet<>(RESOLVER_CACHE.keySet());
      for (final String instId : keysCopy) {
        clearCache(instId);
      }
    }
  }
  
  private FileBackedHTTPMetadataResolver getOrCreateRemoteMetadataResolver() throws ResolverException, IOException, NoSuchAlgorithmException, ComponentInitializationException {
    synchronized (RESOLVER_CACHE) {
      FileBackedHTTPMetadataResolver remoteResolver = RESOLVER_CACHE.get(instanceName);
      if ( remoteResolver == null ) {
        final HttpClient client = HttpClients.custom()
          .useSystemProperties()
          .setSSLHostnameVerifier(HttpsURLConnection.getDefaultHostnameVerifier())
          .setSSLContext(SSLContext.getDefault())
        .build();
        
        final String metadataUrl = remoteMetadataResource.getURI().toASCIIString();
        logger.info("Using URL: {}", metadataUrl);
        
        final Path tmpFile = Files.createTempFile("metadata", ".xml");
        
        remoteResolver = new FileBackedHTTPMetadataResolver(
          client, metadataUrl, tmpFile.toAbsolutePath().toString());
        
        remoteResolver.setIndexes(Collections.singleton(new RoleMetadataIndex()));
        remoteResolver.setParserPool(Configuration.getParserPool());
        remoteResolver.setFailFastInitialization(true);
        remoteResolver.setRequireValidMetadata(true);
        remoteResolver.setMetadataFilter(IDPOnlyFilter);
        remoteResolver.setId(
            instanceName + ":" + remoteResolver.getClass().getCanonicalName());
        remoteResolver.setInitializeFromBackupFile(false);
        remoteResolver.initialize();
        
        RESOLVER_CACHE.put(instanceName, remoteResolver);
      }
      
      return remoteResolver;
    }
  }
  
  private String idpEntityId;

  private final String instanceName;
  protected final Logger logger = LoggerFactory.getLogger(getClass());
  
  private MetadataResolver remoteMetadataProvider;

  private final Resource remoteMetadataResource;
  
  private int totalNumOfIdps = -1;
  
  public boolean hassMultipleIdpsConfigure() {
    return totalNumOfIdps > 1;
  }

  public DiscoAwareIdentityProviderMetadataResolver(final Resource idpMetadataResource, @Nullable final String idpEntityId, final String instanceName) {
    CommonHelper.assertNotNull("idpMetadataResource", idpMetadataResource);
    CommonHelper.assertNotNull("namePrefix", instanceName);
    this.instanceName = instanceName;
    this.remoteMetadataResource = idpMetadataResource;
    this.idpEntityId = idpEntityId;
  }
  
  public DiscoAwareIdentityProviderMetadataResolver(final SAML2Configuration conf, final String instanceName) {
    this(conf.getIdentityProviderMetadataResource(), conf.getIdentityProviderEntityId(), instanceName);
  }

  /**
   * Currently active IDP metadata element.
   * 
   * Derived from the current request.
   */
  @Override
  public XMLObject getEntityDescriptorElement() {
    try {
      return resolve().resolveSingle(new CriteriaSet(new EntityIdCriterion(this.idpEntityId)));
    } catch (final ResolverException e) {
      throw new SAMLException("Error initializing IDPMetadata resolver", e);
    }
  }

  /**
   * Selected IDP entity ID.
   */
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

  
  /**
   * Serialize the active IDP metadata element, and return it as a string.
   */
  @Override
  public String getMetadata() {
    if (getEntityDescriptorElement() != null) {
      return Configuration.serializeSamlObject(getEntityDescriptorElement()).toString();
    }
    throw new TechnicalException("Metadata cannot be retrieved because entity descriptor is null");
  }

  public void init() {
    assert (remoteMetadataResource instanceof UrlResource);
    remoteMetadataProvider = initializeRemoteResolver();
  }

  protected FileBackedHTTPMetadataResolver initializeRemoteResolver() {
    try {      
      final FileBackedHTTPMetadataResolver resolver;
      try {
        
        resolver = getOrCreateRemoteMetadataResolver();
        
      } catch (final FileNotFoundException e) {
        throw new TechnicalException("Error loading idp Metadata");
      }
      
      // We now accept multiple IDP entries.
      // If there are multiples present in MD, then we should flag multiple.
      // If only 1 is present in then we can make other assumptions.
      
      // If no idpEntityId declared, select first EntityDescriptor entityId as our IDP entityId
      final Iterator<EntityDescriptor> it = resolver.iterator();

      totalNumOfIdps = 0;
      while (it.hasNext()) {
        final EntityDescriptor entityDescriptor = it.next();
        totalNumOfIdps++;
        this.idpEntityId = entityDescriptor.getEntityID();
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
  public final MetadataResolver resolve() {
    return remoteMetadataProvider;
  }
}
