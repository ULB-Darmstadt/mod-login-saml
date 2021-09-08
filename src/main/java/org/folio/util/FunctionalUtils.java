package org.folio.util;

import javax.ws.rs.core.Response;

import org.folio.rest.jaxrs.resource.support.ResponseDelegate;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

/**
 * Functional Utilities for moving common patterns to a more central and more
 * testable location.  
 * 
 * @author Steve Osguthorpe
 *
 */
public class FunctionalUtils {
  
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
      handler.handle(Future.failedFuture(t));
    }
  }
  
  /**
   * Handles exceptions in the supplied body by succeeding the supplied Handler
   * by sending an error 500 and setting the text response text.
   *  
   * @param handler
   * @param body
   */
  public static void handleThrowablesWithResponse (Handler<AsyncResult<Response>> handler, ThrowingBody body) {
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
  public static <T> Future<T> handleThrowablesWithResponse (Handler<AsyncResult<Response>> handler, Future<T> future) {
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
