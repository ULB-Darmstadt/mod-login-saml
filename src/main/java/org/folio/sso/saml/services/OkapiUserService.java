package org.folio.sso.saml.services;

import static org.folio.util.ErrorHandlingUtil.assert2xx;

import java.util.Map;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriBuilderException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.util.OkapiHelper;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.OkapiHeaders.MissingHeaderException;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.rxjava.core.Promise;

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
