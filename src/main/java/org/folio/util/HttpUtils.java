package org.folio.util;

import io.vertx.ext.web.client.HttpResponse;

public class HttpUtils {
  private HttpUtils() {}
  
  public static boolean isSuccess(HttpResponse<?> response) {
    return isSuccess(response.statusCode());
  }
  
  public static boolean isSuccess(int statusCode) {
    return statusCode >= 200 && statusCode < 300;
  }
}
