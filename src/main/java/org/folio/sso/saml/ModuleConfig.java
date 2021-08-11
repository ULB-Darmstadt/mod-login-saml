package org.folio.sso.saml;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Strings;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Module Config Object
 *
 * @author Steve Osguthorpe<steve.osguthorpe@k-int.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleConfig implements SamlConfiguration {

  private static final Logger log = LogManager.getLogger(ModuleConfig.class);
  private static final String CACHE_KEY = "MODULE_CONFIG";

  @JsonAnySetter()
  protected final Map<String, String> config = new ConcurrentHashMap<String,String>();

  @JsonAnyGetter()
  public Map<String, String> getConfig() {
    return config;
  }
  
  private ModuleConfig() {
    
  }
  
  public static ModuleConfig fromModConfigJson ( JsonArray mcjson ) {
    final ModuleConfig mc = new ModuleConfig();
    for (Object entry : mcjson) {
      JsonObject jsonEntry = (JsonObject) entry;
      
      // Add each entry.
      final String code = jsonEntry.getString("code");
      final String value = jsonEntry.getString("value");
      mc.config.put(code, value);
    }
    return mc;
  }
  
  public static Future<ModuleConfig> get ( RoutingContext routingContext ) {
    
    final ModuleConfig mc_cache = routingContext.get(CACHE_KEY);
    if (mc_cache != null) return Future.succeededFuture(mc_cache);
    
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext); 

    String query = "(module==" + Constants.MODULE_NAME + " AND configName==" + Constants.Config.CONFIG_NAME + ")";

    try {
      Promise<ModuleConfig> promise = Promise.promise();
      okapiHeaders.verifyInteropValues();
      
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());

      HttpClientInterface httpClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant());
      httpClient.setDefaultHeaders(headers);
      httpClient.request(Constants.Config.ENTRIES_ENDPOINT_URL + "?limit=10000&query=" + encodedQuery) // this is ugly :/
        .whenComplete((Response response, Throwable throwable) -> {
          if (Response.isSuccess(response.getCode())) {

            JsonObject responseBody = response.getBody();
            JsonArray configs = responseBody.getJsonArray("configs");
            promise.complete(fromModConfigJson(configs));
            
          } else {
            log.warn("Cannot get configuration data: {}", response.getError());
            promise.fail(response.getException());
          }
        });
      return promise.future();
    } catch (Exception e) {
      log.warn("Cannot get configuration data: {}", e.getMessage());
      return Future.failedFuture(e);
    }
  }

  public static class MissingHeaderException extends Exception {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }
  
  @Override
  public String getIdpUrl () {
    return config.get(Constants.Config.IDP_URL);
  }

  @Override
  public String getKeystore () {
    return config.get(Constants.Config.KEYSTORE_FILE);
  }

  @Override
  public String getKeystorePassword () {
    return config.get(Constants.Config.KEYSTORE_PASSWORD);
  }

  @Override
  public String getMetadataInvalidated () {
    return config.get(Constants.Config.METADATA_INVALIDATED);
  }

  @Override
  public String getOkapiUrl () {
    return config.get(Constants.Config.OKAPI_URL);
  }

  @Override
  public String getPrivateKeyPassword () {
    return config.get(Constants.Config.KEYSTORE_PRIVATEKEY_PASSWORD);
  }

  @Override
  public String getSamlAttribute () {
    return config.get(Constants.Config.SAML_ATTRIBUTE);
  }

  @Override
  public String getSamlBinding () {
    return config.get(Constants.Config.SAML_BINDING);
  }

  @Override
  public String getUserCreateMissing () {
    return config.get(Constants.Config.USER_CREATE_MISSING);
  }

  @Override
  public boolean hasDefaultUserData () {
    boolean defaultUser = false;
    Iterator<String> keys = this.config.keySet().iterator();
    while (!defaultUser && (keys.hasNext())) {
      keys.next().startsWith(Constants.Config.DEFAULT_USER);
    }
    return defaultUser;
  }

  @Override
  public String getUserDefaultEmailAttribute () {
    return config.get(Constants.Config.DU_EMAIL_ATT);
  }

  @Override
  public String getUserDefaultFirstNameAttribute () {
    return config.get(Constants.Config.DU_FIRST_NM_ATT);
  }

  @Override
  public String getUserDefaultFirstNameDefault () {
    return config.get(Constants.Config.DU_FIRST_NM_DEFAULT);
  }

  @Override
  public String getUserDefaultLastNameAttribute () {
    return config.get(Constants.Config.DU_LAST_NM_ATT);
  }

  @Override
  public String getUserDefaultLastNameDefault () {
    return config.get(Constants.Config.DU_LAST_NM_DEFAULT);
  }

  @Override
  public String getUserDefaultPatronGroup () {
    return config.get(Constants.Config.DU_PATRON_GRP);
  }

  @Override
  public String getUserDefaultUsernameAttribute () {
    return config.get(Constants.Config.DU_UN_ATT);
  }

  @Override
  public String getUserProperty () {
    return config.get(Constants.Config.USER_PROPERTY);
  }
}
