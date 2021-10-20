package org.folio.rest.impl;

import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

import javax.net.ssl.*;

import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.rest.resource.interfaces.InitAPI;
import org.folio.services.ServicesVerticle;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.Json;

public class ApiInitializer implements InitAPI {

  private final Logger log = LogManager.getLogger(ApiInitializer.class);

  @Override
  public void init(Vertx vertx, Context context, Handler<AsyncResult<Boolean>> handler) {
    
    if ("true".equalsIgnoreCase(System.getenv("TRUST_ALL_CERTIFICATES")) ||
        ("true".equalsIgnoreCase(System.getProperty("TRUST_ALL_CERTIFICATES")))) {
      trustAllCertificates();
    }

    String disableResolver = System.getProperty("vertx.disableDnsResolver");
    log.info("vertx.disableDnsResolver (netty workaround): " + disableResolver);
    
//    vertx.eventBus().registerDefaultCodec(SamlValidateResponse.class, new MessageCodec<SamlValidateResponse, SamlValidateResponse>() {
//
//      @Override
//      public void encodeToWire (Buffer buffer, SamlValidateResponse s) {
//        
//        // Encode to buffer first.
//        final Buffer buff = Json.encodeToBuffer(s);
//
//        // Get length of buffer so we know how much to read when decoding.
//        final int length = buff.length();
//
//        // Write data into given buffer, starting with the length as an int.
//        buffer.appendInt(length); // ints are 4 bytes.
//        buffer.appendBuffer(buff);
//      }
//
//      @Override
//      public SamlValidateResponse decodeFromWire (final int pos, final Buffer buffer) {
//        
//        // Read int length first.
//        final int length = buffer.getInt(pos);
//        
//        // BytePosition increases by 4 as getInt reads 4 bytes as an integer.
//        return Json.decodeValue(buffer.getBuffer(pos+4, pos+length+4), SamlValidateResponse.class);
//      }
//
//      @Override
//      public SamlValidateResponse transform (final SamlValidateResponse s) {
//        return s;
//      }
//
//      @Override
//      public String name () {
//        return "json-" + SamlValidateResponse.class.getSimpleName();
//      }
//
//      @Override
//      public byte systemCodecID () {
//        return -1;
//      }
//      
//    });

    vertx.deployVerticle(new ServicesVerticle(), ar -> {
      log.debug("Deployed services verticle");
      handler.handle(ar.map(true));
    });
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
