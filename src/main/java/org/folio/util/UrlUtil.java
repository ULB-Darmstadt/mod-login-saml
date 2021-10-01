package org.folio.util;

import static org.folio.sso.saml.Constants.Exceptions.MSG_INVALID_XML_RESPONSE;
import static org.folio.sso.saml.Constants.Exceptions.MSG_PRE_ERROR_CONNECTION;
import static org.folio.sso.saml.Constants.Exceptions.MSG_PRE_UNEXPECTED;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.nio.charset.Charset;

import javax.ws.rs.core.UriBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.util.model.UrlCheckResult;
import org.xml.sax.SAXException;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;

/**
 * @author rsass
 * @author Steve Osguthorpe
 */
public class UrlUtil {

  private static final Logger log = LogManager.getLogger(UrlUtil.class);
  private UrlUtil() {

  }

  public static URI parseBaseUrl(URI originalUrl) {
    UriBuilder builder = UriBuilder.fromUri(originalUrl)
      .replaceQuery(null)
      .replacePath(null)
    ;
    
    return builder.build();
  }

  public static Future<UrlCheckResult> checkIdpUrl(String url, Vertx vertx) {
    WebClient client = WebClientFactory.getWebClient(vertx);
    return client.getAbs(url).send()
      .map(httpResponse -> {
        if (HttpUtils.isSuccess(httpResponse)) {
          try {
            final String body = httpResponse.bodyAsString();

            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder;
            dBuilder = dbFactory.newDocumentBuilder();

            dBuilder.parse(IOUtils.toInputStream(body, Charset.defaultCharset()));


            String contentType = httpResponse.getHeader("Content-Type");
            if (! contentType.contains("xml")) {
              log.warn("IDP content type has been parsed as XML, but the content-type is incorrectly reported as " + contentType);
            }
            return UrlCheckResult.emptySuccessResult();

          } catch (SAXException e) {
            return UrlCheckResult.failResult(MSG_INVALID_XML_RESPONSE);
          } catch (IOException e) {
            return UrlCheckResult.failResult(MSG_INVALID_XML_RESPONSE);
          } catch (Exception e) {
            return UrlCheckResult.failResult(MSG_PRE_UNEXPECTED + e.getMessage());
          }
        }
        
        return UrlCheckResult.failResult(MSG_PRE_UNEXPECTED + "None 2xx response code from server.");
      })
      .otherwise(cause -> {
        
        cause.printStackTrace();
        
        if (cause instanceof ConnectException) {
          // add locale independent prefix, Netty puts a locale dependent translation into getMessage(),
          // for example German "Verbindungsaufbau abgelehnt:" for English "Connection refused:"
          return UrlCheckResult.failResult(MSG_PRE_ERROR_CONNECTION + cause.getMessage());
        }
        return UrlCheckResult.failResult(MSG_PRE_UNEXPECTED + cause.getMessage());
      });
  }
}
