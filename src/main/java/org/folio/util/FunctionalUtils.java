package org.folio.util;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 *  
 * @author Steve Osguthorpe
 *
 */
public class FunctionalUtils {
  
  /**
   * Handles exceptions in the supplied body by succeeding the supplied Handler
   * by sending an error 500 and setting the text response text.
   *  
   * @param handler
   * @param body
   */
  public static void handleThrowables (Handler<AsyncResult<Response>> handler, ThrowingBody body) {
    try {
      body.exec();
    } catch (Throwable t) {
      final String text = t.getMessage();
      
      Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
      responseBuilder.entity(text);
      handler.handle(Future.succeededFuture(
        new ResponseDelegate(responseBuilder.build(), text) {}
      ));
    }
  }
  
  /**
   * Adds an onFailure handler to handle the general throwable failure.
   *  
   * @param handler
   * @param future
   */
  public static <T> Future<T> handleThrowables (Handler<AsyncResult<Response>> handler, Future<T> future) {
    return future.onFailure(throwable -> {
      final String text = throwable.getMessage();
      Response.ResponseBuilder responseBuilder = Response.status(500).header("Content-Type", "text/plain");
      responseBuilder.entity(text);
      handler.handle(Future.succeededFuture(
        new ResponseDelegate(responseBuilder.build(), text) {}
      ));
    });
  }
  
  
  @FunctionalInterface
  public static interface ThrowingBody {
    void exec() throws Throwable;
  }
}
