/**
 * 
 */
package org.folio.services.codecs;


import javax.validation.constraints.NotNull;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.Json;

/**
 * Convenience factory to create message codecs for types that can be converted by jackson.
 * 
 * @author Steve Osguthorpe
 *
 */
public class JacksonTypeMessageCodec<T> implements MessageCodec<T, T> {
  
  private final Class<T> typeClass;
  
  protected JacksonTypeMessageCodec (Class<T> typeClass) {
    this.typeClass = typeClass;
  }

  @Override
  public void encodeToWire (Buffer buffer, T s) {
    
    // Encode to buffer first.
    final Buffer buff = Json.encodeToBuffer(s);

    // Get length of buffer so we know how much to read when decoding.
    final int length = buff.length();

    // Write data into given buffer, starting with the length as an int.
    buffer.appendInt(length); // ints are 4 bytes.
    buffer.appendBuffer(buff);
  }

  @Override
  public T decodeFromWire (final int pos, final Buffer buffer) {
    
    // Read int length first.
    final int length = buffer.getInt(pos);
    
    // BytePosition increases by 4 as getInt reads 4 bytes as an integer.
    return Json.decodeValue(buffer.getBuffer(pos+4, pos+length+4), typeClass);
  }

  @Override
  public T transform (final T inst) {
    return inst;
  }

  @Override
  public String name () {
    return "jackson." + typeClass.getCanonicalName();
  }

  @Override
  public byte systemCodecID () {
    return -1;
  }
  
  public static <T> JacksonTypeMessageCodec<T> codecFor(@NotNull final Class<T> type) {
    return new JacksonTypeMessageCodec<>( type );
  }
  
  public static <T> JacksonTypeMessageCodec<T> registerFor(@NotNull final Vertx vertx, @NotNull final Class<T> type) {
    final var theCodec = codecFor(type);
    vertx.eventBus().registerDefaultCodec(type, theCodec);
    return theCodec;
  }
}
