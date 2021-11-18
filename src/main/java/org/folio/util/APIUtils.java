package org.folio.util;

import javax.validation.constraints.NotNull;
import javax.ws.rs.core.Response;

import io.vertx.core.*;

/**
 * Some boiler plate and conventional behaviour for the way RMB constructs APIs.
 * Allows us to write code that repeats less in the API implementations, and
 * gives us single entry points to test behaviour. 
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
    
    if (throwable instanceof CodedHttpApiException) {
      // Special cases we can foresee and use a simple throw to trigger in the implementation.
      CodedHttpApiException codedEx = (CodedHttpApiException) throwable;
      return Future.succeededFuture(Response
          .status(codedEx.code)
          .header("Content-Type", "text/plain")
          .entity(codedEx.getMessage())
        .build()
      );
    }
    
    final String text = throwable.getMessage();
    return Future.succeededFuture(Response
        .status(500)
        .header("Content-Type", "text/plain")
        .entity(text)
      .build()
    );
  }
  

  /**
   * Special exception that when caught via the other utility methods will cause a text and code response
   * to be sent to the client instead of a general 500. 
   * 
   * @author Steve Osguthorpe
   */
  public static class CodedHttpApiException extends VertxException {
    private static final long serialVersionUID = 2096339694399530059L;
    final int code;

    public CodedHttpApiException (int code, String message) {
      this(code, message, null);
    }
    
    public CodedHttpApiException (int code, String message, Throwable cause) {
      super(message, cause, true);
      this.code = code;
    }
  }
}
