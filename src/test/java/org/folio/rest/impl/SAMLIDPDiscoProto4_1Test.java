/**
 * 
 */
package org.folio.rest.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.junit.Assert.assertTrue;

import java.io.UnsupportedEncodingException;
import java.net.URI;

import javax.ws.rs.core.UriBuilder;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.junit.rules.HttpMockingVertx;
import org.hamcrest.Matchers;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;

import io.restassured.http.ContentType;
import io.restassured.http.Header;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;

/**
 * Testing SAML IDP Discovery Profile 4.1 HTTP Request to Discovery Service
 * 
 * @author Steve Osguthorpe
 *
 */
public class SAMLIDPDiscoProto4_1Test {
  private static final Logger log = LogManager.getLogger(SAMLIDPDiscoProto4_1Test.class);
  private static final String TENANT_NAME = "saml-test"; 

  private static final URI okapi = UriBuilder.fromUri("http://localhost:9130").build();

  private static final Header TENANT_HEADER = new Header("X-Okapi-Tenant", TENANT_NAME);
  private static final Header TOKEN_HEADER = new Header("X-Okapi-Token", "saml-test");
  private static final Header OKAPI_URL_HEADER = new Header("X-Okapi-Url", okapi.toString());
  private static final Header JSON_CONTENT_TYPE_HEADER = new Header("Content-Type", "application/json");
  private static final String STRIPES_URL = "http://localhost:3000";


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
          .withBodyFile("_mod-config-saml_disco.json")
      )
    );
  }
  
  @Ignore
  @Test
  public void loginEndpointFromValidOrigin() {
    
    // Mock out a single IDP's metadata
    stubFor(
      get(urlEqualTo("/metadata"))
      .withHost(equalTo("mock-metadata-target"))
      .willReturn(
        aResponse()
          .withBodyFile("single-idp.xml")
          .withHeader("Content-Type", "application/xml")));
    
    // Should now always supply redirect through disco. Although for single IDP
    // should always default to passive.
    
    ExtractableResponse<Response> resp = given()
      .header(TENANT_HEADER)
      .header(TOKEN_HEADER)
      .header(OKAPI_URL_HEADER)
      .header(JSON_CONTENT_TYPE_HEADER)
      .body("{\"stripesUrl\":\"" + STRIPES_URL + "\"}")
    .post("/saml/login")
      
    .then()
      .contentType(ContentType.JSON)
      .body(matchesJsonSchemaInClasspath("ramls/schemas/SamlLogin.json"))
      .body("bindingMethod", Matchers.equalTo("POST"))
      .statusCode(200)
    .extract();

//    resp.path("_links.next.href")
    
//    assertEquals(cookie, relayState);
    
    // Redirect to discovery initiator
    // - If single IDP or only 1 selected in Config,
    //   then isPassive=true.
    assertTrue(false);
  }
  
  @Ignore
  @Test
  public void loginEndpointInvalidOrigin() {
    // Error invalid origin. Access denied 403 error.
    assertTrue(false);
  }

  @Ignore
  @Test
  public void invalidServiceProviderEntityID() {
    // Rejected. 
    
    // Test missing. - 400 bad request, Mandatory parameter
    // Test invalid or unknown. - 403 bad request, Invalid value
    assertTrue(false);
  }

  @Ignore
  @Test
  public void validServiceProviderEntityID() {
    // Redirect - Check all defaults.
    
    // return: taken from local SP metadata <idpdisc:DiscoveryResponse> element.
    //   Should be the start of url value of the redirect.
    
    // Policy: Omitted, implementation defaults to "urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol:single".
    //   Note: There is currently only 1 valid value for policy.
    
    // returnIDParam: Defaults to 'entityID'. Name of parameter that contains the IDP ID as seen in the metdata.
    //   Redirect should contain this parameter, and it's value should be the value supplied.
    
    // isPassive: Omitted from redirect and defaults to false in implementation. When true the dico service must choose an IDP for the user and redirect without allowing choice. If no
    // choice can be inferred then error.
    //    Note: Our implementation should force a default (home) institution. If no previous selection via cookie is known then
    //    we will always use that.
    
    assertTrue(false);
  }

  @Ignore
  @Test
  public void validServiceProviderEntityIDInvalidOriginHeader() {
    // Error invalid origin. Access denied 403 error.
    assertTrue(false);
  }

  @Ignore
  @Test
  public void validReturnSepcified() {
    // Validate this URL.
    // - Spec says "query string MUST NOT contain a parameter with the same name as the value of the returnIDParam"
    // - In addition to this, we specify it must match allow origins entry or equal to <idpdisc:DiscoveryResponse> metadata element
    
    // Returns a redirect to value specified.
    
    // isPassive=true : Now value of default IDP and not return.
    assertTrue(false);
  }

  @Ignore
  @Test
  public void invalidReturnSepcified() {
    // Error 403
    assertTrue(false);
  }

  @Ignore
  @Test
  public void validPolicySepcified() {
    // Must be equate to value of "urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol:single".
    
    // Returns a redirect to Disco
    assertTrue(false);
  }

  @Ignore
  @Test
  public void invalidPolicySepcified() {
    // Error 403
    assertTrue(false);
  }
}
