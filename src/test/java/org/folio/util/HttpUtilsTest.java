package org.folio.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.ThreadLocalRandom;

import org.folio.test.mock.MockHttpResponse;
import org.junit.Test;

public class HttpUtilsTest {

  private static final int lowerBound = 200;
  private static final int upperLimit = 299;
  
  @Test
  public void twoHundredCodes() {
    // Test the whole range.
    for (int i=lowerBound; i<=upperLimit; i++) {
      assertTrue(HttpUtils.isSuccess(i));
    }
  }
  
  @Test
  public void noneTwoHundredCodes () {
    // Test boundraries.
    assertFalse(HttpUtils.isSuccess(lowerBound - 1));
    assertFalse(HttpUtils.isSuccess(upperLimit + 1));
    
    // Test 10 random codes for each end,excluding the limits we already tested.
    for (int i=0; i<10; i++) {
      assertFalse(HttpUtils.isSuccess(ThreadLocalRandom.current().nextInt(0, lowerBound - 1)));
    }
    for (int i=0; i<10; i++) {
      assertFalse(HttpUtils.isSuccess(ThreadLocalRandom.current().nextInt(upperLimit + 2, 1000)));
    }
  }
  
  @Test
  public void twoHundredResponses() {
    for (int i=lowerBound; i<=upperLimit; i++) {
      MOCK_RESPONSE.setStatusCode( i );
      assertTrue( HttpUtils.isSuccess(MOCK_RESPONSE) );
    }
  }
  
  @Test
  public void noneTwoHundredResponses() {
    // Test boundraries.
    MOCK_RESPONSE.setStatusCode( lowerBound - 1 );
    assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    MOCK_RESPONSE.setStatusCode( upperLimit + 1 );
    assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    
    // Test 10 random codes for each end,excluding the limits we already tested.
    for (int i=0; i<10; i++) {
      MOCK_RESPONSE.setStatusCode( ThreadLocalRandom.current().nextInt(0, lowerBound - 1) );
      assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    }
    for (int i=0; i<10; i++) {
      MOCK_RESPONSE.setStatusCode( ThreadLocalRandom.current().nextInt(upperLimit + 2, 1000) );
      assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    }
  }
  
  private MockHttpResponse<String> MOCK_RESPONSE = new MockHttpResponse<>();
}
