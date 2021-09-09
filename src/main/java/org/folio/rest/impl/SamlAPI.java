package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.vertx.core.http.HttpHeaders.ORIGIN;
import static io.vertx.core.http.HttpHeaders.VARY;
import static org.folio.util.FunctionalUtils.handleThrowables;
import static org.folio.util.FunctionalUtils.handleThrowablesWithResponse;
import static org.pac4j.saml.state.SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.interop.UserService;
import org.folio.rest.jaxrs.model.SamlCheck;
import org.folio.rest.jaxrs.model.SamlConfig;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.rest.jaxrs.model.SamlDefaultUser;
import org.folio.rest.jaxrs.model.SamlLogin;
import org.folio.rest.jaxrs.model.SamlLoginRequest;
import org.folio.rest.jaxrs.model.SamlRegenerateResponse;
import org.folio.rest.jaxrs.model.SamlValidateGetType;
import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.rest.jaxrs.resource.Saml;
import org.folio.rest.jaxrs.resource.Saml.PostSamlCallbackResponse.HeadersFor302;
import org.folio.session.NoopSession;
import org.folio.sso.saml.Client;
import org.folio.sso.saml.Constants.Config;
import org.folio.sso.saml.ModuleConfig;
import org.folio.util.Base64Util;
import org.folio.util.HttpActionMapper;
import org.folio.util.HttpUtils;
import org.folio.util.OkapiHelper;
import org.folio.util.UrlUtil;
import org.folio.util.VertxUtils;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.springframework.util.StringUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.impl.Utils;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.sstore.impl.SharedDataSessionImpl;
/**
 * Main entry point of module
 *
 * @author rsass
 */
public class SamlAPI implements Saml {

  private static final Logger log = LogManager.getLogger(SamlAPI.class);
  public static final String QUOTATION_MARK_CHARACTER = "\"";
  public static final String CSRF_TOKEN = "csrfToken";
  public static final String RELAY_STATE = "relayState";

  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    handleThrowablesWithResponse(asyncResultHandler, () -> {
      Client.get(routingContext, false, false)
        .onComplete(samlClientHandler -> {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(!samlClientHandler.failed()))));
      });
    });
  }

  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    handleThrowablesWithResponse(asyncResultHandler, () -> {
    
      String stripesUrl = requestEntity.getStripesUrl();
  
      // register non-persistent session (this request only) to overWrite relayState
      Session session = new SharedDataSessionImpl(new PRNG(vertxContext.owner()));
      session.put(SAML_RELAY_STATE_ATTRIBUTE, stripesUrl);
      routingContext.setSession(session);
  
      Client.get(routingContext, false, false) // do not allow login, if config is missing
        .map(saml2client -> postSamlLoginResponse(routingContext, saml2client))
        .recover(e -> {
          log.warn(e.getMessage(), e);
          return Future.succeededFuture(PostSamlLoginResponse.respond500WithTextPlain("Internal Server Error"));
        })
        .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)));
    });
  }

  private Response postSamlLoginResponse(RoutingContext routingContext, Client saml2Client) {
    try {
      RedirectionAction redirectionAction = saml2Client
          .getRedirectionAction(VertxUtils.createWebContext(routingContext))
          .orElse(null);
      if (! (redirectionAction instanceof OkAction)) {
        throw new IllegalStateException("redirectionAction must be OkAction: " + redirectionAction);
      }
      String responseJsonString = ((OkAction) redirectionAction).getContent();
      SamlLogin dto = Json.decodeValue(responseJsonString, SamlLogin.class);
      routingContext.response().headers().clear(); // saml2Client sets Content-Type: text/html header
      addCredentialsAndOriginHeaders(routingContext);
      return PostSamlLoginResponse.respond200WithApplicationJson(dto);
      
    } catch (HttpAction httpAction) {
      return HttpActionMapper.toResponse(httpAction);
    }
  }

  @Override
  public void postSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    handleThrowablesWithResponse(asyncResultHandler, () -> {
      registerFakeSession(routingContext);
  
      final VertxWebContext webContext = VertxUtils.createWebContext(routingContext);
      // Form parameters "RelayState" is not part webContext.
      final String relayState = routingContext.request().getFormAttribute("RelayState");
      URI relayStateUrl;
      try {
        relayStateUrl = new URI(relayState);
      } catch (URISyntaxException e1) {
        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("Invalid relay state url: " + relayState)));
        return;
      }
      final URI originalUrl = relayStateUrl;
      final URI stripesBaseUrl = UrlUtil.parseBaseUrl(originalUrl);
  
      
      CompositeFuture.all(
          ModuleConfig.get(routingContext),
          Client.get(routingContext, false, false)
      ).onComplete(compositeHandler -> {
        
        handleThrowablesWithResponse(asyncResultHandler, () -> {
        
          if (compositeHandler.failed()) {
            // Fail the request.
            asyncResultHandler.handle(
                Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(compositeHandler.cause().getMessage())));
            
          } else {
  
            // Grab the module config and the client.
            CompositeFuture results = compositeHandler.result();
            ModuleConfig config = (ModuleConfig)results.resultAt(0);
            Client client = (Client)results.resultAt(1);
            
            String userPropertyName = config.getUserProperty() == null ? "externalSystemId" : config.getUserProperty();
            String samlAttributeName = config.getSamlAttribute() == null ? "UserID" : config.getSamlAttribute();
    
            SAML2Credentials credentials = client.getCredentials(webContext).orElseThrow(); 
    
            // Get user id
            List<?> samlAttributeList = (List<?>) credentials.getUserProfile().getAttribute(samlAttributeName);
            if (samlAttributeList == null || samlAttributeList.isEmpty()) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("SAML attribute doesn't exist: " + samlAttributeName)));
              return;
            }
            final String samlAttributeValue = samlAttributeList.get(0).toString();
    
            final String usersCql = userPropertyName +
                "==" + '"' + samlAttributeValue + '"';
    
            final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();
    
            OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
    
            final MultiMap headers = parsedHeaders.securedInteropHeaders();
    
            // Grab the client.
            final WebClient webClient = WebClientFactory.getWebClient();
            Future<HttpResponse<Buffer>> clientResponse = webClient
              .getAbs(OkapiHelper.toOkapiUrl(parsedHeaders.getUrl(), userQuery))
              .putHeaders(headers)
              .send()
            ;
            
            clientResponse.onSuccess(response -> {
              handleThrowablesWithResponse(asyncResultHandler, () -> {
                if ( !HttpUtils.isSuccess(response) ) {
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(
                      response.statusMessage())));
                  return;
                }
              
                // Success...
                JsonObject resultObject = response.bodyAsJsonObject();
                int recordCount = resultObject.getInteger("totalRecords");
                if (recordCount > 1) {
                  asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("More than one user record found!")));
                  // Fail if the saml properties match more than 1 user.
                  return;
                } else if (recordCount == 0) {
                  // No matching user was found. If we shouldn't create when missing then we should log and return.
                  if (!"true".equalsIgnoreCase(config.getUserCreateMissing())) {
                    String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
                    log.warn(message);
                    asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain(message)));
                    return;
                  }
                  CommonProfile profile =  credentials.getUserProfile();
                  JsonObject userData = UserService.createUserJSON(profile, config);
      
                  // Also add the property which we are joining on.
                  userData.put(userPropertyName, samlAttributeValue);
      
                  // Attempt to create the missing user.
                  handleThrowablesWithResponse(asyncResultHandler, 
                    webClient
                      .post(OkapiHelper.toOkapiUrl(parsedHeaders.getUrl(), "/users"))
                      .putHeaders(headers)
                      .sendJsonObject(userData)
                      
                    .onSuccess(createResponse -> {
                      if ( !HttpUtils.isSuccess(createResponse) ) {
                        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(
                            createResponse.statusMessage())));
                        return;
                      }
                      
                      getTokenForUser(asyncResultHandler, parsedHeaders, createResponse.bodyAsJsonObject(), originalUrl, stripesBaseUrl);
                    })
                  );
                } else {
                  // 1 user found! Grab them and then grab a token.
                  final JsonObject userObject = resultObject.getJsonArray("users").getJsonObject(0);
                  getTokenForUser(asyncResultHandler, parsedHeaders, userObject, originalUrl, stripesBaseUrl);
                }

              });
              
            }).onFailure(throwable -> {
              
              if (throwable instanceof HttpAction) {
                asyncResultHandler.handle(Future.succeededFuture(HttpActionMapper.toResponse((HttpAction)throwable)));
              } else {
                String message = StringUtils.hasText(throwable.getMessage()) ? throwable.getMessage() : "Unknown error: " + throwable.getClass().getName();
                asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(message)));
              }
            });
          }
        });
      });
    });
  }

  @Override
  public void getSamlRegenerate(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    regenerateSaml2Config(routingContext)
    .onComplete(regenerationHandler -> {
      if (regenerationHandler.failed()) {
        log.warn("Cannot regenerate SAML2 metadata.", regenerationHandler.cause());
        String message =
            "Cannot regenerate SAML2 matadata. Internal error was: " + regenerationHandler.cause().getMessage();
        asyncResultHandler
        .handle(Future.succeededFuture(GetSamlRegenerateResponse.respond500WithTextPlain(message)));
      } else {

        ModuleConfig.get(routingContext).onComplete((AsyncResult<ModuleConfig> configRes) -> {
          ModuleConfig config = configRes.result();
          config.updateEntry(Config.METADATA_INVALIDATED, "false")
          .onComplete(configurationEntryStoredEvent -> {

            if (configurationEntryStoredEvent.failed()) {
              asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.respond500WithTextPlain("Cannot persist metadata invalidated flag!")));
            } else {
              String metadata = regenerationHandler.result();

              Base64Util.encode(vertxContext, metadata)
              .onComplete(base64Result -> {
                if (base64Result.failed()) {
                  String message = base64Result.cause() == null ? "" : base64Result.cause().getMessage();
                  GetSamlRegenerateResponse response = GetSamlRegenerateResponse.respond500WithTextPlain("Cannot encode file content " + message);
                  asyncResultHandler.handle(Future.succeededFuture(response));
                } else {
                  SamlRegenerateResponse responseEntity = new SamlRegenerateResponse()
                      .withFileContent(base64Result.result().toString(StandardCharsets.UTF_8));
                  asyncResultHandler.handle(Future.succeededFuture(GetSamlRegenerateResponse.respond200WithApplicationJson(responseEntity)));
                }
              });
            }
          });
        });
      }
    });
  }

  public static void getTokenForUser(Handler<AsyncResult<Response>> asyncResultHandler, OkapiHeaders parsedHeaders, JsonObject userObject, URI originalUrl, URI stripesBaseUrl) {
    try {
    
      String userId = userObject.getString("id");
      if (!userObject.getBoolean("active")) {
        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("Inactive user account!")));
      } else {     
        
        final JsonObject payload = new JsonObject().put("payload", new JsonObject().put("sub", userObject.getString("username")).put("user_id", userId));
  
        WebClientFactory.getWebClient()
          .postAbs(OkapiHelper.toOkapiUrl(parsedHeaders.getUrl(), "/token"))
          .putHeaders(parsedHeaders.securedInteropHeaders())
          .sendJsonObject(payload)
          
        .onSuccess(response -> {
          if ( !HttpUtils.isSuccess(response) ) {
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(
                response.statusMessage())));
            return;
          }
          
          String candidateAuthToken = null;
          if(response.statusCode() == 200) {
            candidateAuthToken = response.headers().get(OkapiHeaders.OKAPI_TOKEN_HEADER);
          } else {
            //mod-authtoken v2.x returns 201, with token in JSON response body
            try {
              candidateAuthToken = response.bodyAsJsonObject().getString("token");
            } catch(Exception e) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(e.getMessage())));
            }
          }
          final String authToken = candidateAuthToken;
  
          final String location = UriBuilder.fromUri(stripesBaseUrl)
              .path("sso-landing")
              .queryParam("ssoToken", authToken)
              .queryParam("fwd", originalUrl.getPath())
              .build()
              .toString();
  
          final String cookie = new NewCookie("ssoToken", authToken, "", originalUrl.getHost(), "", 3600, false).toString();
  
          HeadersFor302 headers302 = PostSamlCallbackResponse.headersFor302().withSetCookie(cookie).withXOkapiToken(authToken).withLocation(location);
          asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond302(headers302)));
        })
        .onFailure(throwable -> {
          asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(throwable.getMessage())));
        });
      }
    } catch (Throwable throwable) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(throwable.getMessage())));
    }
  }

  @Override
  public void getSamlConfiguration(RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ModuleConfig.get(rc)
    .onComplete(configurationResult -> {
      if (configurationResult.failed()) {
        log.warn("Cannot load configuration", configurationResult.cause());
        asyncResultHandler.handle(
            Future.succeededFuture(
                GetSamlConfigurationResponse.respond500WithTextPlain("Cannot get configuration")));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(
          GetSamlConfigurationResponse.respond200WithApplicationJson(configurationResult.result().getSamlConfig())));
      }
    });
  }


  @Override
  public void putSamlConfiguration(SamlConfigRequest updatedConfig, RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    ModuleConfig.get(rc).onComplete((AsyncResult<ModuleConfig> configRes) -> {
      if (configRes.failed()) {
        asyncResultHandler.handle(Future.succeededFuture(
            PutSamlConfigurationResponse.respond500WithTextPlain(configRes.cause() != null ? configRes.cause().getMessage() : "Cannot load current configuration")));
      } else {

        // Get the config
        final ModuleConfig config = configRes.result();
        // The default user is nested. Grab it.
        final SamlDefaultUser sdu = updatedConfig.getSamlDefaultUser();
        
        CompositeFuture.all(Arrays.asList(new Future[] {
            
          config.updateEntry(Config.IDP_URL, updatedConfig.getIdpUrl().toString()),
          config.updateEntry(Config.SAML_BINDING, updatedConfig.getSamlBinding().toString()),
          config.updateEntry(Config.SAML_ATTRIBUTE, updatedConfig.getSamlAttribute().toString()),
          config.updateEntry(Config.USER_PROPERTY, updatedConfig.getUserProperty().toString()),
          config.updateEntry(Config.OKAPI_URL, updatedConfig.getOkapiUrl().toString()),
          config.updateEntry(Config.USER_CREATE_MISSING, updatedConfig.getUserCreateMissing() ? "true" : "false"),
  
          config.updateEntry(Config.DU_EMAIL_ATT, sdu == null ? null : sdu.getEmailAttribute()),
          config.updateEntry(Config.DU_FIRST_NM_ATT, sdu == null ? null : sdu.getFirstNameAttribute()),
          config.updateEntry(Config.DU_FIRST_NM_DEFAULT, sdu == null ? null : sdu.getFirstNameDefault()),
          config.updateEntry(Config.DU_LAST_NM_ATT, sdu == null ? null : sdu.getLastNameAttribute()),
          config.updateEntry(Config.DU_LAST_NM_DEFAULT, sdu == null ? null : sdu.getLastNameDefault()),
          config.updateEntry(Config.DU_PATRON_GRP, sdu == null ? null : sdu.getPatronGroup()),
          config.updateEntry(Config.DU_UN_ATT, sdu == null ? null : sdu.getUsernameAttribute())
          
        })).onComplete(updateComplete -> {
          if (updateComplete.failed()) {
            
            asyncResultHandler.handle(
              Future.succeededFuture(
                PutSamlConfigurationResponse.respond500WithTextPlain(
                  updateComplete.cause() != null ? updateComplete.cause().getMessage() : "Cannot save configuration"
                )
              )
            );
          } else {
            
            // Config is updated at the same time now.
            SamlConfig dto = config.getSamlConfig();
            asyncResultHandler.handle(Future.succeededFuture(PutSamlConfigurationResponse.respond200WithApplicationJson(dto)));
          }
        });
      }
    });
  }


  @Override
  public void getSamlValidate(SamlValidateGetType type, String value, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    // Bail early.
    if (SamlValidateGetType.IDPURL != type) {
      asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond500WithTextPlain("unknown type: " + type.toString())));
      return;
    }
    
    handleThrowablesWithResponse(asyncResultHandler, UrlUtil.checkIdpUrl(value, vertxContext.owner()))
      .onSuccess(result -> {
        SamlValidateResponse response = new SamlValidateResponse();
        if (result.isSuccess()) {
          response.setValid(true);
        } else {
          response.setValid(false);
          response.setError(result.getMessage());
        }
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond200WithApplicationJson(response)));
      });
  }

  private Future<String> regenerateSaml2Config(final RoutingContext routingContext) {

    final Vertx vertx = routingContext.vertx();
    
    return Future.future(stringHandler -> {
      
      // Grab the client. Instanciates or returns from cache. 
      Client.get(routingContext, false, true)
      
      .onComplete(clientHandler -> {
        
        if (clientHandler.failed()) {
          stringHandler.fail(clientHandler.cause());
        } else {
          Client saml2Client = clientHandler.result();
          vertx.executeBlocking(blockingCode -> {
            
            handleThrowables (blockingCode, () -> {
              
              SAML2Configuration cfg = saml2Client.getConfiguration();
              
              // force metadata generation then init
              cfg.setForceServiceProviderMetadataGeneration(true);
              saml2Client.init();
              cfg.setForceServiceProviderMetadataGeneration(false);
      
              blockingCode.complete(saml2Client.getServiceProviderMetadataResolver().getMetadata());
            });
          }, stringHandler);
        }
      });
    });
  }

  /**
   * Registers a no-op session. Pac4j want to access session variables and fails if there is no session.
   *
   * @param routingContext the current routing context
   */
  private void registerFakeSession(RoutingContext routingContext) {
    routingContext.setSession(new NoopSession());
  }

  private SamlDefaultUser configExtractDefaultUser(ModuleConfig config) {

    if (!config.hasDefaultUserData()) return null;

    SamlDefaultUser defaultUser = new SamlDefaultUser()
        .withEmailAttribute(config.getUserDefaultEmailAttribute())
        .withFirstNameAttribute(config.getUserDefaultFirstNameAttribute())
        .withFirstNameDefault(config.getUserDefaultFirstNameDefault())
        .withLastNameAttribute(config.getUserDefaultLastNameAttribute())
        .withLastNameDefault(config.getUserDefaultLastNameDefault())
        .withPatronGroup(config.getUserDefaultPatronGroup())
        .withUsernameAttribute(config.getUserDefaultUsernameAttribute())
        ;
    return defaultUser;
  }

  /**
   * Converts internal {@link SamlConfiguration} object to DTO, checks illegal values
   * TODO: This may be better as a JSON view agains the config object itself.
   */
  private SamlConfig configToDto(ModuleConfig config) {
    SamlConfig samlConfig = new SamlConfig()
        .withSamlAttribute(config.getSamlAttribute())
        .withUserProperty(config.getUserProperty())
        .withMetadataInvalidated(Boolean.valueOf(config.getMetadataInvalidated()))
        .withUserCreateMissing(Boolean.valueOf(config.getUserCreateMissing()))
        .withSamlDefaultUser(configExtractDefaultUser(config))
        ;

    try {
      URI uri = URI.create(config.getOkapiUrl());
      samlConfig.setOkapiUrl(uri);
    } catch (Exception e) {
      log.debug("Okapi URI is in a bad format");
      samlConfig.setOkapiUrl(URI.create(""));
    }

    try {
      URI uri = URI.create(config.getIdpUrl());
      samlConfig.setIdpUrl(uri);
    } catch (Exception x) {
      samlConfig.setIdpUrl(URI.create(""));
    }

    try {
      SamlConfig.SamlBinding samlBinding = SamlConfig.SamlBinding.fromValue(config.getSamlBinding());
      samlConfig.setSamlBinding(samlBinding);
    } catch (Exception x) {
      samlConfig.setSamlBinding(null);
    }

    return samlConfig;
  }

  @Override
  public void optionsSamlLogin(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptions(routingContext);
  }

  @Override
  public void optionsSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptions(routingContext);
  }

  private void handleOptions(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String origin = request.headers().get(ORIGIN);
    if (isInvalidOrigin(origin)) {
      response.setStatusCode(400).setStatusMessage("Missing/Invalid origin header").end();
      return;
    }
    response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ORIGIN);
    response.putHeader(ACCESS_CONTROL_ALLOW_METHODS, request.getHeader(ACCESS_CONTROL_REQUEST_METHOD));
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ACCESS_CONTROL_REQUEST_METHOD);
    response.putHeader(ACCESS_CONTROL_ALLOW_HEADERS, request.getHeader(ACCESS_CONTROL_REQUEST_HEADERS));
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ACCESS_CONTROL_REQUEST_HEADERS);
    response.setStatusCode(204).end();
  }

  private void addCredentialsAndOriginHeaders(RoutingContext routingContext) {
    String origin = routingContext.request().headers().get(ORIGIN);
    if (isInvalidOrigin(origin)) {
      return;
    }
    HttpServerResponse response = routingContext.response();
    response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
  }

  private boolean isInvalidOrigin(String origin) {
    return origin == null || origin.isBlank() || origin.trim().contentEquals("*");
  }

}
