package org.folio.util;

import static com.github.tomakehurst.wiremock.common.Urls.splitQuery;
import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.github.tomakehurst.wiremock.http.Cookie;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.HttpHeaders;
import com.github.tomakehurst.wiremock.http.QueryParameter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;

import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.impl.HttpContext;

public class HttpContextWiremockAdapter implements Request {

  private static final String MSG_UN_UP = "Only the request URL should be used when determining whether to proxy to the mock server";
  private final RequestOptions request;
  private final Supplier<Map<String, QueryParameter>> cachedQueryParams;

  private final URI urlObj;

  public HttpContextWiremockAdapter(HttpContext<?> context) throws URISyntaxException {
//    this.context = context;
    this.request = context.requestOptions();
    final URI pathOnly = new URI(request.getURI());
    this.urlObj = new URI(
        request.isSsl() ? "https" : "http",
            null,
            request.getHost(),
            request.getPort(),
            pathOnly.getRawPath(),
            pathOnly.getRawQuery(),
            pathOnly.getRawFragment()
        );

    cachedQueryParams = Suppliers.memoize(new Supplier<Map<String, QueryParameter>>() {
      @Override
      public Map<String, QueryParameter> get() {
        return splitQuery(urlObj);
      }
    });
  }

  @Override
  public boolean containsHeader (String key) {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public ContentTypeHeader contentTypeHeader () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public String getAbsoluteUrl() {
    return this.getScheme() + "://" +
        this.getHost() +
        ":" + getPort() +
        this.getUrl();
  }

  @Override
  public Set<String> getAllHeaderKeys () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public byte[] getBody () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public String getBodyAsBase64 () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public String getBodyAsString () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }
  
  @Override
  public String getClientIp () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }  

  @Override
  public Map<String, Cookie> getCookies () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public String getHeader (String key) {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public HttpHeaders getHeaders () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public String getHost() {
    return this.urlObj.getHost();
  }

  @Override
  public RequestMethod getMethod() {
    return RequestMethod.fromString(
      request.getMethod().name().toUpperCase());
  }

  @Override
  public Optional<Request> getOriginalRequest () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public Part getPart (String name) {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public Collection<Part> getParts () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public int getPort() {
    return this.urlObj.getPort();
  }

  @Override
  public String getScheme() {
    return this.urlObj.getScheme();
  }

  @Override
  public String getUrl() {
    return withQueryStringIfPresent(this.urlObj.getRawPath());
  }

  @Override
  public HttpHeader header (String key) {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public boolean isBrowserProxyRequest () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public boolean isMultipart () {
    throw new UnsupportedOperationException(MSG_UN_UP);
  }

  @Override
  public QueryParameter queryParameter(String key) {
    Map<String, QueryParameter> queryParams = cachedQueryParams.get();
    return firstNonNull(
        queryParams.get(key),
        QueryParameter.absent(key)
        );
  }

  @Override
  public String toString() {
    return request.toString();
  }

  private String withQueryStringIfPresent(String url) {
    final String qs = this.urlObj.getRawQuery();
    return url + (isNullOrEmpty(qs) ? "" : "?" + qs);
  }
}
