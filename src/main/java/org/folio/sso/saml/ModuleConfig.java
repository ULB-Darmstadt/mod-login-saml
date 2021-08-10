package org.folio.sso.saml;


import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.ConfigurationObjectMapper;
import org.folio.config.model.SamlConfiguration;
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.Response;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.util.OkapiHelper;
import org.folio.util.model.OkapiHeaders;
import org.springframework.util.Assert;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Strings;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;

/**
 * Connect to mod-configuration via Okapi
 *
 * @author rsass
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ModuleConfig {

  private static final Logger log = LogManager.getLogger(ModuleConfig.class);
  private static final String CACHE_KEY = "MODULE_CONFIG";

  public static final String MISSING_OKAPI_URL = "Missing Okapi URL";
  public static final String MISSING_TENANT = "Missing Tenant";
  public static final String MISSING_TOKEN = "Missing Token";

  @JsonAnySetter()
  protected final Map<String, Object> config = new ConcurrentHashMap<String,Object>();

  @JsonAnyGetter()
  public Map<String, ?> getConfig() {
    return config;
  }
  
  
  private ModuleConfig() {
    
  }

  protected static void verifyOkapiHeaders(OkapiHeaders okapiHeaders) throws MissingHeaderException {
    if (Strings.isNullOrEmpty(okapiHeaders.getUrl())) {
      throw new MissingHeaderException(MISSING_OKAPI_URL);
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getTenant())) {
      throw new MissingHeaderException(MISSING_TENANT);
    }
    if (Strings.isNullOrEmpty(okapiHeaders.getToken())) {
      throw new MissingHeaderException(MISSING_TOKEN);
    }
  }
  
  static Future<ModuleConfig> get ( RoutingContext routingContext ) {
    OkapiHeaders okapiHeaders = OkapiHelper.okapiHeaders(routingContext); 

    String query = "(module==" + Constants.MODULE_NAME + " AND configName==" + Constants.Config.CONFIG_NAME + ")";

    try {
      Promise<ModuleConfig> promise = Promise.promise();
      verifyOkapiHeaders(okapiHeaders);
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
            
//            promise.handle(configs.stream()
//              .filter(JsonObject.class::isInstance)
//              .map(JsonObject.class::cast)
//              .collect(
//                Collector.of(
//                  JsonObject::new,
//                  (result, entry) -> result.put(entry.getString("code"), entry.getString("value")),
//                  JsonObject::mergeIn,
//                  result -> result.mapTo(clazz)
//                )
//              )
//            );
            
            
            final ModuleConfig mc = new ModuleConfig();
            for (Object entry : configs) {
              JsonObject jsonEntry = (JsonObject) entry;
              
              // Add each entry.
              final String code = jsonEntry.getString("code");
              final String value = jsonEntry.getString("value");
              mc.config.put(code, value);
            }
            promise.complete(mc);
            
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

  public static Future<SamlConfiguration> storeEntries(OkapiHeaders headers, Map<String, String> entries) {

    Objects.requireNonNull(headers);
    Objects.requireNonNull(entries);

    Promise<SamlConfiguration> result = Promise.promise();

    // CompositeFuture.all(...) called below only accepts a list of Future (raw type)
    @SuppressWarnings("java:S3740")
    List<Future> futures = entries.entrySet().stream()
      .map(entry -> ModuleConfig.storeEntry(headers, entry.getKey(), entry.getValue()))
      .collect(Collectors.toList());

    CompositeFuture.all(futures).onComplete(compositeEvent -> {
      if (compositeEvent.succeeded()) {
        ModuleConfig.getConfiguration(headers).onComplete(newConfigHandler -> {

          if (newConfigHandler.succeeded()) {
            result.complete(newConfigHandler.result());
          } else {
            result.fail(newConfigHandler.cause());
          }

        });
      } else {
        log.warn("Cannot save configuration entries: {}", compositeEvent.cause().getMessage());
        result.fail(compositeEvent.cause());
      }
    });

    return result.future();
  }

  public static Future<Void> storeEntry(OkapiHeaders okapiHeaders, String code, String value) {
    Assert.hasText(code, "config entry CODE is mandatory");

    Promise<Void> result = Promise.promise();

    JsonObject requestBody = new JsonObject();
    requestBody
      .put("module", MODULE_NAME)
      .put("configName", CONFIG_NAME)
      .put("code", code)
      .put("value", value);

    // decide to POST or PUT
    checkEntry(okapiHeaders, code).onComplete(checkHandler -> {
      if (checkHandler.failed()) {
        result.fail(checkHandler.cause());
      } else {
        String configId = checkHandler.result();

        // not existing -> POST, existing->PUT
        HttpMethod httpMethod = configId == null ? HttpMethod.POST : HttpMethod.PUT;
        String endpoint = configId == null ? CONFIGURATIONS_ENTRIES_ENDPOINT_URL : CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "/" + configId;

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
      }
    });


    return result.future();
  }

  /**
   * Complete future with found config entry id, or null, if not found
   */
  public static Future<String> checkEntry(OkapiHeaders okapiHeaders, String code) {
    Promise<String> result = Promise.promise();

    String query = "(module==" + MODULE_NAME + " AND configName==" + CONFIG_NAME + " AND code== " + code + ")";
    try {
      verifyOkapiHeaders(okapiHeaders);
      String encodedQuery = URLEncoder.encode(query, "UTF-8");

      Map<String, String> headers = new HashMap<>();
      headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, okapiHeaders.getToken());
      HttpClientInterface checkEntryClient = HttpClientFactory.getHttpClient(okapiHeaders.getUrl(), okapiHeaders.getTenant(), true);
      checkEntryClient.setDefaultHeaders(headers);
      checkEntryClient.request(CONFIGURATIONS_ENTRIES_ENDPOINT_URL + "?query=" + encodedQuery)
        .whenComplete((checkEntryResponse, throwable) -> {
          if (checkEntryResponse.getCode() != 200) {
            result.fail("Failed to check configuration entry: " + code
              + " HTTP result was " + checkEntryResponse.getCode() + " " + checkEntryResponse.getBody().encode());
          } else {
            JsonObject entries = checkEntryResponse.getBody();
            JsonArray configs = entries.getJsonArray("configs");
            if (configs == null || configs.isEmpty()) {
              result.complete(); // null
            } else {
              JsonObject entry = configs.getJsonObject(0);
              String id = entry.getString("id");
              result.complete(id);
            }
          }
        });

    } catch (Exception exception) {
      result.fail(exception);
    }

    return result.future();
  }

  public static class MissingHeaderException extends Exception {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }
}
