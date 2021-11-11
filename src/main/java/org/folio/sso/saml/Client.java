package org.folio.sso.saml;

import java.nio.charset.StandardCharsets;
import java.util.*;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.JsonReponseSaml2RedirectActionBuilder;
import org.folio.sso.saml.Constants.Config;
import org.folio.sso.saml.metadata.DiscoAwareServiceProviderMetadataResolver;
import org.folio.sso.saml.metadata.FederationIdentityProviderMetadataResolver;
import org.folio.sso.saml.metadata.FederationSAML2ContextProvider;
import org.folio.util.ErrorHandlingUtil;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.Attribute;
import org.opensaml.saml.saml2.core.Conditions;
import org.opensaml.saml.saml2.core.NameID;
import org.opensaml.saml.saml2.core.impl.ConditionsBuilder;
import org.opensaml.saml.saml2.core.impl.NameIDBuilder;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.saml.state.SAML2StateGenerator;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

/**
 * Extension of the base client from Pac4J. Allows us to properly customise the
 * client and provide handlers that extend the base code.
 * 
 * @author Steve Osguthorpe
 *
 */
public class Client extends SAML2Client {
  
  /**
   * TODO: This needs refactoring out into something better.
   */
  private static class MockClient extends Client {

    private static final Logger log = LogManager.getLogger(MockClient.class);
    public static final String SAML_USER_ID = "saml-user-id";

    public MockClient(final SAML2Configuration cfg) {
      super(cfg);
      log.info("SAML2 Client MOCK mode");
    }

    @Override
    protected Optional<SAML2Credentials> retrieveCredentials(WebContext context) {
      log.info("Mocking Client retrieveCredentials...");

      assert(context.getRequestParameter("SAMLResponse").isPresent());

      NameID nameId = new NameIDBuilder().buildObject();
      String issuerId = this.getClass().getName();
      List<Attribute> samlAttributes = new ArrayList<>();
      Conditions conditions = new ConditionsBuilder().buildObject();
      List<String> authnContexts = new ArrayList<>();
      SAML2Credentials cred = new SAML2Credentials(nameId, issuerId, samlAttributes, conditions, "1", authnContexts);

      CommonProfile userProfile = new CommonProfile();
      userProfile.addAttribute("UserID", Arrays.asList(SAML_USER_ID));
      cred.setUserProfile(userProfile);
      return Optional.of(cred);
    }

  }
  
  private static final String CACHE_KEY = "SAML_CLIENT";
  private static final Logger log = LogManager.getLogger(Client.class);
  
  private static String buildCallbackUrl(String okapiUrl, String tenantId) {
    return okapiUrl + "/_/invoke/tenant/" + CommonHelper.urlEncode(tenantId) + Config.ENDPOINT_CALLBACK;
  }
  
  private static Future<Client> createClient(final RoutingContext routingContext, final boolean generateMissingKeyStore) {
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext);
    final String tenantId = okapiHeaders.getTenant();
    
    return ModuleConfig.get(routingContext).compose(config -> {
      final Promise<Client> clientInstantiationFuture = Promise.promise();

      final String idpUrl = config.getIdpUrl();
      final String keystore = config.getKeystore();
      final String keystorePassword = config.getKeystorePassword();
      final String privateKeyPassword = config.getPrivateKeyPassword();
      final String samlBinding = config.getSamlBinding();
      final String okapiUrl = config.getOkapiUrl();

      final Vertx vertx = routingContext.vertx();

      if (StringUtils.isBlank(idpUrl)) {
        return Future.failedFuture("There is no metadata configuration stored!");
      }

      if (StringUtils.isBlank(keystore)) {
        if (!generateMissingKeyStore) {
          return Future.failedFuture("No KeyStore stored in configuration and regeneration is not allowed.");
        }
        
        // Generate new KeyStore
        final String randomId = RandomStringUtils.randomAlphanumeric(12);
        final String randomFileName = RandomStringUtils.randomAlphanumeric(12);

        final String actualKeystorePassword = StringUtils.isEmpty(keystorePassword) ? randomId : keystorePassword;
        final String actualPrivateKeyPassword = StringUtils.isEmpty(privateKeyPassword) ? randomId : privateKeyPassword;
        final String keystoreFileName = "temp_" + randomFileName + ".jks";

        Client saml2Client = get(okapiUrl, tenantId, idpUrl, actualKeystorePassword, actualPrivateKeyPassword, keystoreFileName, samlBinding);

        vertx.executeBlocking(blockingHandler -> {
            saml2Client.init();
            blockingHandler.complete();
          },
          samlClientInitHandler -> {
            if (samlClientInitHandler.failed()) {
              clientInstantiationFuture.fail(samlClientInitHandler.cause());
            } else {
              storeKeystore(routingContext, keystoreFileName, actualKeystorePassword, actualPrivateKeyPassword).onComplete(keyfileStorageHandler -> {
                if (keyfileStorageHandler.succeeded()) {
                  // storeKeystore is deleting JKS file, recreate client from byteArray
                  ErrorHandlingUtil.handleThrowables(clientInstantiationFuture, () -> {
                    Buffer keystoreBytes = keyfileStorageHandler.result();
                    ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                    UrlResource idpUrlResource = new UrlResource(idpUrl);
                    Client reinitedSaml2Client = get(okapiUrl, tenantId, actualKeystorePassword, actualPrivateKeyPassword, idpUrlResource, keystoreResource, samlBinding);

                    clientInstantiationFuture.complete(reinitedSaml2Client);
                  });
                } else {
                  clientInstantiationFuture.fail(keyfileStorageHandler.cause());
                }
              });
            }
          });
      } else {
        // Load KeyStore from configuration
        vertx.executeBlocking((Promise<Buffer> blockingCode) ->
            blockingCode.complete(Buffer.buffer(Base64.getDecoder().decode(keystore))),
          resultHandler -> {
            if (resultHandler.failed()) {
              clientInstantiationFuture.fail(resultHandler.cause());
            } else {
              ErrorHandlingUtil.handleThrowables(clientInstantiationFuture, () -> {
                Buffer keystoreBytes = resultHandler.result();
                ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
              
                UrlResource idpUrlResource = new UrlResource(idpUrl);
                Client saml2Client = get(okapiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource, keystoreResource, samlBinding);
                saml2Client.init();
                
                clientInstantiationFuture.complete(saml2Client);
              });
            }
          });
      }
      return clientInstantiationFuture.future();
    });
  }
  
  public static void forceReinit() {
    // Clear the caches...
    FederationIdentityProviderMetadataResolver.clearCache();
  }
  
  public static void forceReinit(final String tenant) {
    // Clear the cache for the single tenant...
    FederationIdentityProviderMetadataResolver.clearCache(tenant);
  }
  
  public static Future<Client> get ( final RoutingContext routingContext ) {
    return get(routingContext, false, true);
  }
  
  public static Future<Client> get ( final RoutingContext routingContext, final boolean generateMissingKeyStore, final boolean reinitialize ) {
       
    Future<Client> future;
    if (!reinitialize) {
      future = routingContext.get(CACHE_KEY);
      if (future != null) {
        log.debug("Returning client from request cache");
        return future;
      }
    }
    
    // Else create and cache in the request.
    log.debug("Creating new client");
    future = createClient( routingContext, generateMissingKeyStore );
    
    routingContext.put( CACHE_KEY, future );
    return future;
  }
    
  private static Client get(String okapiUrl, String tenantId, SAML2Configuration cfg, String samlBinding) {
    
    // Default lifetitme.
    cfg.setMaximumAuthenticationLifetime(18000);
    cfg.setAuthnRequestBindingType(
      "REDIRECT".equalsIgnoreCase(samlBinding) ? 
        SAMLConstants.SAML2_REDIRECT_BINDING_URI : 
          SAMLConstants.SAML2_POST_BINDING_URI); // POST is the default
    
    /* TODO: THIS SUCKS!!! Production code should not have mocks embedded.
       If time we need to refactor this out and produce a mocked IDP response
       and interact properly instead of mocking responses in this manor. */ 
    final boolean mock = Boolean.parseBoolean(System.getProperty("mock.httpclient")); 
    
    
    Client saml2Client = mock ? new MockClient(cfg) : new Client(cfg);
    saml2Client.setName(tenantId);
    saml2Client.setCallbackUrl(buildCallbackUrl(okapiUrl, tenantId));
    saml2Client.setRedirectionActionBuilder(new JsonReponseSaml2RedirectActionBuilder(saml2Client));
    saml2Client.setStateGenerator(new SAML2StateGenerator(saml2Client));
    saml2Client.setCallbackUrlResolver(null);
    
    return saml2Client;
  }


  private static Client get(String okapiUrl, String tenantId, String idpUrl, String keystorePassword, String actualPrivateKeyPassword, String keystoreFileName, String samlBinding) {
    final SAML2Configuration cfg = new SAML2Configuration(
      keystoreFileName,
      keystorePassword,
      actualPrivateKeyPassword,
      idpUrl);
    
    cfg.setCertificateExpirationPeriod(Config.CERTIFICATE_VALIDITY);

    return get(okapiUrl, tenantId, cfg, samlBinding);
  }


  private static Client get(String okapiUrl, String tenantId, String keystorePassword, String privateKeyPassword, UrlResource idpUrlResource, Resource keystoreResource, String samlBinding) {

    final SAML2Configuration byteArrayCfg = new SAML2Configuration(
      keystoreResource,
      keystorePassword,
      privateKeyPassword,
      idpUrlResource);
    
    byteArrayCfg.setCertificateExpirationPeriod(Config.CERTIFICATE_VALIDITY);

    return get(okapiUrl, tenantId, byteArrayCfg, samlBinding);
  }

  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(final RoutingContext routingContext, final String keystoreFileName, final String keystorePassword, final String privateKeyPassword) {

    final Vertx vertx = routingContext.vertx();
    
    // Move file ops off the event loop.
    return vertx.executeBlocking((Promise<Buffer> blockingFuture) -> {
      
      ErrorHandlingUtil.handleThrowables(blockingFuture,
          
        vertx.fileSystem().readFile(keystoreFileName)
          .onSuccess(buffer -> {
            
            ErrorHandlingUtil.handleThrowables(blockingFuture, () -> {
              
              Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(buffer.getBytes()));
              
              ErrorHandlingUtil.handleThrowables(blockingFuture,
                ModuleConfig.get(routingContext).compose((ModuleConfig config) -> {
                  return CompositeFuture.all(
                    config.updateEntry(Config.KEYSTORE_FILE, encodedBytes.toString(StandardCharsets.UTF_8)),
                    config.updateEntry(Config.KEYSTORE_PASSWORD, keystorePassword),
                    config.updateEntry(Config.KEYSTORE_PRIVATEKEY_PASSWORD, privateKeyPassword)
//                    config.updateEntry(Config.METADATA_INVALIDATED, "true") // if keystore modified, current metadata is invalid.
                    
                  ).onComplete(configEntriesHandler -> {
                    // We still attempt the delete no matter what.
                    vertx.fileSystem().delete(keystoreFileName, deleteResult -> {
                      Throwable failureCause = null;
                      
                      // If the initial operation failed then log.
                      if (configEntriesHandler.failed()) {
                        failureCause = configEntriesHandler.cause();
                        log.error ("Error storing configuration", failureCause);
                      }
                      
                      // We should log the error with delete too, to prevent it from
                      // being lost if the storage op fails.
                      if (deleteResult.failed()) {
                        Throwable deleteFailure = deleteResult.cause();
                        log.error ("Error deleteing keystore file", deleteFailure);
                        if (failureCause == null) failureCause = deleteFailure;
                      }
                      
                      // Succeed buffered future if appropriate
                      if (configEntriesHandler.succeeded()) {
                        // Success
                        blockingFuture.complete(buffer);
                      }
                    });
                  });
                })
              );
            });
          })
        );
    });
  }
  
  private Client() {}

  private Client( SAML2Configuration cfg ) {
    super(cfg);
  }
  
  @Override
  protected void initIdentityProviderMetadataResolver() {
    try {
      FederationIdentityProviderMetadataResolver md = new FederationIdentityProviderMetadataResolver(
          this.configuration,
          getName()
      );
      this.idpMetadataResolver = md;
      md.init();
    } catch (Exception e) {
      log.error("Error creating Saml2Client", e);
      throw new RuntimeException("Could not create IDPMetadata resolver", e);
    }
  }
  
  @Override
  protected void initServiceProviderMetadataResolver() {
    this.spMetadataResolver = new DiscoAwareServiceProviderMetadataResolver(this.configuration,
        computeFinalCallbackUrl(null),
        this.credentialProvider);
    this.spMetadataResolver.resolve();
  }
  
  @Override
  protected void initSAMLContextProvider() {
    // Build the contextProvider
    this.contextProvider = new FederationSAML2ContextProvider(
        this.idpMetadataResolver,
        this.spMetadataResolver,
        this.configuration.getSamlMessageStoreFactory());
  }
}
