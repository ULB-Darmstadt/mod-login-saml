package org.folio.util;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * @author rsass
 * @author Steve Osguthorpe
 */
public class Base64Util {

  private Base64Util() {}

  /**
   * Encodes a {@link String} with Base64, asnyc.
   *
   * @param content String to encode
   * @return Buffer bytes of Base64 string
   */
  public static Future<Buffer> encode(String content) {
    return ErrorHandlingUtil.checkedFuture(handler -> {
      byte[] encodedBytes = Base64.getEncoder().encode(content.getBytes(StandardCharsets.UTF_8));
      handler.complete(Buffer.buffer(encodedBytes));
    });
  }
}
