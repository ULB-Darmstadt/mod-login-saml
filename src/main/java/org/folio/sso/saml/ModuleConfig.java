package org.folio.sso.saml;

import static org.folio.sso.saml.Constants.MODULE_NAME;

import java.net.URI;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.SamlConfig;
import org.folio.rest.jaxrs.model.SamlDefaultUser;
import org.folio.sso.saml.Constants.Config;
import static org.folio.util.ErrorHandlingUtil.*;
import org.folio.util.HttpUtils;
import org.folio.util.OkapiHelper;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Module Config Object
 *
 * @author Steve Osguthorpe<steve.osguthorpe@k-int.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleConfig implements Configuration {

  private static final Logger log = LogManager.getLogger(ModuleConfig.class);
  private static final String CACHE_KEY = "MODULE_CONFIG";
  private final Set<String> invalidating_keys;
  
  private final Map<String, String> config_ids = new HashMap<String,String>();

  @JsonAnySetter()
  protected final Map<String, String> config = new HashMap<String,String>();

  @JsonAnyGetter()
  public Map<String, String> getConfig() {
    return config;
  }
  
  private final OkapiHeaders okapiHeaders;
  
  private ModuleConfig( final OkapiHeaders okapiHeaders ) {
    this.okapiHeaders = okapiHeaders;
    this.invalidating_keys = new HashSet<String>();
    this.invalidating_keys.add(Config.IDP_URL);
    this.invalidating_keys.add(Config.OKAPI_URL);
  }
  
  public static ModuleConfig fromModConfigJson ( final OkapiHeaders okapiHeaders, final JsonArray mcjson ) {
    final ModuleConfig mc = new ModuleConfig(okapiHeaders);
    for (Object entry : mcjson) {
      
      // Add each entry.
      mc.updateMapsForJsonEntry((JsonObject) entry);
    }
    return mc;
  }
  
  private void updateMapsForJsonEntry (JsonObject jsonEntry) {
    final String code = jsonEntry.getString("code");
    final String value = jsonEntry.getString("value");
    final String id = jsonEntry.getString("id");
    this.config.put(code, value);
    if (id != null) this.config_ids.put(code, id);
  }
  
  public static Future<ModuleConfig> get ( RoutingContext routingContext ) {
    
    Future<ModuleConfig> future = routingContext.get(CACHE_KEY);
    if (future != null) {
      log.debug("Returning config from request cache");
      return future;
    }
    
    // Else create and cache in the request.
    final Promise<ModuleConfig> promise = Promise.promise();
    future = promise.future();
    routingContext.put(CACHE_KEY, future);
    
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext); 

    String query = "(module==" + MODULE_NAME + " AND configName==" + Config.CONFIG_NAME + ")";

    handleThrowables(promise, () -> {
    
      String encodedQuery = URLEncoder.encode(query, "UTF-8");
      final String theUrl = OkapiHelper.toOkapiUrl(okapiHeaders.getUrl(), Config.ENDPOINT_ENTRIES + "?limit=10000&query=" + encodedQuery); // this is ugly :/
      WebClientFactory.getWebClient()
        .getAbs(theUrl)
        .putHeaders(okapiHeaders.securedInteropHeaders())
      .send()
      
      .onSuccess(response -> {
        handleThrowables(promise, () -> {
          assert2xx(response, "Cannot get configuration data");
        
          JsonObject responseBody = response.bodyAsJsonObject();
          JsonArray configs = responseBody.getJsonArray("configs");
          promise.complete(fromModConfigJson(okapiHeaders, configs));
        });
      })
      
      .onFailure(throwable -> {
        log.warn("Cannot get configuration data: {}", throwable.getMessage());
        promise.fail(throwable);
      });
      
    });
    return future;
  }
  
  @Override
  public String getIdpUrl () {
    return config.get(Config.IDP_URL);
  }

  @Override
  public String getKeystore () {
    return config.get(Config.KEYSTORE_FILE);
  }

  @Override
  public String getKeystorePassword () {
    return config.get(Config.KEYSTORE_PASSWORD);
  }

  @Override
  public String getMetadataInvalidated () {
    return config.get(Config.METADATA_INVALIDATED);
  }

  @Override
  public String getOkapiUrl () {
    return config.get(Config.OKAPI_URL);
  }

  @Override
  public String getPrivateKeyPassword () {
    return config.get(Config.KEYSTORE_PRIVATEKEY_PASSWORD);
  }

  @Override
  public String getSamlAttribute () {
    return config.get(Config.SAML_ATTRIBUTE);
  }

  @Override
  public String getSamlBinding () {
    return config.get(Config.SAML_BINDING);
  }

  @Override
  public String getUserCreateMissing () {
    return config.get(Config.USER_CREATE_MISSING);
  }

  @Override
  public boolean hasDefaultUserData () {
    boolean defaultUser = false;
    Iterator<String> keys = this.config.keySet().iterator();
    while (!defaultUser && (keys.hasNext())) {
      defaultUser = keys.next().startsWith(Config.DEFAULT_USER + ".");
    }
    return defaultUser;
  }

  @Override
  public String getUserDefaultEmailAttribute () {
    return config.get(Config.DU_EMAIL_ATT);
  }

  @Override
  public String getUserDefaultFirstNameAttribute () {
    return config.get(Config.DU_FIRST_NM_ATT);
  }

  @Override
  public String getUserDefaultFirstNameDefault () {
    return config.get(Config.DU_FIRST_NM_DEFAULT);
  }

  @Override
  public String getUserDefaultLastNameAttribute () {
    return config.get(Config.DU_LAST_NM_ATT);
  }

  @Override
  public String getUserDefaultLastNameDefault () {
    return config.get(Config.DU_LAST_NM_DEFAULT);
  }

  @Override
  public String getUserDefaultPatronGroup () {
    return config.get(Config.DU_PATRON_GRP);
  }

  @Override
  public String getUserDefaultUsernameAttribute () {
    return config.get(Config.DU_UN_ATT);
  }

  @Override
  public String getUserProperty () {
    return config.get(Config.USER_PROPERTY);
  }
  
  /**
   * Send an entry to the configuration module if it's value has changed.
   * Return a future that completes to true if the configuration 
   * 
   * @param code The Code key for the config entry
   * @param value The value of the config entry
   * @return A Future that indicates the suuccess or failure.
   */
  public Future<Void> updateEntry(final String code, final String value) {

    
    Assert.hasText(code, "config entry CODE is mandatory");
    
    // Grab the existing one from here...
    final String existing = this.config.get(code);
    if (existing == null) {
      if (value == null) {
        // Do nothing, but return successfully.
        return Future.succeededFuture();
      }
    } else if (existing.equals(value)) {
      // Do nothing, but return successfully.
      return Future.succeededFuture();
    }
     
    final Promise<Void> result = Promise.promise();
    try {
      // Add/Update the value.
      final String configId = this.config_ids.get(code);
  
      final JsonObject requestBody = new JsonObject()
        .put("module", MODULE_NAME)
        .put("configName", Config.CONFIG_NAME)
        .put("code", code)
        .put("value", value);
  
      // decide to POST or PUT
  
      // not existing -> POST, existing->PUT
      final HttpMethod httpMethod = configId == null ? HttpMethod.POST : HttpMethod.PUT;
      final String endpoint = Config.ENDPOINT_ENTRIES + (configId == null ? "" : "/" + configId);

      WebClientFactory.getWebClient()
        .requestAbs(httpMethod, OkapiHelper.toOkapiUrl(okapiHeaders.getUrl(), endpoint))
        .putHeaders(okapiHeaders.securedInteropHeaders())
      .sendJsonObject(requestBody)
      
      .onSuccess(response -> {
        try {
          if ( !HttpUtils.isSuccess(response) ) {
            log.error("Cannot {} configuration entry '{}' with value '{}': {}", httpMethod.toString(), code, value, response.statusMessage());
            result.fail(response.statusMessage());
            return;
          }
          
          final int respCode = response.statusCode();            
          if ((httpMethod.equals(HttpMethod.POST) && respCode == 201)
            || (httpMethod.equals(HttpMethod.PUT) && respCode == 204)) {
            
            // Update the internal references too.
            
            if (configId == null) {
              JsonObject resp = response.bodyAsJsonObject();
              if (resp != null) {
                requestBody.put("id", resp.getString("id"));
              }
            } else {
              requestBody.put("id", configId);
            }
            
            updateMapsForJsonEntry(requestBody);
            
            // We may need to also invalidate the metadata.
            if (this.invalidating_keys.contains(code)) {
              
              // Invalidate also by recursively calling this method. And use
              // That result for the success.
              updateEntry(Config.METADATA_INVALIDATED, "true")
                .onSuccess( h -> {
                  Client.forceReinit(okapiHeaders.getTenant());
                  result.complete();
                })
                .onFailure(t -> {
                  result.fail(t);
                });
            } else {
              
              // Otherwise, we're done.
              result.complete();
            }
          } else {
            result.fail(
              String.format(
                "Unknown response code %d, with message '%s' for config %s operation",
                response.statusCode(),
                response.statusMessage(),
                httpMethod.name())
            );
          }
        } catch (Exception e) {
          log.error("Cannot set configuration data: {}", e.getMessage());
          result.fail(e);
        }
      })
      
      .onFailure(throwable -> {
        log.error("Cannot set configuration data: {}", throwable.getMessage());
        result.fail(throwable);
      });
    } catch (Throwable t) {
      log.error("Cannot set configuration data: {}", t.getMessage());
      result.fail(t);
    }


    return result.future();
  }

  /**
   * Converts to the RMB managed model for the DefaultUser object.
   */
  public SamlDefaultUser getSamlDefaultUser() {

    if (!hasDefaultUserData()) return null;

    SamlDefaultUser defaultUser = new SamlDefaultUser()
        .withEmailAttribute(getUserDefaultEmailAttribute())
        .withFirstNameAttribute(getUserDefaultFirstNameAttribute())
        .withFirstNameDefault(getUserDefaultFirstNameDefault())
        .withLastNameAttribute(getUserDefaultLastNameAttribute())
        .withLastNameDefault(getUserDefaultLastNameDefault())
        .withPatronGroup(getUserDefaultPatronGroup())
        .withUsernameAttribute(getUserDefaultUsernameAttribute())
        ;
    return defaultUser;
  }
  
  /**
   * Converts to the RMB managed model for the Config.
   */
  public SamlConfig getSamlConfig() {
    SamlConfig samlConfig = new SamlConfig()
      .withSamlAttribute(getSamlAttribute())
      .withUserProperty(getUserProperty())
      .withMetadataInvalidated(Boolean.valueOf(getMetadataInvalidated()))
      .withUserCreateMissing(Boolean.valueOf(getUserCreateMissing()))
      .withSamlDefaultUser(getSamlDefaultUser())
    ;

    try {
      URI uri = URI.create(getOkapiUrl());
      samlConfig.setOkapiUrl(uri);
    } catch (Exception e) {
      log.debug("Okapi URI is in a bad format");
      samlConfig.setOkapiUrl(URI.create(""));
    }

    try {
      URI uri = URI.create(getIdpUrl());
      samlConfig.setIdpUrl(uri);
    } catch (Exception x) {
      samlConfig.setIdpUrl(URI.create(""));
    }

    try {
      SamlConfig.SamlBinding samlBinding = SamlConfig.SamlBinding.fromValue(getSamlBinding());
      samlConfig.setSamlBinding(samlBinding);
    } catch (Exception x) {
      samlConfig.setSamlBinding(null);
    }

    return samlConfig;
  }
}
