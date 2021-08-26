package org.folio.util;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.matching.RequestPattern;
import com.github.tomakehurst.wiremock.matching.UrlPattern;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import io.vertx.core.Vertx;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.web.client.impl.HttpContext;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;

public class ServerMock {
  private final URI baseUri;
  private final WireMock mock;
  private final WireMockServer wireMockServer;
  
  public URI getUri() {
    return this.baseUri;
  }
  
  private final Vertx vertxInst;

  public ServerMock (int port) throws URISyntaxException {
    this(new URI("http", null, "localhost", port, null, null, null));
  }

  private ServerMock (URI baseUri) throws URISyntaxException {
    this.baseUri = baseUri;
    this.vertxInst = Vertx.vertx();

    this.wireMockServer = new WireMockServer(options().port(baseUri.getPort())); //No-args constructor will start on port 8080, no HTTPS
    this.wireMockServer.start();

    this.mock = new WireMock(baseUri.getScheme(), baseUri.getHost(), baseUri.getPort(), baseUri.getPath());

    
    StubMapping sm = this.mock.register(
        get(urlEqualTo("/xml"))
       .willReturn(
         aResponse()
           .withHeader("Content-Type", "text/plain")
           .withBody("Hello world!")));
  }
  
  public boolean shouldProxy ( HttpContext<?> context ) {
    final RequestOptions reqOpts = context.requestOptions();
    
    final List<StubMapping> mappings = this.mock.allStubMappings().getMappings();
    final String uriToMatch = reqOpts.getURI();
    boolean proxy = false;
    for (int i=0; !proxy && i<mappings.size(); i++) {
      final StubMapping mapping = mappings.get(i);
      final RequestPattern reqP = mapping.getRequest();
      final UrlPattern urlP = reqP.getUrlMatcher();
      proxy = urlP.match(uriToMatch).isExactMatch();
    }
    return proxy;
  }
  
  public void close() {
    wireMockServer.stop();
  }
}
