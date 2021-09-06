package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.and;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.badRequest;
import static com.github.tomakehurst.wiremock.client.WireMock.created;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.forbidden;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.notMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.reset;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.folio.util.Base64AwareXsdMatcher.matchesBase64XsdInClasspath;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.junit.rules.HttpMockingVertx;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.sso.saml.Client;
import org.folio.util.TestingClasspathResolver;
import org.hamcrest.Matchers;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.pac4j.core.context.HttpConstants;
import org.w3c.dom.ls.LSResourceResolver;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author rsass
 * @author Steve Osguthorpe
 */
@FixMethodOrder(MethodSorters.JVM) // Preserve the ordering of declared tests.
public class SamlAPITest {
  private static final Logger log = LogManager.getLogger(SamlAPITest.class);
  private static final String TENANT_NAME = "saml-test"; 
  
  private static final URI okapi = UriBuilder.fromUri("http://localhost:9130").build();
  
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "saml-test");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", okapi.toString());
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";

  @Rule
  public TestName testName = new TestName();
  
  // Instead of using vertx runner to run inline. We use our rule which creates
  // a vertx instance and sets the proxy option so that all traffic is passed
  // through the sister WireMock instance.
  @ClassRule
  public static HttpMockingVertx mock = new HttpMockingVertx();
  
  @BeforeClass
  public static void setupGlobal() throws UnsupportedEncodingException {
    // Use the DSL to create the test data. Mocking Vertx rule should ensure that
    // requests made from within Vertx using the WebclientFactory will proxy through
    // our mock server first.
    stubFor(
      any(urlPathMatching(okapi.getPath() + "/.*"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withHeader( TENANT_HEADER.getName(), notMatching(TENANT_HEADER.getValue()).or(absent()) )
      .willReturn(serverError().withStatusMessage("Invalid or missing tenant"))
    );
    stubFor(
      any(urlPathMatching(okapi.getPath() + "/.*"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withHeader(TOKEN_HEADER.getName(), absent() )
      .willReturn(unauthorized())
    );
    stubFor(
      any(urlPathMatching(okapi.getPath() + "/.*"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withHeader(TOKEN_HEADER.getName(), notMatching(TOKEN_HEADER.getValue()) )
      .willReturn(forbidden())
    );
      
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
        
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBodyFile("_mod-config-saml.json")
      )
    );
    
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml AND code== saml.attribute)"))
        
      .willReturn(
        okJson(new JsonObject()
          .put("totalRecords", 0)
          .put("configs", new JsonArray()).encode()
      ))
    );
    
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml AND code== idp.url)"))
        
      .willReturn(
        okJson(new JsonObject()
          .put("totalRecords", 1)
          .put("configs", new JsonArray()
            .add(new JsonObject()
              .put("id", "60eead4f-de97-437c-9cb7-09966ce50e49")
              .put("module", "LOGIN-SAML")
              .put("configName", "saml")
              .put("code", "idp.url")
              .put("value", "https://idp.ssocircle.com" ))).encode()
      ))
    );
    
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml AND code== metadata.invalidated)"))
        
      .willReturn(
        okJson(new JsonObject()
          .put("totalRecords", 1)
          .put("configs", new JsonArray()
            .add(new JsonObject()
              .put("id", "717bf1d1-a5a3-460f-a0de-29e6b70a0027")
              .put("module", "LOGIN-SAML")
              .put("configName", "saml")
              .put("code", "metadata.invalidated")
              .put("value", "false" ))).encode()
      ))
    );
    
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml AND code== user.property)"))
        
      .willReturn(
        okJson(new JsonObject()
          .put("totalRecords", 0)
          .put("configs", new JsonArray()).encode()
      ))
    );
    
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/users"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("query", equalTo("externalSystemId==\"saml-user-id\""))
        
      .willReturn(
        okJson(new JsonObject()
          .put("totalRecords", 1)
          .put("users", new JsonArray()
            .add(new JsonObject()
              .put("id", "saml-user")
              .put("username", "samluser")
              .put("active", true))).encode()
      ))
    );
    
    stubFor(
      post(urlPathMatching(okapi.getPath() + "/token"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withRequestBody(
          and(
            matchingJsonPath("$.payload[?(@.sub=='samluser')]"),
            matchingJsonPath("$.payload[?(@.user_id=='saml-user')]")
          )
        )
      .willReturn(
        created()
          .withBody(new JsonObject()
            .put("token", "saml-token").encode()
      ))
    );
    
    // Return the correct code. No persistence happens here, but we should be able
    // to replace the entries in the mock to reflect the new state.
    stubFor(
      put(urlPathMatching(okapi.getPath() + "/configurations/entries/.*"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        
      .willReturn(
        noContent()
      )
    );
    
    stubFor(
      post(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
      .willReturn(
        created()
          .withBody(
            "{{parseJson request.body 'bodyMap'}}" +
            "{{set-value bodyMap 'id' (randomValue type='UUID')}}" +
            "{{{json bodyMap}}}").withTransformers("response-template")
      )
    );
    
    stubFor(
      get(urlEqualTo("/xml"))
        .withHost(equalTo("mock-idp"))
        .willReturn(
          aResponse()
            .withBodyFile("meta-idp.xml")
            .withHeader("Content-Type", "application/xml")));
    

    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.port = mock.vertxPort;
  }

  @Before
  public void printTestMethod() {
    log.info("Running {}", testName.getMethodName());
  }

  @Test
  public void checkEndpointTests() {

    // Force a reinit.
    Client.forceReinit(TENANT_NAME);
    
    // bad
    given()
      .get("/saml/check")
      .then()
      .statusCode(400);

    // missing OKAPI_URL_HEADER -> "active": false
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .get("/saml/check")
    .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlCheck.json"))
      .body("active", Matchers.equalTo(false));
    
    // good -> "active": true
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/check")
    .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlCheck.json"))
      .body("active", Matchers.equalTo(true));

  }

  @Test
  public void loginEndpointTestsBad() {
    // empty body
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .post("/saml/login")
      .then()
      .statusCode(400);
  }

  @Test
  public void loginEndpointTestsGood() {
    // good
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
    .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlLogin.json"))
      .body("bindingMethod", Matchers.equalTo("POST"))
      .body("relayState", Matchers.equalTo(STRIPES_URL))
      .statusCode(200);

    // AJAX 401
    given()
      .header(HttpConstants.AJAX_HEADER_NAME, HttpConstants.AJAX_HEADER_VALUE)
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
      .then()
      .statusCode(401);
  }

  @Test
  public void regenerateEndpointTests() throws IOException {


    LSResourceResolver resolver = new TestingClasspathResolver("schemas/");

    String metadata = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
    .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlRegenerateResponse.json"))
      .body("fileContent", matchesBase64XsdInClasspath("schemas/saml-schema-metadata-2.0.xsd", resolver))
      .statusCode(200)
      .extract().asString();

    // Update the config
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
        .withIdpUrl(URI.create("http://mock-idp/xml"))
        .withSamlAttribute("UserID")
        .withSamlBinding(SamlConfigRequest.SamlBinding.REDIRECT)
        .withUserProperty("externalSystemId")
        .withUserCreateMissing(false)
        .withOkapiUrl(URI.create("http://localhost:9130"));

    String jsonString = Json.encode(samlConfigRequest);

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonString)
      .put("/saml/configuration")
    .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"));

    // Get metadata, ensure it's changed
    String regeneratedMetadata = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
      .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlRegenerateResponse.json"))
      .body("fileContent", matchesBase64XsdInClasspath("schemas/saml-schema-metadata-2.0.xsd", resolver))
      .statusCode(200)
      .extract().asString();

    assertNotEquals(metadata, regeneratedMetadata);
  }

  @Test
  public void callbackEndpointTests() {


    final String testPath = "/test/path";

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", STRIPES_URL + testPath)
      .post("/saml/callback")
      
    .then()
      .statusCode(302)
      .header("Location", Matchers.containsString(URLEncoder.encode(testPath, StandardCharsets.UTF_8)))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");

  }

  @Test
  public void healthEndpointTests() {

    // good
    given()
      .get("/admin/health")
    .then()
      .statusCode(200);

  }

  @Test
  public void getConfigurationEndpoint() {

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
    .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"))
      .body("idpUrl", Matchers.equalTo("https://idp.ssocircle.com"))
      .body("samlBinding", Matchers.equalTo("POST"))
      .body("metadataInvalidated", Matchers.equalTo(Boolean.FALSE));
  }

  @Test
  public void putConfigurationEndpoint() {
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
      .withIdpUrl(URI.create("http://mock-idp/xml"))
      .withSamlAttribute("UserID")
      .withUserCreateMissing(false)
      .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
      .withUserProperty("externalSystemId")
      .withOkapiUrl(URI.create("http://localhost:9130"));

    String jsonString = Json.encode(samlConfigRequest);

    // PUT
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body(jsonString)
      .put("/saml/configuration")
    .then()
      .statusCode(200)
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"));
  }

  @Test
  public void testWithConfiguration400() throws IOException {
    
    // Clear the mock data.
    reset();
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
        
      .willReturn(
        badRequest().withStatusMessage("Simulated error response")
      )
    );

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/configuration")
      
    .then()
      .statusCode(500)
      .contentType(ContentType.TEXT)
      .body(Matchers.containsString("Cannot get configuration"));
  }


  @Test
  public void regenerateEndpointNoIdP() throws IOException {
    // Remove all mock stubs.
    reset();
    
    // No IDP in config.
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
        
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBodyFile("_mod-config-saml-no-idp.json")
      )
    );

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
      
    .then()
      .statusCode(500)
      .contentType(ContentType.TEXT)
      .body(Matchers.containsString("There is no IdP configuration stored"));
  }

  @Test
  public void regenerateEndpointNoKeystore() throws IOException {
    // Remove all mock stubs.
    reset();
    
    // No Keystore in config.
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
        
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBodyFile("_mod-config-saml-no-keystore.json")
      )
    );

    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")
    .then()
      .statusCode(500)
      .contentType(ContentType.TEXT)
      .body(Matchers.containsString("No KeyStore stored in configuration and regeneration is not allowed"));
  }
}
