package org.folio.rest.impl;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.resource.interfaces.InitAPI;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;

public class ApiInitializer implements InitAPI {

  private final Logger log = LogManager.getLogger(ApiInitializer.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    
    log.info("New version of mod-login-saml");

    if ("true".equalsIgnoreCase(System.getenv("TRUST_ALL_CERTIFICATES")) ||
        ("true".equalsIgnoreCase(System.getProperty("TRUST_ALL_CERTIFICATES")))) {
      trustAllCertificates();
    }

    String disableResolver = System.getProperty("vertx.disableDnsResolver");
    log.info("vertx.disableDnsResolver (netty workaround): " + disableResolver);

    handler.handle(Future.succeededFuture(true));
  }

  /**
   * A HACK for disable HTTPS security checks. DO NOT USE IN PRODUCTION!
   * https://stackoverflow.com/a/2893932
   */
  private void trustAllCertificates() {
    log.warn(
      "\n************ DO NOT USE IN PRODUCTION **********" +
      "\n** Disabling all SSL certificate verification **" +
      "\n************************************************");

    // Install the all-trusting trust manager
    try {
      
      // Create a trust manager that does not validate certificate chains
      final TrustManager[] trustAllCerts = new TrustManager[]{
        new X509TrustManager() {
          public java.security.cert.X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
          }

          public void checkClientTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
          }

          public void checkServerTrusted(
            java.security.cert.X509Certificate[] certs, String authType) {
          }
        }
      };
      
      // Inititalise the security manager with the new insecure trust-store.
      final SSLContext sc = SSLContext.getInstance(SSLConnectionSocketFactory.TLS);
      sc.init(null, trustAllCerts, new java.security.SecureRandom());
      
      // Set some defaults.
      SSLContext.setDefault(sc);
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
      HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
        public boolean verify(String hostname, SSLSession session) {
          return true; // Allow any name in the certificate.
        }
      });
      
    } catch (GeneralSecurityException e) {
      log.error("Error installing custom Certificate trust manager", e); 
    }
  }
}
