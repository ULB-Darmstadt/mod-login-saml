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
import io.vertx.ext.web.client.predicate.ResponsePredicate;

/**
 * @author Steve Osguthorpe
 */
public class OkapiUserService extends AbstractOkapiHttpService implements UserService {
  private static final Logger log = LogManager.getLogger(OkapiUserService.class);
  private static final String BASE_PATH = "/users";

  @Override
  public Future<JsonObject> save (@NotNull final Map<String, String> headers, @NotNull final JsonObject user) {
    log.debug("OkapiUserService::save {}", user);
    return Future.succeededFuture(user);
  }

  @Override
  public Future<JsonObject> findByID (@NotNull final Map<String, String> headers, @NotNull String user) {
    return null;
  }

  @Override
  public Future<JsonObject> findByAttribute (@NotNull final Map<String, String> headers, @NotNull final String attributeName, @NotNull String attributeValue) {
    
    try {
      final String usersCql = String.format("%s==\"%s\"", attributeName, attributeValue);

      final UriBuilder userQuery = 
        UriBuilder.fromPath(BASE_PATH)
          .queryParam("query", usersCql);
      return get(userQuery.build(), headers)
        .expect(ResponsePredicate.SC_SUCCESS)
        .send()
        .compose(response -> {
          return Future.succeededFuture(response.bodyAsJsonObject());
        })
      ;
    } catch (Exception e) {
      log.error("Error attempting to find user", e);
    }
    return null;
  }

}
