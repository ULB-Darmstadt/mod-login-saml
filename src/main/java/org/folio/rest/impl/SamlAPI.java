package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS;
import static io.vertx.core.http.HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD;
import static io.vertx.core.http.HttpHeaders.ORIGIN;
import static io.vertx.core.http.HttpHeaders.VARY;
import static org.pac4j.saml.state.SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
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
import org.folio.rest.tools.client.HttpClientFactory;
import org.folio.rest.tools.client.interfaces.HttpClientInterface;
import org.folio.session.NoopSession;
import org.folio.sso.saml.Client;
import org.folio.sso.saml.Constants.Config;
import org.folio.sso.saml.ModuleConfig;
import org.folio.util.Base64Util;
import org.folio.util.HttpActionMapper;
import org.folio.util.OkapiHelper;
import org.folio.util.UrlUtil;
import org.folio.util.VertxUtils;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.UrlCheckResult;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;
import org.springframework.util.StringUtils;

import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.PRNG;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.Session;
import io.vertx.ext.web.impl.Utils;
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

    Client.get(routingContext, false, false)
      .onComplete(samlClientHandler -> {
        if (samlClientHandler.failed()) {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(false))));
        } else {
          asyncResultHandler.handle(Future.succeededFuture(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(true))));
        }
    });
  }


  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    String csrfToken = UUID.randomUUID().toString();
    String stripesUrl = requestEntity.getStripesUrl();
    String relayState = stripesUrl + (stripesUrl.indexOf('?') >= 0 ? '&' : '?') + CSRF_TOKEN + '=' + csrfToken;
    Cookie relayStateCookie = Cookie.cookie(RELAY_STATE, relayState)
        .setPath("/").setHttpOnly(true).setSecure(true);
    routingContext.addCookie(relayStateCookie);


    // register non-persistent session (this request only) to overWrite relayState
    Session session = new SharedDataSessionImpl(new PRNG(vertxContext.owner()));
    session.put(SAML_RELAY_STATE_ATTRIBUTE, relayState);
    routingContext.setSession(session);

    Client.get(routingContext, false, false) // do not allow login, if config is missing
      .map(saml2client -> postSamlLoginResponse(routingContext, saml2client))
      .recover(e -> {
        log.warn(e.getMessage(), e);
        return Future.succeededFuture(PostSamlLoginResponse.respond500WithTextPlain("Internal Server Error"));
      })
      .onSuccess(response -> asyncResultHandler.handle(Future.succeededFuture(response)));
  }

  private Response postSamlLoginResponse(RoutingContext routingContext, SAML2Client saml2Client) {
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
      
      if (compositeHandler.failed()) {
        // Fail the request.
        asyncResultHandler.handle(
            Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(compositeHandler.cause().getMessage())));
        
      } else {
        try {
          
          // Grab the module config and the client.
          CompositeFuture results = compositeHandler.result();
          ModuleConfig config = (ModuleConfig)results.resultAt(0);
          Client client = (Client)results.resultAt(1);
          
          String userPropertyName = config.getUserProperty() == null ? "externalSystemId" : config.getUserProperty();
          String samlAttributeName = config.getSamlAttribute() == null ? "UserID" : config.getSamlAttribute();
  
          SAML2Credentials credentials = client.getCredentials(webContext).get();
  
          // Get user id
          List<?> samlAttributeList = (List<?>) credentials.getUserProfile().getAttribute(samlAttributeName);
          if (samlAttributeList == null || samlAttributeList.isEmpty()) {
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("SAML attribute doesn't exist: " + samlAttributeName)));
            return;
          }
          final String samlAttributeValue = samlAttributeList.get(0).toString();
  
          final String usersCql = userPropertyName +
              "=="
              + QUOTATION_MARK_CHARACTER + samlAttributeValue + QUOTATION_MARK_CHARACTER;
  
          final String userQuery = UriBuilder.fromPath("/users").queryParam("query", usersCql).build().toString();
  
          OkapiHeaders parsedHeaders = OkapiHelper.okapiHeaders(okapiHeaders);
  
          Map<String, String> headers = new HashMap<>();
          headers.put(OkapiHeaders.OKAPI_TOKEN_HEADER, parsedHeaders.getToken());
  
          HttpClientInterface usersClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
          usersClient.setDefaultHeaders(headers);
          usersClient.request(userQuery)
          .whenComplete((userQueryResponse, ex) -> {
            if (!org.folio.rest.tools.client.Response.isSuccess(userQueryResponse.getCode())) {
              asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(userQueryResponse.getError().toString())));
            } else { // success
  
              JsonObject resultObject = userQueryResponse.getBody();
  
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
                try {
                  usersClient.request(HttpMethod.POST, userData, "/users", null).whenComplete((userResponse, tokenError) -> {
  
                    if (!org.folio.rest.tools.client.Response.isSuccess(userResponse.getCode())) {
                      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(userResponse.getError().toString())));
                      return;
                    } else {
                      getTokenForUser(asyncResultHandler, parsedHeaders, userResponse.getBody(), originalUrl, stripesBaseUrl);
                    }
  
                  });
                } catch (Exception userCreationException) {
                  asyncResultHandler.handle(Future.succeededFuture(
                      PostSamlCallbackResponse.respond500WithTextPlain(userCreationException.getMessage())
                      ));
                }
              } else {
                // 1 user found! Grab them and then grab a token.
                final JsonObject userObject = resultObject.getJsonArray("users").getJsonObject(0);
                getTokenForUser(asyncResultHandler, parsedHeaders, userObject, originalUrl, stripesBaseUrl);
              }
            }
          });
  
        } catch (HttpAction httpAction) {
          asyncResultHandler.handle(Future.succeededFuture(HttpActionMapper.toResponse(httpAction)));
        } catch (Exception ex) {
          String message = StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : "Unknown error: " + ex.getClass().getName();
          asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(message)));
        }
      }
      
//      final SamlConfiguration configuration = samlClientComposite.getConfiguration();
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
    String userId = userObject.getString("id");
    if (!userObject.getBoolean("active")) {
      asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond403WithTextPlain("Inactive user account!")));
    } else {

      // Current token to headers.
      final Map<String, String> defaultHeaders = new HashMap<>();
      defaultHeaders.put(OkapiHeaders.OKAPI_TOKEN_HEADER, parsedHeaders.getToken());

      final JsonObject payload = new JsonObject().put("payload", new JsonObject().put("sub", userObject.getString("username")).put("user_id", userId));


      HttpClientInterface tokenClient = HttpClientFactory.getHttpClient(parsedHeaders.getUrl(), parsedHeaders.getTenant());
      tokenClient.setDefaultHeaders(defaultHeaders);
      try {
        tokenClient.request(HttpMethod.POST, payload, "/token", null)
        .whenComplete((tokenResponse, tokenError) -> {
          if (!org.folio.rest.tools.client.Response.isSuccess(tokenResponse.getCode())) {
            asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(tokenResponse.getError().toString())));
          } else {
            String candidateAuthToken = null;
            if(tokenResponse.getCode() == 200) {
              candidateAuthToken = tokenResponse.getHeaders().get(OkapiHeaders.OKAPI_TOKEN_HEADER);
            } else { //mod-authtoken v2.x returns 201, with token in JSON response body
              try {
                candidateAuthToken = tokenResponse.getBody().getString("token");
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
          }
        });
      } catch (Exception httpClientEx) {
        asyncResultHandler.handle(Future.succeededFuture(PostSamlCallbackResponse.respond500WithTextPlain(httpClientEx.getMessage())));
      }
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

    Handler<AsyncResult<UrlCheckResult>> handler = hnd -> {
      if (hnd.succeeded()) {
        UrlCheckResult result = hnd.result();
        SamlValidateResponse response = new SamlValidateResponse();
        if (result.isSuccess()) {
          response.setValid(true);
        } else {
          response.setValid(false);
          response.setError(result.getMessage());
        }
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond200WithApplicationJson(response)));
      } else {
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond500WithTextPlain("unknown error")));
      }
    };

    switch (type) {
      case IDPURL:
        UrlUtil.checkIdpUrl(value, vertxContext.owner()).onComplete(handler);
        break;
      default:
        asyncResultHandler.handle(Future.succeededFuture(GetSamlValidateResponse.respond500WithTextPlain("unknown type: " + type.toString())));
    }


  }

  private Future<String> regenerateSaml2Config(RoutingContext routingContext) {

    Promise<String> result = Promise.promise();
    final Vertx vertx = routingContext.vertx();

    Client.get(routingContext, false, true)
    .onComplete(handler -> {
      if (handler.failed()) {
        result.fail(handler.cause());
      } else {
        SAML2Client saml2Client = handler.result();

        vertx.executeBlocking(blockingCode -> {
          SAML2Configuration cfg = saml2Client.getConfiguration();

          // force metadata generation then init
          cfg.setForceServiceProviderMetadataGeneration(true);
          saml2Client.init();
          cfg.setForceServiceProviderMetadataGeneration(false);

          try {
            blockingCode.complete(saml2Client.getServiceProviderMetadataResolver().getMetadata());
          } catch (Exception e) {
            blockingCode.fail(e);
          }
        }, result);
      }
    });
    return result.future();
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
