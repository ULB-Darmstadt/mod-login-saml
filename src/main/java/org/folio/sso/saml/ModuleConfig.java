package org.folio.sso.saml;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
public class ModuleConfig {

  private static final Logger log = LogManager.getLogger(ModuleConfig.class);
  private static final String CACHE_KEY = "MODULE_CONFIG";

  @JsonAnySetter()
  protected final Map<String, Object> config = new ConcurrentHashMap<String,Object>();

  @JsonAnyGetter()
  public Map<String, ?> getConfig() {
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
}
