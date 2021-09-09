package org.folio.sso.saml;

import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.JsonReponseSaml2RedirectActionBuilder;
import org.folio.sso.saml.Constants.Config;
import org.folio.sso.saml.metadata.ExtendedSAML2ServiceProviderMetadataResolver;
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

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

public class Client extends SAML2Client {
  
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

  private static final Map<String, Future<Client>> tenantCache = new ConcurrentHashMap<String, Future<Client>>();
  
  private static String buildCallbackUrl(String okapiUrl, String tenantId) {
    return okapiUrl + "/_/invoke/tenant/" + CommonHelper.urlEncode(tenantId) + Config.CALLBACK_ENDPOINT;
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
        return Future.failedFuture("There is no IdP configuration stored!");
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
                  Buffer keystoreBytes = keyfileStorageHandler.result();
                  ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
                  try {
                    UrlResource idpUrlResource = new UrlResource(idpUrl);
                    Client reinitedSaml2Client = get(okapiUrl, tenantId, actualKeystorePassword, actualPrivateKeyPassword, idpUrlResource, keystoreResource, samlBinding);

                    clientInstantiationFuture.complete(reinitedSaml2Client);
                  } catch (MalformedURLException e) {
                    clientInstantiationFuture.fail(e);
                  }
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
              Buffer keystoreBytes = resultHandler.result();
              ByteArrayResource keystoreResource = new ByteArrayResource(keystoreBytes.getBytes());
              try {
                UrlResource idpUrlResource = new UrlResource(idpUrl);
                Client saml2Client = get(okapiUrl, tenantId, keystorePassword, privateKeyPassword, idpUrlResource, keystoreResource, samlBinding);

                clientInstantiationFuture.complete(saml2Client);
              } catch (MalformedURLException e) {
                clientInstantiationFuture.fail(e);
              }
            }
          });
      }
      return clientInstantiationFuture.future();
    });
  }
  
  public static void forceReinit() {
    // Clear the caches...
    tenantCache.clear();
  }
  
  public static void forceReinit(final String tenant) {
    // Clear the cache for the single tenant...
    tenantCache.remove(tenant);
  }
  
  public static Future<Client> get ( final RoutingContext routingContext, final boolean generateMissingKeyStore, final boolean reinitialize ) {
       
    // Override this value if we are testing.
    
    // Get the okapi tenant header.
    final String tenant = OkapiHelper.okapiHeaders(routingContext).getTenant();
    
    Future<Client> future;
    if (!reinitialize) {
      future = routingContext.get(CACHE_KEY);
      if (future != null) {
        log.debug("Returning client from request cache");
        return future;
      }
      
      // Not in the request. Check if we already have 1 in the tenant cache.
      future = tenantCache.get(tenant);
      if (future != null) {
        if (future.succeeded()) {
          // clear cache to allow cleanup.
          log.debug("Returning client from tenant cache");
          return future;
        }
        
        // Cleanup.
        tenantCache.remove(tenant);
      }
    }
    
    // Else create and cache in the request, and the tenant cache.
    log.debug("Creating new client");
    future = createClient( routingContext, generateMissingKeyStore );
    
    routingContext.put( CACHE_KEY, future );
    tenantCache.put( tenant, future );
    return future;
  }
    
  private static Client get(String okapiUrl, String tenantId, SAML2Configuration cfg, String samlBinding) {
    
    // Default lifetitme.
    cfg.setMaximumAuthenticationLifetime(18000);
    
    final boolean mock = Boolean.parseBoolean(System.getProperty("mock.httpclient")); // TODO: THIS SUCKS!!!
    cfg.setAuthnRequestBindingType(
      "REDIRECT".equalsIgnoreCase(samlBinding) ? 
        SAMLConstants.SAML2_REDIRECT_BINDING_URI : 
          SAMLConstants.SAML2_POST_BINDING_URI); // POST is the default
    
    Client saml2Client = mock ? new MockClient(cfg) : new Client(cfg);
    saml2Client.setName(tenantId);
    saml2Client.setCallbackUrl(buildCallbackUrl(okapiUrl, tenantId));
    saml2Client.setRedirectionActionBuilder(new JsonReponseSaml2RedirectActionBuilder(saml2Client));
    saml2Client.setStateGenerator(new SAML2StateGenerator(saml2Client));

    return saml2Client;
  }


  private static Client get(String okapiUrl, String tenantId, String idpUrl, String keystorePassword, String actualPrivateKeyPassword, String keystoreFileName, String samlBinding) {
    final SAML2Configuration cfg = new SAML2Configuration(
      keystoreFileName,
      keystorePassword,
      actualPrivateKeyPassword,
      idpUrl);

    return get(okapiUrl, tenantId, cfg, samlBinding);
  }


  private static Client get(String okapiUrl, String tenantId, String keystorePassword, String privateKeyPassword, UrlResource idpUrlResource, Resource keystoreResource, String samlBinding) {

    final SAML2Configuration byteArrayCfg = new SAML2Configuration(
      keystoreResource,
      keystorePassword,
      privateKeyPassword,
      idpUrlResource);

    return get(okapiUrl, tenantId, byteArrayCfg, samlBinding);
  }

  /**
   * Store KeyStore (as Base64 string), KeyStorePassword and PrivateKeyPassword in mod-configuration,
   * complete returned future with original file bytes.
   */
  private static Future<Buffer> storeKeystore(final RoutingContext routingContext, final String keystoreFileName, final String keystorePassword, final String privateKeyPassword) {

    Promise<Buffer> future = Promise.promise();
    final Vertx vertx = routingContext.vertx();
    
    // read generated jks file
    vertx.fileSystem().readFile(keystoreFileName, fileResult -> {
      if (fileResult.failed()) {
        future.fail(fileResult.cause());
      } else {
        final byte[] rawBytes = fileResult.result().getBytes();

        // base64 encode
        vertx.executeBlocking((Promise<Buffer> blockingFuture) -> {
          Buffer encodedBytes = Buffer.buffer(Base64.getEncoder().encode(rawBytes));
          blockingFuture.complete(encodedBytes);
        }, resultHandler -> {
          Buffer encodedBytes = resultHandler.result();
          
          // Store in mod-configuration with passwords, wait for all operations to finish.
          ModuleConfig.get(routingContext).onComplete((AsyncResult<ModuleConfig> configRes) -> {
            ModuleConfig config = configRes.result();
            CompositeFuture.all(
              config.updateEntry(Config.KEYSTORE_FILE, encodedBytes.toString(StandardCharsets.UTF_8)),
              config.updateEntry(Config.KEYSTORE_PASSWORD, keystorePassword),
              config.updateEntry(Config.KEYSTORE_PRIVATEKEY_PASSWORD, privateKeyPassword),
              config.updateEntry(Config.METADATA_INVALIDATED, "true") // if keystore modified, current metadata is invalid.
              
            ).onComplete(allConfigurationsStoredHandler -> {
              
              // We still attempt the delete no matter what.
              vertx.fileSystem().delete(keystoreFileName, deleteResult -> {
                Throwable failureCause = null;
                
                // If the initial operation failed then fail with that cause.
                if (allConfigurationsStoredHandler.failed()) {
                  failureCause = allConfigurationsStoredHandler.cause();
                  log.error ("Error storing configuration", failureCause);
                }
                
                // We should log the error with delete too, to prevent it from
                // being lost if the storage op fails.
                if (deleteResult.failed()) {
                  Throwable deleteFailure = deleteResult.cause();
                  log.error ("Error deleteing keystore file", deleteFailure);
                  if (failureCause == null) failureCause = deleteFailure;
                }
  
                // Finally we should succeed or fail the future correctly.
                if (failureCause == null) {
                  future.complete(Buffer.buffer(rawBytes));
                } else {
                  future.fail(failureCause);
                }
              });
            });
          });
        });
      }
    });

    return future.future();

  }
  
  private Client() {}

  private Client( SAML2Configuration cfg ) {
    super(cfg);
  }
  
  @Override
  protected void initServiceProviderMetadataResolver() {
    this.spMetadataResolver = new ExtendedSAML2ServiceProviderMetadataResolver(this.configuration,
        computeFinalCallbackUrl(null),
        this.credentialProvider);
    this.spMetadataResolver.resolve();
  }
}
