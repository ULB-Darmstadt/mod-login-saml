package org.folio.util;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.client.HttpResponse;

/**
 * Functional Utilities for moving common patterns to a more central and more
 * testable location.  
 * 
 * @author Steve Osguthorpe
 *
 */
public class ErrorHandlingUtils {
  
  private ErrorHandlingUtils() {}
  
  @FunctionalInterface
  public static interface ThrowingBody {
    void exec() throws Throwable;
  }
  
  private static final Logger log = LogManager.getLogger(ErrorHandlingUtils.class);
  
  public static void defaultLoggingForThrowable(Throwable t) {
    
    if (t instanceof CriticalDependencyException) {
      // Trace as well.
      log.error("Unrecoverable error", t);
      return;
    }

    // We assume anything getting this far is unexpected or at least a variable error.
    // Specific errors should be caught within the bodies of the Deltas and handled on a case
    // by case basis.
    log.error("Unexpected error ", t);
  }
  
  /**
   * Handles exceptions in the supplied body by failing the supplied Handler.
   * Because vertx promises extend Handler<AsyncResult<T>>, you can also use
   * this with promises.
   *  
   * @param handler
   * @param body
   */
  public static <D, T extends Handler<AsyncResult<D>>> void handleThrowables (T handler, ThrowingBody body) {
    
    try {
      body.exec();
    } catch (Throwable t) {
      defaultLoggingForThrowable(t);
      handler.handle(Future.failedFuture(t));
    }
  }
  
  /**
   * Adds an onFailure handler to handle the general throwable failure.
   *  
   * @param handler
   * @param future
   */
  public static <T, D extends Handler<AsyncResult<Response>>> Future<T> handleThrowablesWithResponse (D handler, Future<T> future) {
    return future.onFailure(throwable -> {
      defaultLoggingForThrowable(throwable);
      
      final String text = throwable.getMessage();
      Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
      responseBuilder.entity(text);
      handler.handle(Future.succeededFuture(
        new ResponseDelegate(responseBuilder.build(), text) {}
      ));
    });
  }
  
  /**
   * Handles exceptions in the supplied body by succeeding the supplied Handler
   * by sending an error 500 and setting the text response text.
   *  
   * @param handler
   * @param body
   */
  public static <T extends Handler<AsyncResult<Response>>> void handleThrowablesWithResponse (T handler, ThrowingBody body) {
    try {
      body.exec();
    } catch (Throwable t) {
      defaultLoggingForThrowable(t);
      final String text = t.getMessage();
      
      Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
      responseBuilder.entity(text);
      handler.handle(Future.succeededFuture(
        new ResponseDelegate(responseBuilder.build(), text) {}
      ));
    }
  }
  
  /**
   * Helper to throw a custom exception if the response from an external service
   * is a none 2XX.
   * 
   * @param from The response from the webclient
   * @param prelude Text to append to our exception message
   */
  public static void assert2xx (final HttpResponse<?> from, final String prelude) {
    if (!HttpUtils.isSuccess(from)) {
      
      // Extract the text.
      String text = from.bodyAsString();
      if (StringUtils.isBlank(text)) {
        text = from.statusMessage();
      }
      
      throw new CriticalDependencyException(
          String.format("%s. Return code: %d, Response: %s", prelude, from.statusCode(), text));
    }
    
    // Else do nothing :)
  }
  
  public static class CriticalDependencyException extends RuntimeException {
    private static final long serialVersionUID = 7675771235867415725L;

    public CriticalDependencyException (String message) {
      super(message);
    }
  }
}
