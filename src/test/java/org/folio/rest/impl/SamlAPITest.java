package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.folio.sso.saml.Constants.COOKIE_RELAY_STATE;
import static org.folio.util.Base64AwareXsdMatcher.matchesBase64XsdInClasspath;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.junit.rules.HttpMockingVertx;
import org.folio.rest.jaxrs.model.HomeInstitution;
import org.folio.rest.jaxrs.model.SamlConfigRequest;
import org.folio.sso.saml.Client;
import org.folio.util.TestingClasspathResolver;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.pac4j.core.context.HttpConstants;
import org.w3c.dom.ls.LSResourceResolver;

import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
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

  static {
    // In order to proxy https traffic the proxy needs to act as a man
    // in the middle. We enable the trust of all certs for this reason here.

    // Class rule runs before our @BeforeClass, so we set in a static block here.
    System.setProperty("TRUST_ALL_CERTIFICATES", "true");
  }

  @AfterClass
  public static void teardownGlobal() {
    System.clearProperty("TRUST_ALL_CERTIFICATES");
  }

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
    
    stubFor(
        get(urlEqualTo("/xml"))
        .withHost(equalTo("mock-idp"))
        .willReturn(
            aResponse()
            .withBodyFile("single-idp.xml")
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
    

    // Missing entitiyID
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
      .post("/saml/login")
    .then()
      .statusCode(400);
  }

  @Test
  public void loginEndpointTestsGood() {
    // good
    ExtractableResponse<Response> resp = given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .header(JSON_CONTENT_TYPE_HEADER)
        .queryParam("entityID", "https://idp.ssocircle.com")
        .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
        .post("/saml/login")
        .then()
        .contentType(ContentType.JSON)
        .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
        .body("bindingMethod", Matchers.equalTo("POST"))
        .statusCode(200)
        .extract();

    String cookie = URLDecoder.decode( resp.cookie(COOKIE_RELAY_STATE), StandardCharsets.UTF_8);
    String relayState = resp.body().jsonPath().getString(COOKIE_RELAY_STATE);
    assertEquals(cookie, relayState);

    // stripesUrl w/ query args
    resp = given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .header(JSON_CONTENT_TYPE_HEADER)
        .queryParam("entityID", "https://idp.ssocircle.com")
        .body("{\"stripesUrl\":\"" + STRIPES_URL + "?foo=bar\"}")
        .post("/saml/login")
        .then()
        .contentType(ContentType.JSON)
        .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
        .body("bindingMethod", Matchers.equalTo("POST"))
        .statusCode(200)
        .extract();

    cookie = URLDecoder.decode( resp.cookie(COOKIE_RELAY_STATE), StandardCharsets.UTF_8 );
    relayState = resp.body().jsonPath().getString("relayState");
    assertEquals(cookie, relayState);

    // AJAX 401
    given()
    .header(HttpConstants.AJAX_HEADER_NAME, HttpConstants.AJAX_HEADER_VALUE)
    .header(TENANT_HEADER)
    .header(TOKEN_HEADER)
    .header(OKAPI_URL_HEADER)
    .header(JSON_CONTENT_TYPE_HEADER)
    .queryParam("entityID", "https://idp.ssocircle.com")
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
    final String regeneratedMetadata = given()
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
  public void callbackEndpointTests() throws IOException {
    final String testPath = "/test/path";

    log.info("=== Setup - POST /saml/login - need relayState and cookie ===");
    ExtractableResponse<Response> resp = given()
        .header(TENANT_HEADER)
        .header(TOKEN_HEADER)
        .header(OKAPI_URL_HEADER)
        .header(JSON_CONTENT_TYPE_HEADER)
        .body("{\"stripesUrl\":\"" + STRIPES_URL + testPath + "\"}")
        .post("/saml/login")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
        .body("bindingMethod", Matchers.equalTo("POST"))
        .extract();

    String cookie = resp.cookie(COOKIE_RELAY_STATE);
    String relayState = resp.body().jsonPath().getString(COOKIE_RELAY_STATE);

    log.info("=== Test - POST /saml/callback - success ===");
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .cookie(COOKIE_RELAY_STATE, cookie)
      .formParam("SAMLResponse", "saml-response")
      .formParam("RelayState", relayState)
      .post("/saml/callback")
      
    .then()
      .statusCode(302)
      .header("Location", Matchers.containsString(URLEncoder.encode(testPath, "UTF-8")))
      .header("x-okapi-token", "saml-token")
      .cookie("ssoToken", "saml-token");

    log.info("=== Test - POST /saml/callback - failure (wrong cookie) ===");
    given()
    .header(TENANT_HEADER)
    .header(TOKEN_HEADER)
    .header(OKAPI_URL_HEADER)
    .cookie(COOKIE_RELAY_STATE, "bad" + cookie)
    .formParam("SAMLResponse", "saml-response")
    .formParam("RelayState", relayState)
    .post("/saml/callback")
    .then()
    .statusCode(403);

    log.info("=== Test - POST /saml/callback - failure (wrong relay) ===");
    given()
    .header(TENANT_HEADER)
    .header(TOKEN_HEADER)
    .header(OKAPI_URL_HEADER)
    .cookie(COOKIE_RELAY_STATE, cookie)
    .formParam("SAMLResponse", "saml-response")
    .formParam("RelayState", relayState.replace("localhost", "demo"))
    .post("/saml/callback")
    .then()
    .statusCode(403);

    log.info("=== Test - POST /saml/callback - failure (no cookie) ===");
    given()
    .header(TENANT_HEADER)
    .header(TOKEN_HEADER)
    .header(OKAPI_URL_HEADER)
    .formParam("SAMLResponse", "saml-response")
    .formParam("RelayState", relayState)
    .post("/saml/callback")
    .then()
    .statusCode(403);
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
  public void getConfigurationWithVersionEndpoint() {

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
  public void invalidRemoteMetadataCauses500() {

    // MD is cached. Clear it first.
    Client.forceReinit(TENANT_NAME);
    
    // Mock out sso circle to return a json doc instead of XML.
    StubMapping mockIDPMetadata = stubFor(
        get(urlPathEqualTo("/"))
        .withHost(equalTo("idp.ssocircle.com"))
        .willReturn(
            okJson("{ \"empty\": true }"))
        );

    // We can use any endpoint that uses the remote metadata here.
    try {
      given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .get("/saml/regenerate")

      .then()
      .statusCode(500)
      ;
    } finally {
      // Cleanup.
      removeStub( mockIDPMetadata );
    }
  }

  @Test
  public void putConfigurationEndpoint() {
    SamlConfigRequest samlConfigRequest = new SamlConfigRequest()
        .withIdpUrl(URI.create("http://mock-idp/xml"))
        .withSamlAttribute("UserID")
        .withUserCreateMissing(false)
        .withSamlBinding(SamlConfigRequest.SamlBinding.POST)
        .withUserProperty("externalSystemId")
        .withHomeInstitution(new HomeInstitution()
            .withId("http://test.id")
            .withPatronGroup("58109b10-2d1c-11ec-a778-d7c066ba98f4"))
          .withSelectedIdentityProviders(Arrays.asList(new HomeInstitution[] {
            new HomeInstitution()
              .withId("http://test2.id")
              .withPatronGroup("58109b10-2d1c-11ec-a778-d7c066ba98f4")
          }))
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
    .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlConfig.json"))
    .body("homeInstitution", Matchers.aMapWithSize(2))
    .body("selectedIdentityProviders", Matchers.hasSize(1));
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
    .body(Matchers.containsString("There is no metadata configuration stored"));
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
