/**
 * 
 */
package org.folio.services;

import java.net.URI;
import java.util.Map;

import javax.validation.constraints.NotNull;

import org.folio.util.OkapiHelper;
import org.folio.util.WebClientFactory;
import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.OkapiHeaders.MissingHeaderException;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.WebClient;

/**
 * Abstract to handle the boilerplate headers and provide general method wrappers.
 * 
 * @author Steve Osguthorpe
 */
public abstract class AbstractOkapiHttpService {

  private final WebClient webClient;

  protected AbstractOkapiHttpService(WebClient wc) {
    webClient = wc;
  }

  protected AbstractOkapiHttpService() {
    this(WebClientFactory.getWebClient());
  }

  private HttpRequest<Buffer> request ( @NotNull final HttpMethod method, @NotNull final String path, @NotNull final Map<String, String> headers ) throws MissingHeaderException {
    
    final OkapiHeaders okh = OkapiHelper.okapiHeaders(headers);
    
    return webClient
      .requestAbs(method, OkapiHelper.toOkapiUrl(okh.getUrl(), path))
      .putHeaders(okh.securedInteropHeaders())
    ;
  }
  
  protected HttpRequest<Buffer> get(@NotNull final String path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return request(HttpMethod.GET, path, incomingHeaders);
  }

  protected HttpRequest<Buffer> get(@NotNull final URI path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return get(path.toASCIIString(), incomingHeaders);
  }

  protected HttpRequest<Buffer> post(@NotNull final String path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return request(HttpMethod.POST, path, incomingHeaders);
  }

  protected HttpRequest<Buffer> post(@NotNull final URI path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return post(path.toASCIIString(), incomingHeaders);
  }

  protected HttpRequest<Buffer> put(@NotNull final String path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return request(HttpMethod.PUT, path, incomingHeaders);
  }

  protected HttpRequest<Buffer> put(@NotNull final URI path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return put(path.toASCIIString(), incomingHeaders);
  }

  protected HttpRequest<Buffer> delete(@NotNull final String path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return request(HttpMethod.DELETE, path, incomingHeaders);
  }

  protected HttpRequest<Buffer> delete(@NotNull final URI path, @NotNull final Map<String, String> incomingHeaders) throws MissingHeaderException {
    return delete(path.toASCIIString(), incomingHeaders);
  }
}
