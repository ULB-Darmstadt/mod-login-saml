package org.folio.util;

import static org.folio.util.ErrorHandlingUtil.*;
import static org.folio.util.ErrorHandlingUtil.handleThrowables;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import javax.ws.rs.core.Response;

import org.folio.test.mock.MockHttpResponse;
import org.folio.util.ErrorHandlingUtil.CriticalDependencyException;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * @author Steve Osguthorpe
 */
@RunWith(VertxUnitRunner.class)
public class ErrorHandlingUtilTest {
  
  private MockHttpResponse<String> MOCK_RESPONSE = new MockHttpResponse<>(); 
  private static final int lowerBound = 200;
  private static final int upperLimit = 299;
  
  @Test
  public void twoHundredResponses() {
    for (int i=lowerBound; i<=upperLimit; i++) {
      MOCK_RESPONSE.setStatusCode( i );
      
      final String errorprelude = "Some text " + i;
      assert2xx(MOCK_RESPONSE, errorprelude);

      // Does not throw.
    }
  }
  
  @Test
  public void noneTwoHundredResponses() {
    // Test boundraries.
    MOCK_RESPONSE.setStatusCode( lowerBound - 1 );
    
    final String errorpreludeLow = "Some text " + (lowerBound - 1);
    Exception exception = assertThrows(CriticalDependencyException.class, () -> {
      assert2xx(MOCK_RESPONSE, errorpreludeLow);
    });
    assertTrue(exception.getMessage().startsWith(errorpreludeLow));
    
    
    MOCK_RESPONSE.setStatusCode( upperLimit + 1 );
    final String errorpreludeHigh = "Some text " + (upperLimit + 1);
    exception = assertThrows(CriticalDependencyException.class, () -> {
      assert2xx(MOCK_RESPONSE, errorpreludeHigh);
    });
    assertTrue(exception.getMessage().startsWith(errorpreludeHigh));
    
    // Test 10 random codes for each end,excluding the limits we already tested.
    for (int i=0; i<10; i++) {
      MOCK_RESPONSE.setStatusCode( i );
      
      final String errorprelude = "Some text " + i;
      
      exception = assertThrows(CriticalDependencyException.class, () -> {
        assert2xx(MOCK_RESPONSE, errorprelude);
      });
    
      assertTrue(exception.getMessage().startsWith(errorprelude));
    }
    for (int i=0; i<10; i++) {
      MOCK_RESPONSE.setStatusCode( i );
      
      final String errorprelude = "Some text " + i;
      
      exception = assertThrows(CriticalDependencyException.class, () -> {
        assert2xx(MOCK_RESPONSE, errorprelude);
      });
    
      assertTrue(exception.getMessage().startsWith(errorprelude));
    }
  }

  private Handler<AsyncResult<Object>> withThrowableHandler(final TestContext context, final String message) {
    return context.asyncAssertFailure(cause -> {
      context.assertTrue(cause instanceof Throwable);
      context.assertEquals(cause.getMessage(), message,
          "cause.getMessage() expected : '" + message + 
          "', found '" + cause.getMessage() +"'");
    });
  }
  
  private Handler<AsyncResult<Response>> withRequestHandler(final TestContext context, final String message) {
    return context.asyncAssertSuccess((Response res) -> {
      context.assertTrue(res.getStatus() == 500, "Response exected: 500, received: " + res.getStatus());
      context.assertEquals(res.getEntity(), message, "Response message expected: '" + message + 
          "', found '" + res.getEntity() + "'");
    });
  }
  
  @Test
  public void futureFailureFailsHandler(TestContext context) {
    final String message = "Failed doing stuff";
    
    // Failure with exception.
    handleThrowables(
      withThrowableHandler(context, message),
      Future.failedFuture(new Exception(message))
    );
    
    // Failure with message only.
    handleThrowables(
      withThrowableHandler(context, message),
      Future.failedFuture(message)
    );
  }
  
  @Test
  public void thrownExceptionFailsHandler(TestContext context) {
    final String message = "This body allows throwables and should trigger handler failure";
    
    handleThrowables(withThrowableHandler(context, message), () -> {
      throw new Exception (message);
    });
  }
  
  @Test
  public void futureFailureSucceedsHandlerWith500Response (TestContext context) {
    final String message = "Failed doing stuff";
    handleThrowablesWithResponse(
      withRequestHandler(context, message),
      Future.failedFuture(message)
    );
  }
  
  @Test
  public void thrownExceptionSucceedsHandlerWith500Response (TestContext context) {
    final String message = "This body allows throwables and should trigger handler failure";
    
    handleThrowablesWithResponse(withRequestHandler(context, message), () -> {
      throw new Exception (message);
    });
  }
}
