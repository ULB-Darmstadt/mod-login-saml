package org.folio.util;

import java.util.function.Function;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.*;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.serviceproxy.ServiceException;

/**
 * Functional Utilities for moving common patterns to a more central and more
 * testable location.  
 * 
 * @author Steve Osguthorpe
 *
 */
public class ErrorHandlingUtil {
  
  public static <T> Future<T> FAIL_WITH_SERVICE_EXCEPTION (Throwable throwable) {
    return Future.failedFuture(throwable instanceof ServiceException ? throwable : new ServiceException(-1, throwable.getMessage()));
  };
  
  /**
   * Produce a future that fails if the handler throws any throwables. This is an improvement on the
   * native implementation that only allows the handler to throw runtime exceptions.
   * 
   * @param <T> The generic type for the future
   * @param handler The handler which may throw none runtime type exceptions.
   * @return The future
   */
  public static <T> Future<T> checkedFuture(ThrowingHandler<Promise<T>> handler) {
    
    return Future.future((Promise<T> event) -> {
      ErrorHandlingUtil.handleThrowables(event, () -> {
        handler.handle(event);
      });
    });
  }
  
  /**
   * Produce a future that fails if the handler throws any throwables. This is an improvement on the
   * native implementation that only allows the handler to throw runtime exceptions.
   * 
   * @param <T> The generic type for the future
   * @param handler The handler which may throw none runtime type exceptions.
   * @return The future
   */
  public static <T> Future<T> blockingCheckedFuture(Context context, ThrowingHandler<Promise<T>> handler) {
    return context.executeBlocking((Promise<T> event) -> {
      ErrorHandlingUtil.handleThrowables(event, () -> {
        handler.handle(event);
      });
    });
  }
  
  private ErrorHandlingUtil() {}
  
  private static final Throwable ERROR_CONVERTER (Throwable throwable) {
    // Default throwable handler just logs and returns the original.
    defaultLoggingForThrowable(throwable);
    return throwable;
  };
  
  @FunctionalInterface
  public static interface ThrowingBody {
    void exec() throws Throwable;
  }
  
  private static final Logger log = LogManager.getLogger(ErrorHandlingUtil.class);
  
  public static void defaultLoggingForThrowable(Throwable t) {
    
    if (t instanceof CriticalDependencyException) {
      // Trace as well.
      log.error("Unrecoverable error", t);
      return;
    }

    // We assume anything getting this far is unexpected or at least a variable error.
    // Specific errors should be caught and handled on a case by case basis.
    log.error("Unexpected error: {}", t.getMessage());
  }
  
  /**
   * Adds an onFailure handler to propagate the general throwable failure from
   * the promise and to the other handler.
   *  
   * @param handler The handler which should be failed when the future fails
   * @param future The future which we listen to for failure
   */
  public static <T, D> Future<T> handleThrowables (Handler<AsyncResult<D>> handler, Future<T> future) {
    return handleThrowables(handler, future, ErrorHandlingUtil::ERROR_CONVERTER); 
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
    handleThrowables(handler, body, ErrorHandlingUtil::ERROR_CONVERTER);
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
  
  public static class CriticalDependencyException extends ReplyException {

    private static final long serialVersionUID = 7675771235867415725L;
    private static final ReplyFailure type = ReplyFailure.RECIPIENT_FAILURE; 
    
    public CriticalDependencyException(String message) {
      super(type, message);
    }

    public CriticalDependencyException (int failureCode, String message) {
      super(type, failureCode, message);
    }

    public CriticalDependencyException () {
      super(type);
    }
    
  }
}
