package org.folio.test.mock;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.web.client.HttpResponse;

public class MockHttpResponse<T> implements HttpResponse<T> {
  
  private int statusCode = 200;
  
  @Fluent
  public HttpResponse<T> setStatusCode(final int statusCode) {
    this.statusCode = statusCode;
    return this;
  }

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
  public @Nullable T body () {
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
}

