package org.folio.services;

import javax.validation.constraints.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;

/**
 * Central class for generic Services interaction.
 * 
 * @author Steve Osguthorpe
 *
 */
public interface Services {
  final Logger log = LogManager.getLogger(Services.class);

  public static final String MODULE_NAME = "services";
  public static final String GROUP_PACKAGE = "org.folio" + "." + MODULE_NAME;
  
  public static String getAddressFor(@NotNull Class<?> serviceInterfaceType) {
    return String.format("%s:%s", MODULE_NAME, serviceInterfaceType.getCanonicalName());
  }

  @SuppressWarnings("unchecked")
  public static <T> T proxyFor(@NotNull Vertx vertx, @NotNull Class<T> serviceType) {
    try {
      return (T) Class.forName(serviceType.getCanonicalName() + "VertxEBProxy")
        .getDeclaredConstructor(new Class[] {Vertx.class, String.class})
        .newInstance(vertx, getAddressFor(serviceType));
    } catch (Exception e) {
      log.error("Could not create proxy for interface " + serviceType.getCanonicalName(), e);
    }
    
    return null;
  }
}
