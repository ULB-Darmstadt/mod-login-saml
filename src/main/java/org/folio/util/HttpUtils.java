package org.folio.util;

import javax.validation.constraints.NotNull;

import io.vertx.ext.web.client.HttpResponse;

public class HttpUtils {
  private HttpUtils() {}
  
  public static boolean isSuccess(@NotNull HttpResponse<?> response) {
    return isSuccess(response.statusCode());
  }
  
  public static boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }
}
