package org.folio.services;

import java.util.HashSet;
import java.util.Set;

import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.services.impl.OkapiTokenService;
import org.folio.services.impl.OkapiUserService;
import org.folio.services.impl.OpenSamlIdpMetadataService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;

public class ServicesVerticle extends AbstractVerticle {

  private ServiceBinder binder;
  private Set<MessageConsumer<JsonObject>> consumers = new HashSet<>();
  
  @Override
  public void start() {
    binder = new ServiceBinder(vertx);
    
    vertx.eventBus().registerDefaultCodec(SamlValidateResponse.class, new MessageCodec<SamlValidateResponse, SamlValidateResponse>() {

      @Override
      public void encodeToWire (Buffer buffer, SamlValidateResponse s) {
        
        // Encode to buffer first.
        final Buffer buff = Json.encodeToBuffer(s);

        // Get length of buffer so we know how much to read when decoding.
        final int length = buff.length();

        // Write data into given buffer, starting with the length as an int.
        buffer.appendInt(length); // ints are 4 bytes.
        buffer.appendBuffer(buff);
      }

      @Override
      public SamlValidateResponse decodeFromWire (final int pos, final Buffer buffer) {
        
        // Read int length first.
        final int length = buffer.getInt(pos);
        
        // BytePosition increases by 4 as getInt reads 4 bytes as an integer.
        return Json.decodeValue(buffer.getBuffer(pos+4, pos+length+4), SamlValidateResponse.class);
      }

      @Override
      public SamlValidateResponse transform (final SamlValidateResponse s) {
        return s;
      }

      @Override
      public String name () {
        return "json-" + SamlValidateResponse.class.getSimpleName();
      }

      @Override
      public byte systemCodecID () {
        return -1;
      }
      
    });
    
    register(UserService.class, new OkapiUserService());
    register(TokenService.class, new OkapiTokenService());
    register(IdpMetadataService.class, new OpenSamlIdpMetadataService());
  }
  
  private <T, D extends T> void register( final Class<T> serviceClass, D inst ) {
    consumers.add(
      binder
        .setAddress(String.format("%s:%s", Services.MODULE_NAME, serviceClass.getCanonicalName()))
        .registerLocal(serviceClass, inst)
    );
  }
  
  @Override
  public void stop() {
    if (binder != null) {
      // Remove each consumer as cleanup.
      consumers.stream()
        .forEach(binder::unregister);
      binder = null;
      consumers = null;
    }
  }
}