/**
 * 
 */
package org.folio.services.impl;

import java.util.Map;

import javax.validation.constraints.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.services.AbstractOkapiHttpService;
import org.folio.services.TokenService;
import org.folio.util.ErrorHandlingUtil;
import org.folio.util.model.OkapiHeaders;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.predicate.ResponsePredicate;

/**
 * Implementation for the OKAPI mod-authtoken based token service
 * 
 * @author Steve Osguthorpe
 *
 */
public class OkapiTokenService extends AbstractOkapiHttpService implements TokenService {

  private static final Logger log = LogManager.getLogger(OkapiTokenService.class);
  private static final String BASE_PATH = "/token";
  
  // Custom predicate as differing versions of the auth service return different statuses.
  // v2.x+ is a 201 with the token in the body and pre 2.x is 200 with a header.
  private static final ResponsePredicate CREATE_SC_SUCCESS = ResponsePredicate.create(
    ResponsePredicate.status(200, 202), // Max is exclusive!
    RESPONSE_TO_SERVICE_EXCEPTION
  );
  
  @Override
  public Future<String> create (@NotNull final String subject, @NotNull final String userId, @NotNull Map<String, String> headers) {
    
    return Future.future((Promise<String> handler) -> {
      log.debug("OkapiTokenService::create");
      if (log.isTraceEnabled()) log.trace("userdata: {}", subject, userId);
      
      // Create the payload.
      final JsonObject payload = new JsonObject()
          .put("payload", new JsonObject()
              .put("sub", subject)
              .put("user_id", userId));
      
      post(BASE_PATH, headers)
        .expect(CREATE_SC_SUCCESS) // 200/201 response only
        .sendJsonObject(payload)
        
      .map(tokenResponse -> (tokenResponse.statusCode() == 200 ?
          tokenResponse.headers().get(OkapiHeaders.OKAPI_TOKEN_HEADER) :
            tokenResponse.bodyAsJsonObject().getString("token")
      ))
      .onComplete(handler);
      
    }).recover(ErrorHandlingUtil::FAIL_WITH_SERVICE_EXCEPTION);
  }

}
