package org.folio.util;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.folio.sso.saml.Constants;

import io.vertx.core.Future;

/**
 * Utilities for asynchronous operations.
 * 
 * @author Steve Osguthorpe
 *
 */
public interface AsyncUtil {

  /**
   * Executes some code and awaits the results for a default amount of time (currently 5 seconds).
   * 
   * @see {@link #blocking(Future, int) blocking(T future, int secondsToWait)}
   */
  public static <D, T extends Future<D>> D blocking (T future)
      throws Exception {
    return blocking (future, Constants.BLOCKING_OP_TIMEOUT_SECONDS);
  }

  /**
   * Executes some code and awaits the results, for a given length of time in
   * seconds One of the Vertx mantra is to not block. For normal pure vertx code
   * that's fine. However, sometimes we need to block and wait, like when
   * extending a traditional POJO method that is expected to return a concrete
   * value and not an Asynchronous result handle like a promise or future.
   * 
   * It can also be useful/desirable to use this pattern in singleton type
   * getters where failure to manufacture our object is a failure anyway. This
   * pattern is used in the WebClientFactory {@link WebClientFactory} for
   * improved legibility and use.
   * 
   * @param <D>
   *          Return type
   * @param <T>
   *          Future type with resolution to type &lt;D&gt;
   * @param future
   *          The future we wish to await the results of
   * @param secondsToWait
   *          The max number of seconds to wait before timing out
   * @return The result of type &lt;D&gt;
   * @throws Exception
   *           If the future fails with an exception we rethrow that cause.
   */
  public static <D, T extends Future<D>> D blocking (final T future,
      final int secondsToWait) throws Exception {
    try {
      return future.toCompletionStage().toCompletableFuture()
          .get(secondsToWait, TimeUnit.SECONDS);
    } catch (final ExecutionException exeEx) {

      // This means there was something thrown in the body of the future.
      // AS these are special in that we care about the cause more than the
      // result
      // We should log the result here and rethrow the cause.
      ErrorHandlingUtils.defaultLoggingForThrowable(exeEx);
      final Throwable cause = exeEx.getCause();

      throw (cause != null && Exception.class.isAssignableFrom(cause.getClass())
          ? (Exception) cause
          : exeEx);
    }
  }

}