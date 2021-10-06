/**
 * 
 */
package org.folio.sso.saml.services;

import javax.validation.constraints.NotNull;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;

/**
 * Central class for generic Services interraction.
 * 
 * @author Steve Osguthorpe
 *
 */
public interface Services {
  final Logger log = LogManager.getLogger(Services.class);

  public static final String MODULE_NAME_SERVICES = "services";
  public static final String GROUP_PACKAGE_SERVICES = "org.folio.sso.saml";
  public static final String PACKAGE_SERVICES = GROUP_PACKAGE_SERVICES + "." + MODULE_NAME_SERVICES;
  
  public static String getAddressFor(@NotNull Class<?> serviceInterfaceType) {
    return String.format("%s:%s", MODULE_NAME_SERVICES, serviceInterfaceType.getCanonicalName());
  }

  @SuppressWarnings("unchecked")
  public static <T> T proxyFor(@NotNull Vertx vertx, @NotNull Class<T> serviceType) {
    
    try {
      
      return (T) Class.forName(PACKAGE_SERVICES + "." + serviceType.getSimpleName() + "VertxEBProxy")
        .getDeclaredConstructor(new Class[] {Vertx.class, String.class})
        .newInstance(vertx, getAddressFor(serviceType));
    } catch (Exception e) {
      log.error("Could not create proxy for interface " + serviceType.getCanonicalName(), e);
    }
    
    return null;
  }
}
