package org.folio.rest.impl;

import static io.restassured.RestAssured.given;

import java.io.IOException;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.RestVerticle;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.restassured.RestAssured;
import io.restassured.http.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

/**
 * Wiremock strips the cors headers we send, so moving these tests to own file. 
 * 
 * @author Steve Osguthorpe
 *
 */
@RunWith(VertxUnitRunner.class)
public class CorsTest {
  private static final Logger log = LogManager.getLogger(SamlAPITest.class);
  private static final String TENANT_NAME = "saml-test"; 
  
  private static final URI okapi = UriBuilder.fromUri("http://localhost:9130").build();
  
  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "saml-test");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", okapi.toString());
  private Vertx vertx;
  
  public static final int PORT = 8081;
  
  @Before
  public void setUp(TestContext context) throws IOException {
    vertx = Vertx.vertx();

    DeploymentOptions options = new DeploymentOptions()
      .setConfig(new JsonObject()
        .put("http.port", PORT)
      );

    RestAssured.port = PORT;
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    vertx.deployVerticle(new RestVerticle(), options, context.asyncAssertSuccess());
  }

  @After
  public void tearDown(TestContext context) {
    // Need to clear singleton to maintain test/order independence
    vertx.close(context.asyncAssertSuccess());
  }
  
  @Test
  public void callbackCorsTests() throws IOException {
    String origin = "http://localhost";

    Header ACCESS_CONTROL_REQ_HEADERS_HEADER = new Header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toString(),
        "content-type,x-okapi-tenant,x-okapi-token");
    
    Header ACCESS_CONTROL_REQUEST_METHOD_HEADER = new Header(
        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toString(), "POST");

    log.info("=== Test CORS preflight - OPTIONS /saml/callback - success ===");
    given()
      .header(new Header(HttpHeaders.ORIGIN.toString(), origin))
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(ACCESS_CONTROL_REQ_HEADERS_HEADER)
      .header(ACCESS_CONTROL_REQUEST_METHOD_HEADER)
      .options("/saml/callback")
      
    .then()
      .statusCode(204)
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), Matchers.equalTo(origin))
      .header(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), Matchers.equalTo("true"));
  }
}
