/**
 * 
 */
package org.folio.rest.impl;

import static org.junit.Assert.assertTrue;

import org.folio.junit.rules.HttpMockingVertx;
import org.junit.Rule;
import org.junit.Test;

/**
 * Testing SAML IDP Discovery Profile 4.1 HTTP Request to Discovery Service
 * 
 * @author Steve Osguthorpe
 *
 */
public class SAMLIDPDiscoProto4_1Test {

  @Rule
  public HttpMockingVertx mock = new HttpMockingVertx();
  
  @Test
  public void loginEndpointFromValidOrigin() {
    // Redirect to discovery initiator
    // - If single IDP or only 1 selected in Config,
    //   then isPassive=true.
    assertTrue(false);
  }
  
  @Test
  public void loginEndpointInvalidOrigin() {
    // Error invalid origin. Access denied 403 error.
    assertTrue(false);
  }
  
  @Test
  public void invalidServiceProviderEntityID() {
    // Rejected. 
    
    // Test missing. - 400 bad request, Mandatory parameter
    // Test invalid or unknown. - 403 bad request, Invalid value
    assertTrue(false);
  }

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

  @Test
  public void validServiceProviderEntityIDInvalidOriginHeader() {
    // Error invalid origin. Access denied 403 error.
    assertTrue(false);
  }

  @Test
  public void validReturnSepcified() {
    // Validate this URL.
    // - Spec says "query string MUST NOT contain a parameter with the same name as the value of the returnIDParam"
    // - In addition to this, we specify it must match allow origins entry or equal to <idpdisc:DiscoveryResponse> metadata element
    
    // Returns a redirect to value specified.
    
    // isPassive=true : Now value of default IDP and not return.
    assertTrue(false);
  }

  @Test
  public void invalidReturnSepcified() {
    // Error 403
    assertTrue(false);
  }

  @Test
  public void validPolicySepcified() {
    // Must be equate to value of "urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol:single".
    
    // Returns a redirect to Disco
    assertTrue(false);
  }

  @Test
  public void invalidPolicySepcified() {
    // Error 403
    assertTrue(false);
  }
}
