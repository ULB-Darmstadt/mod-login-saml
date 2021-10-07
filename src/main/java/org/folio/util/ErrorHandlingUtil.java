package org.folio.util;

import java.util.function.Function;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
public class ErrorHandlingUtil {
  
  private ErrorHandlingUtil() {}
  
  private static final Function<Throwable, Throwable> ERROR_CONVERTER = (throwable) -> {
    // Default throwable handler just logs and returns the original.
    defaultLoggingForThrowable(throwable);
    return throwable;
  };
  
  @FunctionalInterface
  public static interface ThrowingBody {
    void exec() throws Throwable;
  }
  
  @FunctionalInterface
  public static interface ThrowingSupplier<R> {
    R get() throws Throwable;
  }
  
  private static final Logger log = LogManager.getLogger(ErrorHandlingUtil.class);
  
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
   * Adds an onFailure handler to propagate the general throwable failure from
   * the promise and to the other handler.
   *  
   * @param handler The handler which should be failed when the future fails
   * @param future The future which we listen to for failure
   */
  public static <T, D> Future<T> handleThrowables (Handler<AsyncResult<D>> handler, Future<T> future) {
    return handleThrowables(handler, future, ERROR_CONVERTER); 
  }
  

  /**
   * Adds an onFailure handler to propagate the general throwable failure from
   * the promise and to the other handler.
   *  
   * @param handler The handler which should be failed when the future fails
   * @param future The future which we listen to for failure
   * @param errorConverter Takes in the exception and allows for creation of a new exception that will be used to fail the handler.
   */
  public static <T, D> Future<T> handleThrowables (Handler<AsyncResult<D>> handler, Future<T> future, Function<Throwable, Throwable> errorConverter) {
    return future.onFailure(throwable -> {
      
      // Anything failing in the function should be used to fail the handler instead.
      handleThrowables(handler, () -> {
        handler.handle(Future.failedFuture(errorConverter.apply(throwable)));
      });
    });
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
    handleThrowables(handler, body, ERROR_CONVERTER);
  }
  
  /**
   * Handles exceptions in the supplied body by failing the supplied Handler.
   * Because vertx promises extend Handler<AsyncResult<T>>, you can also use
   * this with promises.
   *  
   * @param handler
   * @param body
   * @param errorConverter Takes in the exception and allows for creation of a new exception that will be used to fail the handler.
   */
  public static <D, T extends Handler<AsyncResult<D>>> void handleThrowables (T handler, ThrowingBody body, Function<Throwable, Throwable> errorConverter) {
    
    try {
      body.exec();
    } catch (Throwable t) {
      handler.handle(Future.failedFuture(errorConverter.apply(t)));
    }
  }
  
  /**
   * Handles exceptions in the supplied function by returning a failed future.
   * Or the results of the body if successful. Helps for cleaner traditional try catch
   * type code, while making testing easier.
   *  
   * @param handler
   * @param body
   */
  public static <D> Future<D> handleThrowables (ThrowingSupplier<Future<D>> body) {
    return handleThrowables(body, ERROR_CONVERTER);
  }
  
  /**
   * Handles exceptions in the supplied function by returning a failed future.
   * Or the results of the body if successful. Helps for cleaner traditional try catch
   * type code, while making testing easier.
   *  
   * @param handler
   * @param body
   * @param errorConverter Takes in the exception and allows for creation of a new exception that will be used to fail the handler.
   */
  public static <D> Future<D> handleThrowables (ThrowingSupplier<Future<D>> body, Function<Throwable, Throwable> errorConverter) {
    
    try {
      return body.get();
    } catch (Throwable t) {
      return Future.failedFuture(errorConverter.apply(t));
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
      throwableToResponseHandler(throwable, handler);
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
      throwableToResponseHandler(t, handler);
    }
  }
  
  private static void throwableToResponseHandler (Throwable throwable, Handler<AsyncResult<Response>> handler) {
    final String text = throwable.getMessage();
    handler.handle(Future.succeededFuture(Response
      .status(500)
      .header("Content-Type", "text/plain")
      .entity(text)
        .build()
    ));
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
      this(message, null);
    }
    
    public CriticalDependencyException (String message, Throwable cause) {
      super(message, cause, true, false);
    }
  }
}
