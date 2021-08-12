package org.folio.sso.saml;

import static org.folio.sso.saml.Constants.*;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.OkapiHelper;
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
  }
  
  public static ModuleConfig fromModConfigJson ( final OkapiHeaders okapiHeaders, final JsonArray mcjson ) {
    final ModuleConfig mc = new ModuleConfig(okapiHeaders);
    for (Object entry : mcjson) {
      JsonObject jsonEntry = (JsonObject) entry;
      
      // Add each entry.
      final String code = jsonEntry.getString("code");
      final String value = jsonEntry.getString("value");
      final String id = jsonEntry.getString("id");
      mc.config.put(code, value);
      mc.config_ids.put(code, id);
    }
    return mc;
  }
  
  public static ModuleConfig get ( RoutingContext routingContext ) {
    return getAsync( routingContext ).result();
  }
  
  public static Future<ModuleConfig> getAsync ( RoutingContext routingContext ) {
    
    Future<ModuleConfig> future = routingContext.get(CACHE_KEY);
    if (future != null) return future;
    
    // Else create and cache in the request.
    final Promise<ModuleConfig> promise = Promise.promise();
    future = promise.future();
    routingContext.put(CACHE_KEY, future);
    
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext); 

    String query = "(module==" + MODULE_NAME + " AND configName==" + Config.CONFIG_NAME + ")";

    try {
      
      okapiHeaders.verifyInteropValues();
      
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

      HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
      httpClient.setDefaultHeaders(headers);
      httpClient.request(Config.ENTRIES_ENDPOINT + "?limit=10000&query=" + encodedQuery) // this is ugly :/
        .whenComplete((Response response, Throwable throwable) -> {
          if (Response.isSuccess(response.getCode())) {

            JsonObject responseBody = response.getBody();
            JsonArray configs = responseBody.getJsonArray("configs");
            promise.complete(fromModConfigJson(okapiHeaders, configs));
            
          } else {
            log.warn("Cannot get configuration data: {}", response.getError());
            promise.fail(response.getException());
          }
        });
    } catch (Exception e) {
      log.warn("Cannot get configuration data: {}", e.getMessage());
      promise.fail(e);
    }
    return future;
  }

  public static class MissingHeaderException extends Exception {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
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
      keys.next().startsWith(Config.DEFAULT_USER);
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
  
  public Future<Void> storeEntry(final String code, final String value) {
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
    
    // Add/Update the value.
    final String configId = this.config_ids.get(code);

    final Promise<Void> result = Promise.promise();

    final JsonObject requestBody = new JsonObject()
      .put("module", MODULE_NAME)
      .put("configName", Config.CONFIG_NAME)
      .put("code", code)
      .put("value", value);

    // decide to POST or PUT
    

    // not existing -> POST, existing->PUT
    final HttpMethod httpMethod = configId == null ? HttpMethod.POST : HttpMethod.PUT;
    final String endpoint = Config.ENTRIES_ENDPOINT + (configId == null ? "" : "/" + configId);

    Map<String, String> headers = new HashMap<>();
    headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

    try {
      HttpClientInterface storeEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant(), true);
      storeEntryClient.setDefaultHeaders(headers);
      storeEntryClient.request(httpMethod, requestBody, endpoint, null)
        .whenComplete((storeEntryResponse, throwable) -> {

          if (storeEntryResponse == null) {
            if (throwable == null) {
              result.fail("Cannot " + httpMethod.toString() + " configuration entry");
            } else {
              result.fail(throwable);
            }
          }
          // POST->201 created, PUT->204 no content
          else if ((httpMethod.equals(HttpMethod.POST) && storeEntryResponse.getCode() == 201)
            || (httpMethod.equals(HttpMethod.PUT) && storeEntryResponse.getCode() == 204)) {

            result.complete();
          } else {
            result.fail("The response status is not 'created',instead "
              + storeEntryResponse.getCode()
              + " with message  "
              + storeEntryResponse.getError());
          }

        });
    } catch (Exception ex) {
      result.fail(ex);
    }


    return result.future();
  }
}
