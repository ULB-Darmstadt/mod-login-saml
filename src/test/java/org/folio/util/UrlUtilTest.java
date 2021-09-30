package org.folio.util;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.folio.sso.saml.Constants.Exceptions.MSG_INVALID_XML_RESPONSE;
import static org.folio.sso.saml.Constants.Exceptions.MSG_PRE_UNEXPECTED;

import javax.validation.constraints.NotNull;

import org.folio.junit.rules.HttpMockingVertx;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UrlUtilTest {

  @ClassRule
  public static HttpMockingVertx mock = new HttpMockingVertx();
  
  @BeforeClass
  public static void setupGlobal(TestContext context) {
    // Use the DSL to create the test data. Mocking Vertx rule should ensure that
    // requests made from within Vertx using the WebclientFactory will proxy through
    // our mock server first.
    stubFor(
        get(urlEqualTo("/xml"))
          .willReturn(
            aResponse()
              .withBodyFile("single-idp.xml")
              .withHeader("Content-Type", "application/xml")
          )
      );
    stubFor(
        get(urlEqualTo("/json"))
        .willReturn(
          okJson(
            new JsonObject()
              .put("validjson", true)
              .put("nested", new JsonObject()
                .put("name", "Nested Obj")  
              )
            .encode()
          ))
      );
    stubFor(
        get(urlEqualTo("/xml-incorrect-header"))
        .willReturn(
          aResponse()
            .withBodyFile("single-idp.xml")
            .withHeader("Content-Type", "application/json")
        )
      );
    stubFor(
        get(urlEqualTo("/json-incorrect-header"))
        .willReturn(
          aResponse()
            .withBody(
                new JsonObject()
                .put("validjson", true)
                .put("nested", new JsonObject()
                  .put("name", "Nested Obj")  
                )
              .encode()
            )
            .withHeader("Content-Type", "application/xml")
        )
      );
    stubFor(
        get(urlEqualTo("/"))
        .willReturn(
          aResponse()
            .withBody(
                new JsonObject()
                .put("validjson", true)
                .put("nested", new JsonObject()
                  .put("name", "Nested Obj")  
                )
              .encode()
            )
        )
      );
    stubFor(
        get(anyUrl())
        .withHost(equalTo("localhost"))
        .withPort(1001)
        .willReturn(
          serverError()
        )
      );
  }

  @Test
  public void checkIdpUrl(TestContext context) {
    UrlUtil.checkIdpUrl(appURI("/xml"), mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("", result.getMessage());
      }));
  }

  @Test
  public void checkIdpUrlNon200(TestContext context) {
    // 1001 is a special port for the mock server to respond with a 500.
    UrlUtil.checkIdpUrl("http://localhost:" + 1001, mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertTrue(result.getMessage().startsWith(MSG_PRE_UNEXPECTED));
      }));
  }

  @Test
  public void checkIdpUrlBlank(TestContext context) {
    UrlUtil.checkIdpUrl(appURI("/"), mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
      }));
  }

  @Test
  public void checkIdpUrlJson(TestContext context) {
    UrlUtil.checkIdpUrl(appURI("/json"), mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
      }));
  }
  
  @Test
  public void checkIdpUrlXmlMissreportedType(TestContext context) {
    UrlUtil.checkIdpUrl(appURI("/xml-incorrect-header"), mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("", result.getMessage());
      }));
  }
  
  @Test
  public void checkIdpUrlJsonWithXMLHeader(TestContext context) {
    UrlUtil.checkIdpUrl(appURI("/json-incorrect-header"), mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
      }));
  }
  
  private static String appURI(@NotNull final String path) {
    return "http://localhost:" + mock.vertxPort + ( path.startsWith("/") ? "" : "/" ) + path;
  }
}
