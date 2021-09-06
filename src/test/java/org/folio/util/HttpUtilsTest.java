package org.folio.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;

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
      statusCode = i;
      assertTrue( HttpUtils.isSuccess(MOCK_RESPONSE) );
    }
  }
  
  @Test
  public void noneTwoHundredResponses() {
    // Test boundraries.
    statusCode = lowerBound - 1;
    assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    statusCode = upperLimit + 1;
    assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    
    // Test 10 random codes for each end,excluding the limits we already tested.
    for (int i=0; i<10; i++) {
      statusCode = ThreadLocalRandom.current().nextInt(0, lowerBound - 1);
      assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    }
    for (int i=0; i<10; i++) {
      statusCode = ThreadLocalRandom.current().nextInt(upperLimit + 2, 1000);
      assertFalse(HttpUtils.isSuccess(MOCK_RESPONSE));
    }
  }

  private static int statusCode = -1;
  
  public static final HttpResponse<String> MOCK_RESPONSE = new HttpResponse<String>() {
    
    @Override
    public HttpVersion version () {
      return HttpVersion.HTTP_1_1;
    }

    @Override
    public int statusCode () {
      return statusCode;
    }

    @Override
    public String statusMessage () {
      // TODO Auto-generated method stub
      return null;
    }

    MultiMap headers;
    @Override
    public MultiMap headers () {
      if (headers == null) {
        headers = MultiMap.caseInsensitiveMultiMap();
      }
      return headers;
    }

    @Override
    public @Nullable String getHeader (String headerName) {
      return headers().get(headerName);
    }
    
    MultiMap trailers;
    @Override
    public MultiMap trailers () {
      if (trailers == null) {
        trailers = MultiMap.caseInsensitiveMultiMap();
      }
      return trailers;
    }

    @Override
    public @Nullable String getTrailer (String trailerName) {
      return headers().get(trailerName);
    }

    @Override
    public List<String> cookies () {
      return Collections.emptyList();
    }

    @Override
    public @Nullable String body () {
      return null;
    }

    @Override
    public @Nullable Buffer bodyAsBuffer () {
      return null;
    }

    @Override
    public List<String> followedRedirects () {
      return Collections.emptyList();
    }

    @Override
    public @Nullable JsonArray bodyAsJsonArray () {
      return null;
    }
  };
}
