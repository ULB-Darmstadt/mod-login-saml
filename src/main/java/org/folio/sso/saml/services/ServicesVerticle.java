package org.folio.sso.saml.services;

import io.vertx.core.AbstractVerticle;
import io.vertx.serviceproxy.ServiceBinder;

public class ServicesVerticle extends AbstractVerticle {

  @Override
  public void start() {
    UserService service = new OkapiUserService();
    new ServiceBinder(vertx)
      .setAddress(String.format("services:%s", UserService.class.getCanonicalName()))
      .register(UserService.class, service);
  }
}