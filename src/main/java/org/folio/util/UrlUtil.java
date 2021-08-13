package org.folio.util;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.impl.SamlAPI;
import org.folio.util.model.UrlCheckResult;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 */
public class UrlUtil {

  private static final Logger log = LogManager.getLogger(UrlUtil.class);
  private UrlUtil() {

  }

  public static URI parseBaseUrl(URI originalUrl) {
      return URI.create(originalUrl.getScheme() + "://" + originalUrl.getAuthority());
  }

  public static Future<UrlCheckResult> checkIdpUrl(String url, Vertx vertx) {
    WebClient client = WebClientFactory.getWebClient(vertx);

    return client.getAbs(url).send()
    .map(httpResponse -> {
      String contentType = httpResponse.getHeader("Content-Type");
      if (! contentType.contains("xml")) {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        try {
          DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
          final String body = httpResponse.bodyAsString();
          
          dBuilder.parse(IOUtils.toInputStream(body, Charset.defaultCharset()));
          
        } catch (Exception e) {
          return UrlCheckResult.failResult("Response content-type is not XML and we couldn't parse the document.");
        }

        log.warn("IDP content type has been parsed as XML, but the content-type is incorrectly reported as " + contentType);
        
//        return UrlCheckResult.failResult("Response content-type is not XML");
      }
      return UrlCheckResult.emptySuccessResult();
    })
    .otherwise(cause -> {
      if (cause instanceof ConnectException) {
        // add locale independent prefix, Netty puts a locale dependent translation into getMessage(),
        // for example German "Verbindungsaufbau abgelehnt:" for English "Connection refused:"
        return UrlCheckResult.failResult("ConnectException: " + cause.getMessage());
      }
      return UrlCheckResult.failResult("Unexpected error: " + cause.getMessage());
    });
  }
}
