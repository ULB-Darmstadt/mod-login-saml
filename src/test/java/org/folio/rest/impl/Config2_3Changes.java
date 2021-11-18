package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;

import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.junit.rules.HttpMockingVertx;
import org.hamcrest.Matchers;
import org.junit.*;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;

import io.restassured.RestAssured;
import io.restassured.http.Header;

/**
 * @author Steve Osguthorpe
 */
@FixMethodOrder(MethodSorters.JVM) // Preserve the ordering of declared tests.
public class Config2_3Changes {
  private static final Logger log = LogManager.getLogger(Config2_3Changes.class);
  private static final String TENANT_NAME = "saml-test"; 

  private static final URI okapi = UriBuilder.fromUri("http://localhost:9130").build();

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "saml-test");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", okapi.toString());
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");

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
  public static void setupGlobal() {
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.port = mock.vertxPort;
  }

  @Before
  public void setup() {
    log.info("Running {}", testName.getMethodName());

    // Clear wiremock before each test.
    removeAllMappings();
    
    // Reinit the config client for each test.
    
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
  }

  @Test
  public void checkConfigNoVersion() {
    // Mock mod config.
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

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
        
    .then()
      .statusCode(200)
      .body("_version", Matchers.nullValue()) // Version null and homeInstitution not required.
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"));
  }
  
  @Test
  public void checkConfigPre2_3() {
    // Mock mod config.
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
  
        .willReturn(
            aResponse()
            .withHeader("Content-Type", "application/json")
            .withBodyFile("_mod-config-saml-pre2_3.json")
            )
        );

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
        
    .then()
      .statusCode(200)
      .body("_version", Matchers.notNullValue()) // Pre 2.3 did not require homeInstitution
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"));
  }
  
  @Test
  public void checkConfigInstitutionData() {
    // Mock mod config.
    var configMapping = stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
  
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBodyFile("_mod-config-saml-v2_3-no-inst.json")
          )
        );

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
        
    .then()
      .statusCode(200)
      .body("_version", Matchers.notNullValue()) // 2.3 requires homeInstitution, should fail.
      .body(Matchers.not(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json")));
    
    // Replace config with one that includes valid institution data
    removeStub(configMapping);
    stubFor(
      get(urlPathMatching(okapi.getPath() + "/configurations/entries"))
        .withHost(equalTo(okapi.getHost()))
        .withPort(okapi.getPort())
        .withQueryParam("limit", equalTo("10000"))
        .withQueryParam("query", equalTo("(module==LOGIN-SAML AND configName==saml)"))
  
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBodyFile("_mod-config-saml-v2_3.json")
          )
        );

    // GET
    given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .get("/saml/configuration")
        
    .then()
      .statusCode(200)
      .body("_version", Matchers.notNullValue()) // 2.3 require homeInstitution.
      .body(matchesJsonSchemaInClasspath("apidocs/raml/schemas/SamlConfig.json"))
      
      .body("selectedIdentityProviders", Matchers.hasSize(1));
  }
}
