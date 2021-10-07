package org.folio.services.impl;

import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.AbstractOkapiHttpService;
import org.folio.services.UserService;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

/**
 * @author Steve Osguthorpe
 */
public class OkapiUserService extends AbstractOkapiHttpService implements UserService {
  private static final Logger log = LogManager.getLogger(OkapiUserService.class);
  private static final String BASE_PATH = "/users";

  @Override
  public Future<JsonObject> save ( @NotNull final JsonObject user, @NotNull final Map<String, String> headers ) {
    log.debug("OkapiUserService::save {}", user);
    return doWithErrorHandling(() -> { return Future.succeededFuture(user); });
  }

  @Override
  public Future<JsonObject> findByID ( @NotNull String user, @NotNull final Map<String, String> headers ) {
    return null;
  }

  @Override
  public Future<JsonObject> findByAttribute ( @NotNull final String attributeName, @NotNull String attributeValue, @NotNull final Map<String, String> headers ) {
    
    // the doWithErrorHandling method wraps the code wit hthe boiler plate error handling and conversion.    
    return doWithErrorHandling(() -> {
      // The CQL template
      final String usersCql = String.format("%s==\"%s\"", attributeName, attributeValue);

      // Create the URI
      final UriBuilder userQuery = 
        UriBuilder.fromPath(BASE_PATH)
          .queryParam("query", usersCql);
      
      // This future is propagatedby the handleThrowables method.
      return get(userQuery.build(), headers)
        .expect(SERVICE_SC_SUCCESS) // 2xx response only
        .send()
        .compose(response -> {
          return Future.succeededFuture(response.bodyAsJsonObject());
        });
    });
  }
}
