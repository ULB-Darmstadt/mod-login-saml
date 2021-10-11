package org.folio.util;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

import io.vertx.core.*;

/**
 * Some boiler plate and conventional behaviour for the way RMB constructs APIs.
 * 
 * @author Steve Osguthorpe
 *
 */
public class APIUtils {
  
  /**
   * Executes the code function in a none event-loop thread and delegates the success handling to the supplied handler.
   * The handler is always succeeded, with the response reflecting the success or error.
   * An error 500 response is returned if:
   * 
   * <ul>
   *   <li>the code handler is failed with a throwable.</li>
   *   <li>there is an uncaught exception within the code handler</li>
   * </ul>
   * 
   * @param vertxContext The Vertx context
   * @param delegate The response handler to delegate to
   * @param code The handling function which may throw an exception
   * @return
   */
  public static Future<Response> blockingRespondWith (@NotNull final Context vertxContext, @NotNull final Handler<AsyncResult<Response>> delegate, @NotNull final ThrowingHandler<Promise<Response>> code) {

    return vertxContext.executeBlocking((Promise<Response> blockingCode) -> {
      ErrorHandlingUtil.checkedFuture(code).onComplete(blockingCode::handle);
    })

      // Any failure is translated to a success but with the error message
      .recover(throwable -> throwableToResponseFuture(throwable))

      // Use the supplied handler.
      .onComplete(delegate::handle);
  }

  /**
   * Executes the code function and delegates to the supplied handler.
   * The handler is always succeeded, with the response reflecting the success or error.
   * An error 500 response is returned if:
   * 
   * <ul>
   *   <li>the code handler is failed with a throwable.</li>
   *   <li>there is an uncaught exception within the code handler</li>
   * </ul>
   * 
   * @param delegate The response handler to delegate to
   * @param code The handling function which may throw an exception
   * @return
   */
  public static Future<Response> respondWith (final Handler<AsyncResult<Response>> delegate, final ThrowingHandler<Promise<Response>> code) {

    return ErrorHandlingUtil.checkedFuture(code)

      // Any failure is translated to a success but with the error message
      .recover(throwable -> throwableToResponseFuture(throwable))

      // Use the supplied handler.
      .onComplete(delegate::handle);
  }

  private static Future<Response> throwableToResponseFuture (Throwable throwable) {
    final String text = throwable.getMessage();
    return Future.succeededFuture(Response
        .status(500)
        .header("Content-Type", "text/plain")
        .entity(text)
        .build()
        );
  }
}
