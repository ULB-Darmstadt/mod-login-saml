package org.folio.services;

import java.util.HashSet;
import java.util.Set;

import org.folio.services.impl.OkapiTokenService;
import org.folio.services.impl.OkapiUserService;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.serviceproxy.ServiceBinder;

public class ServicesVerticle extends AbstractVerticle {

  private ServiceBinder binder;
  private Set<MessageConsumer<JsonObject>> consumers = new HashSet<>();
  
  @Override
  public void start() {
    binder = new ServiceBinder(vertx);
    register(UserService.class, new OkapiUserService());
    register(TokenService.class, new OkapiTokenService());
  }
  
  private <T, D extends T> void register( final Class<T> serviceClass, D inst ) {
    consumers.add(
      binder
        .setAddress(String.format("%s:%s", Services.MODULE_NAME, serviceClass.getCanonicalName()))
        .register(serviceClass, inst)
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