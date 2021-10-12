package org.folio.services.impl;

import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.AbstractOkapiHttpService;
import org.folio.services.Services;
import org.folio.services.TokenService;
import org.folio.services.UserService;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceException;

/**
 * @author Steve Osguthorpe
 */
public class OkapiUserService extends AbstractOkapiHttpService implements UserService {
  private static final Logger log = LogManager.getLogger(OkapiUserService.class);
  private static final String BASE_PATH = "/users";

  @Override
  public Future<JsonObject> create ( @NotNull final JsonObject userData, @NotNull final Map<String, String> headers ) {
    
    return Future.future((Promise<JsonObject> handler) -> {
      log.debug("OkapiUserService::create");
      if (log.isTraceEnabled()) log.trace("userdata: {}", userData);
      
      post(BASE_PATH, headers)
        .expect(SERVICE_SC_SUCCESS) // 2xx response only
        .sendJsonObject(userData)
        
      .map(response -> response.bodyAsJsonObject())
      .onComplete(handler);
      
    }).recover(OkapiUserService::FAIL_WITH_SERVICE_EXCEPTION);
  }

  @Override
  public Future<JsonObject> findByAttribute ( @NotNull final String attributeName, @NotNull String attributeValue, @NotNull final Map<String, String> headers ) {
        
    return Future.future((Promise<JsonObject> handler) -> {
      final String usersCql = String.format("%s==\"%s\"", attributeName, attributeValue);

      // Create the URI
      final UriBuilder userQuery = 
        UriBuilder.fromPath(BASE_PATH)
          .queryParam("query", usersCql);
      
      get(userQuery.build(), headers)
        .expect(SERVICE_SC_SUCCESS) // 2xx response only
        .send()
        
      .map(response -> response.bodyAsJsonObject())
      .onComplete(handler);
      
    }).recover(OkapiUserService::FAIL_WITH_SERVICE_EXCEPTION);
  }

  @Override
  public Future<String> getToken (@NotNull JsonObject user,
      @NotNull Map<String, String> headers) {
    
    return Future.future((Promise<String> handler) -> {
      
      // We know the okapi user object should have an active status of true for this to be valid.
      // We can check and bail early.
      if (!user.getBoolean("active")) {
        handler.handle(ServiceException.fail(403, "Inactive user account!"));
        return;
      }

      final String userId = user.getString("id");
      final String subject = user.getString("username");
      
      // Grab the token service implementation present.
      final TokenService tokenService = Services.proxyFor(Vertx.currentContext().owner(), TokenService.class);
      
      // Use the handler to propagate the result.
      tokenService.create(subject, userId, headers)
        .onComplete(handler);
      
    }).recover(OkapiUserService::FAIL_WITH_SERVICE_EXCEPTION);
  }
}
