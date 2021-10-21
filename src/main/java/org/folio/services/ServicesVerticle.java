package org.folio.services;

import java.util.HashSet;
import java.util.Set;

import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.services.codecs.JacksonTypeMessageCodec;
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
    
    // Register the codec for our type.
    JacksonTypeMessageCodec.registerFor(vertx, SamlValidateResponse.class);
    
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