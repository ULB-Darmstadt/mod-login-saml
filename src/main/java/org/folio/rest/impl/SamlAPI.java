package org.folio.rest.impl;

import static io.vertx.core.http.HttpHeaders.*;
import static org.folio.sso.saml.Constants.COOKIE_RELAY_STATE;
import static org.folio.sso.saml.Constants.QUERY_PARAM_CSRF_TOKEN;
import static org.folio.sso.saml.Constants.Config.INST_ID;
import static org.folio.sso.saml.Constants.Config.PATRON_GRP;
import static org.folio.sso.saml.Constants.Config.SELECTED_IDPS;
import static org.folio.util.APIUtils.blockingRespondWith;
import static org.folio.util.APIUtils.respondWith;
import static org.pac4j.saml.state.SAML2StateGenerator.SAML_RELAY_STATE_ATTRIBUTE;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.NewCookie;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.*;
import org.folio.rest.jaxrs.resource.Saml;
import org.folio.rest.jaxrs.resource.Saml.PostSamlCallbackResponse.HeadersFor302;
import org.folio.services.IdpMetadataService;
import org.folio.services.Services;
import org.folio.services.UserService;
import org.folio.session.NoopSession;
import org.folio.sso.saml.Client;
import org.folio.sso.saml.Constants.Config;
import org.folio.sso.saml.ModuleConfig;
import org.folio.sso.saml.metadata.DiscoAwareServiceProviderMetadataResolver;
import org.folio.sso.saml.metadata.FederationIdentityProviderMetadataResolver;
import org.folio.util.*;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.RedirectionAction;
import org.pac4j.core.profile.CommonProfile;
import org.pac4j.saml.config.SAML2Configuration;
import org.pac4j.saml.credentials.SAML2Credentials;
import org.pac4j.vertx.VertxWebContext;

import io.vertx.core.*;
import io.vertx.core.http.Cookie;
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

  /**
   * Check that client can be loaded, SAML-Login button can be displayed.
   */
  @Override
  public void getSamlCheck(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    respondWith(asyncResultHandler, handler -> {
      Client.get(routingContext, false, false)
        .onComplete(samlClientHandler -> {
          handler.complete(GetSamlCheckResponse.respond200WithApplicationJson(new SamlCheck().withActive(!samlClientHandler.failed())));
        });
    });
  }

  @Override
  public void postSamlLogin(SamlLoginRequest requestEntity, RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
      
    respondWith(asyncResultHandler, response -> {
      
      final String stripesUrl = requestEntity.getStripesUrl();

      final UriBuilder uri = UriBuilder.fromUri(stripesUrl)
        .queryParam(QUERY_PARAM_CSRF_TOKEN, UUID.randomUUID().toString());
      
      String relayState = uri.build().toASCIIString();
      Cookie relayStateCookie = Cookie.cookie(COOKIE_RELAY_STATE, relayState)
          .setPath("/").setHttpOnly(true).setSecure(true);
      routingContext.addCookie(relayStateCookie);
  
      // register non-persistent session (this request only) to overWrite relayState
      Session session = new SharedDataSessionImpl(new PRNG(vertxContext.owner()));
      session.put(SAML_RELAY_STATE_ATTRIBUTE, relayState);
      routingContext.setSession(session);
  
      Client.get(routingContext, false, false) // do not allow login, if config is missing
        .map(samlClient -> {
          final RedirectionAction redirectionAction = samlClient
            .getRedirectionAction(VertxUtils.createWebContext(routingContext))
            .orElse(null);
          
          if (! (redirectionAction instanceof OkAction)) {
            throw new IllegalStateException("redirectionAction must be OkAction: " + redirectionAction);
          }
          
          String responseJsonString = ((OkAction) redirectionAction).getContent();
          SamlLogin dto = Json.decodeValue(responseJsonString, SamlLogin.class);
          routingContext.response().headers().clear(); // saml2Client sets Content-Type: text/html header
          addCredentialsAndOriginHeaders(routingContext);
          return (Response)PostSamlLoginResponse.respond200WithApplicationJson(dto);
        })
        
        .recover(throwable -> {
          if (throwable instanceof HttpAction) {
            return Future.succeededFuture(HttpActionMapper.toResponse((HttpAction) throwable));
          }
          return Future.failedFuture(throwable);
        })
        
        .onComplete(response);
    });
  }

  @Override
  public void postSamlCallback(final RoutingContext routingContext, final Map<String, String> okapiHeaders,
      final Handler<AsyncResult<Response>> asyncResultHandler, final Context vertxContext) {

    respondWith(asyncResultHandler, response -> {
      registerFakeSession(routingContext);
      
      // Form parameters "RelayState" is not part webContext.
      final String relayState = routingContext.request().getFormAttribute("RelayState");
      final URI relayStateUrl;
      try {
        relayStateUrl = new URI(relayState);
      } catch (URISyntaxException e1) {
        response.complete(PostSamlCallbackResponse.respond400WithTextPlain("Invalid relay state url: " + relayState));
        return;
      }
      
      Cookie relayStateCookie = routingContext.getCookie(COOKIE_RELAY_STATE);
      if (relayStateCookie == null || !relayState.contentEquals(relayStateCookie.getValue())) {
        response.complete(PostSamlCallbackResponse.respond403WithTextPlain("CSRF attempt detected"));
        return;
      }
      
      final URI stripesBaseUrl = UrlUtil.parseBaseUrl(relayStateUrl);
      
      // Grab the ModuleConfig and the Saml client 
      CompositeFuture.all(
        ModuleConfig.get(routingContext),
        Client.get(routingContext, false, false)
        
      ).compose(configAndClient -> {
        
        final ModuleConfig config = (ModuleConfig)configAndClient.resultAt(0);
        final Client client = (Client)configAndClient.resultAt(1);

        final VertxWebContext webContext = VertxUtils.createWebContext(routingContext);
        final String targetEntityID = client.getContextProvider().buildContext(webContext).getSAMLPeerEntityContext().getEntityId();
        final SAML2Credentials credentials = client.getCredentials(webContext).orElseThrow();
        final String issuer = credentials.getIssuerId();
        
        
        // Issuer isn't mandatory in a response. We should fail if it's present and doesn't match 
        if (issuer != null && !targetEntityID.equals(issuer)) {
          return Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("Issuer and IDP from context do not match"));
        }
        
        final String userPropertyName = config.getUserProperty() == null ? "externalSystemId" : config.getUserProperty();
        final String samlAttributeName = config.getSamlAttribute() == null ? "UserID" : config.getSamlAttribute();

        // Get SAML Attributes.
        List<?> samlAttributeList = (List<?>) credentials.getUserProfile().extractAttributeValues(samlAttributeName);
        if (samlAttributeList == null || samlAttributeList.isEmpty()) {
          return Future.succeededFuture(PostSamlCallbackResponse.respond400WithTextPlain("SAML attribute doesn't exist: " + samlAttributeName));
        }
        
        final String samlAttributeValue = samlAttributeList.get(0).toString();

        // Grab a proxy to talk to a user service implementation.
        final UserService userService = Services.proxyFor(vertxContext.owner(), UserService.class);
        
        return userService.findByAttribute(userPropertyName, samlAttributeValue, okapiHeaders)
          .compose (resultObject -> {
            final int recordCount = resultObject.getInteger("totalRecords");
            
            switch (recordCount) {
              case 0:
                // No matching user was found. If we shouldn't create when missing then we should log and return.
                if (!"true".equalsIgnoreCase(config.getUserCreateMissing())) {
                  String message = "No user found by " + userPropertyName + " == " + samlAttributeValue;
                  log.warn(message);
                  return Future.succeededFuture((Response)PostSamlCallbackResponse.respond400WithTextPlain(message));
                }
                
                CommonProfile profile =  credentials.getUserProfile();
                JsonObject userData = UserService.createUserJSON(profile, config);
    
                // Also add the property which we are joining on.
                userData.put(userPropertyName, samlAttributeValue);
    
                // Attempt to create the missing user.
                return userService
                  .create(userData, okapiHeaders)
                  .compose(createdUser -> userService.getToken(userData, okapiHeaders)
                      .compose(token -> respondWithToken(stripesBaseUrl, relayStateUrl, token))
                  );
                // break;
                
              case 1:
                // 1 user found! Grab them and then grab a token.
                final JsonObject userObject = resultObject.getJsonArray("users").getJsonObject(0);
                return userService.getToken(userObject, okapiHeaders)
                  .compose(token -> respondWithToken(stripesBaseUrl, relayStateUrl, token));
                // break;
               
              default:
                // < 0 results, shouldn't ever happen... but lets be good citizen.
                final String message = recordCount > 1 ? "More than one user record found!" :
                  String.format("Invalid count of %d returned from lookup" + recordCount);
                
                return Future.succeededFuture((Response)PostSamlCallbackResponse.respond400WithTextPlain(message));   
            }
          });
      })
      .onComplete(response);
    });
  }

  @Override
  public void getSamlRegenerate(final RoutingContext routingContext, final Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    blockingRespondWith(vertxContext, asyncResultHandler, (Promise<Response> response) -> {
      
      CompositeFuture.all(ModuleConfig.get(routingContext), regenerateSaml2Config(routingContext))
        .compose(configAndMetadata -> {
          final ModuleConfig config = configAndMetadata.resultAt(0);
          final String metadata = configAndMetadata.resultAt(1);
          
          // Only update once both parts of the Composite have succeeded.
          return config.updateEntry(Config.METADATA_INVALIDATED, "false")
            .compose(_void -> {
              return Base64Util.encode(metadata)
                .map(base64Result -> {
                  SamlRegenerateResponse responseEntity = new SamlRegenerateResponse()
                      .withFileContent(base64Result.toString(StandardCharsets.UTF_8));
                  return (Response)GetSamlRegenerateResponse.respond200WithApplicationJson(responseEntity);
                });
            });
        })
      .onComplete(response);
      
    });
  }
  
  private static Future<Response> respondWithToken( @NotNull final URI allowedOrigin, @NotNull final URI returUrl, @NotNull final String token ) {
    final String location = UriBuilder.fromUri(allowedOrigin)
        .path("sso-landing")
        .queryParam("ssoToken", token)
        .queryParam("fwd", returUrl.getPath())
        .build()
        .toString();

      final String cookie = new NewCookie("ssoToken", token, "", returUrl.getHost(), "", 3600, false).toString();

      HeadersFor302 headers302 = PostSamlCallbackResponse.headersFor302().withSetCookie(cookie).withXOkapiToken(token).withLocation(location);
      return Future.succeededFuture( PostSamlCallbackResponse.respond302(headers302) );
  }

  @Override
  public void getSamlConfiguration(RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {

    respondWith(asyncResultHandler, (Promise<Response> response) -> {
      
      ModuleConfig.get(rc)
        .map(configurationResult -> (Response)GetSamlConfigurationResponse.respond200WithApplicationJson(configurationResult.getSamlConfig()))
        .onComplete(response);
    });
  }


  @Override
  public void putSamlConfiguration(SamlConfigRequest updatedConfig, RoutingContext rc, Map<String, String> okapiHeaders, Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    
    respondWith(asyncResultHandler, response -> {
      
      ModuleConfig.get(rc)
        .compose(config -> {
          final SamlDefaultUser sdu = updatedConfig.getSamlDefaultUser();
          final HomeInstitution hi = updatedConfig.getHomeInstitution();
          
          @SuppressWarnings("rawtypes")
          final List<Future> futures = new ArrayList<>(Arrays.asList(new Future[] {

            config.updateEntry(Config.VERSION_PROPERTY,
                Optional.ofNullable(updatedConfig.getVersion()).map(val -> (val + "")).orElse(null)),

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
            config.updateEntry(Config.DU_UN_ATT, sdu == null ? null : sdu.getUsernameAttribute()),
            
            config.updateEntry(Config.HI_ID, hi == null ? null : hi.getId()),
            config.updateEntry(Config.HI_PATRON_GRP, hi == null ? null : hi.getPatronGroup())
            
          }));
          
          // Also add the futures for the configured IDPs
          List<HomeInstitution> providers = updatedConfig.getSelectedIdentityProviders();
          for (int i=0; i<providers.size(); i++) {
            final HomeInstitution sidp = providers.get(i);
            final String prefix = String.format("%s[%d]", SELECTED_IDPS, i);
            
            final String instId = prefix + INST_ID;
            final String patronGrp = prefix + PATRON_GRP; 
            
            futures.add(config.updateEntry(instId, sidp.getId()));
            futures.add(config.updateEntry(patronGrp, sidp.getPatronGroup()));
          }
          
          return CompositeFuture.all(futures)
            .map(allUpdates -> (Response)PutSamlConfigurationResponse.respond200WithApplicationJson(config.getSamlConfig()));
        })
        
      .onComplete(response);
    });
  }

  @Override
  public void getSamlValidate(final SamlValidateGetType type, final String value, final RoutingContext routingContext, final Map<String, String> okapiHeaders, final Handler<AsyncResult<Response>> asyncResultHandler, final Context vertxContext) {

    respondWith(asyncResultHandler, response -> {
      
      // Bail early.
      if (SamlValidateGetType.IDPURL != type) {
        response.complete(GetSamlValidateResponse.respond500WithTextPlain(type != null ? "unknown type: " + type.toString() : "type not supplied"));
        return;
      }
      
      final List<String> langs = routingContext.acceptableLanguages().stream().map( langHeader -> {
        return langHeader.rawValue();
      }).collect(Collectors.toUnmodifiableList());
      
      // The language header also affects what we supply in the root of the JSON so we should flag that
      // to anything that might cache the results of this endpoint.
      Utils.appendToMapIfAbsent(routingContext.response().headers(), VARY, ",", ACCEPT_LANGUAGE);
      
      IdpMetadataService service = Services.proxyFor(vertxContext.owner(), IdpMetadataService.class);
      service.parse(value, langs)
        .map(validateResponse -> (Response)GetSamlValidateResponse.respond200WithApplicationJson(validateResponse))
        .onComplete(response);
    });
  }

  private Future<String> regenerateSaml2Config(final RoutingContext routingContext) {
    return Client.get(routingContext, false, true)
      .compose(saml2Client -> {
        return ErrorHandlingUtil.checkedFuture((Promise<String> stringHandler) -> {
              
          SAML2Configuration cfg = saml2Client.getConfiguration();
          
          // force metadata generation then init.
          cfg.setForceServiceProviderMetadataGeneration(true);
          saml2Client.init();
          cfg.setForceServiceProviderMetadataGeneration(false);
  
          stringHandler.complete(saml2Client.getServiceProviderMetadataResolver().getMetadata());
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

  @Override
  public void optionsSamlLogin(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptionsForPost(routingContext);
  }

  @Override
  public void optionsSamlCallback(RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    handleOptionsForPost(routingContext);
  }

  private void handleOptionsForPost(RoutingContext routingContext) {
    HttpServerRequest request = routingContext.request();
    HttpServerResponse response = routingContext.response();
    String origin = request.headers().get(ORIGIN);
    if (isInvalidOrigin(origin)) {
      response.setStatusCode(400).setStatusMessage("Missing/Invalid origin header").end();
      return;
    }
    response.putHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
    response.putHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST");
    
    response.putHeader(ACCESS_CONTROL_ALLOW_ORIGIN, origin);
    Utils.appendToMapIfAbsent(response.headers(), VARY, ",", ORIGIN);
    
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

  @Override
  public void getSamlMetadata (RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> responseHandler, Context vertxContext) {
    blockingRespondWith(vertxContext, responseHandler, blockingCode -> {
      Client.get(routingContext)
        .compose(client -> {
          return ErrorHandlingUtil.checkedFuture((Promise<String> handler) -> {
            final DiscoAwareServiceProviderMetadataResolver provider = 
                (DiscoAwareServiceProviderMetadataResolver) client.getServiceProviderMetadataResolver();
            
            handler.complete(provider.getMetadata());
          });
        })
        .map(xmlString -> (Response)GetSamlMetadataResponse.respond200WithApplicationXml(xmlString))
        .onComplete(blockingCode);
    });
  }

  @Override
  public void getSamlDiscoInit (String entityID,
      SamlDiscoInitGetReturnIDParam returnIDParam, String policy,
      boolean isPassive, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    // TODO Implement!
    respondWith(asyncResultHandler, handler -> {
      
      handler.complete(GetSamlDiscoInitResponse.respond403WithTextPlain("OK"));
      
    });
  }

  @Override
  public void getSamlMetadataIdpsAllowed (RoutingContext routingContext, Map<String, String> okapiHeaders,
      Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) {
    
    // Off the event loop.
    APIUtils.blockingRespondWith(vertxContext, asyncResultHandler, blockingCode -> {
      
      final List<String> langs = routingContext.acceptableLanguages().stream().map( langHeader -> {
        return langHeader.rawValue();
      }).collect(Collectors.toUnmodifiableList());
      
      // The language header also affects what we supply in the root of the JSON so we should flag that
      // to anything that might cache the results of this endpoint.
      Utils.appendToMapIfAbsent(routingContext.response().headers(), VARY, ",", ACCEPT_LANGUAGE);
      
      Client.get(routingContext)
        .map(client -> {
          final FederationIdentityProviderMetadataResolver provider = 
              (FederationIdentityProviderMetadataResolver) client.getIdentityProviderMetadataResolver();
          
          return (Response)GetSamlMetadataIdpsAllowedResponse.respond200WithApplicationJson(
              provider.getKnownIDPs(langs));
        })
        .onComplete(blockingCode);
    });
  }
}
