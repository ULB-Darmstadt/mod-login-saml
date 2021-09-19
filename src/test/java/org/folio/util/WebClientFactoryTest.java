package org.folio.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClient;

@RunWith(VertxUnitRunner.class)
public class WebClientFactoryTest {
  
  Vertx vertx;
  
  @After
  public void teardown(TestContext context) {
    if (vertx != null) vertx.close(context.asyncAssertSuccess());
  }

  @Test
  public void noneVertxContext() {
    Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
      WebClientFactory.getWebClient();
    });

    String expectedMessage = "Call to getWebClient() outside of vertx context";
  
    assertTrue(exception.getMessage().contains(expectedMessage));
    
    withVertxConfig(); // Default conf.
    
    // Still not passed in
    exception = assertThrows(UnsupportedOperationException.class, () -> {
      WebClientFactory.getWebClient();
    });
    
    assertTrue(exception.getMessage().contains(expectedMessage));
  }

  @Test
  public void vertxContext() {
    withVertxConfig(); // Default conf.
    WebClient wc = WebClientFactory.getWebClient(vertx);
    
    // Client should be returned
    assertNotNull(wc);
  }
  
  private void withVertxConfig() {
    vertx = Vertx.vertx();
  }
}
