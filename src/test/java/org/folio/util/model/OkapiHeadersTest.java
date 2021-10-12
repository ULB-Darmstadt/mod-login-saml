package org.folio.util.model;

import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_OKAPI_URL;
import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_TENANT;
import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_TOKEN;
import static org.junit.Assert.assertEquals;

import org.folio.util.model.OkapiHeaders.MissingHeaderException;
import org.junit.Test;

public class OkapiHeadersTest {

  @Test
  public void testVerifyOkapiSecuredInteropHeadersAllPresent() throws MissingHeaderException {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");

    // Both should pass.
    okapiHeaders.interopHeaders();
    okapiHeaders.securedInteropHeaders();
  }

  @Test
  public void testVerifyOkapiInteropHeadersMissingToken() throws MissingHeaderException {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setUrl("url");

    // This should pass...
    okapiHeaders.interopHeaders();
    try {
      // This shouldn't.
      okapiHeaders.securedInteropHeaders();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_TOKEN, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingTenant() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    try {
      okapiHeaders.interopHeaders();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_TENANT, e.getMessage());
    }
    
    try {
      okapiHeaders.securedInteropHeaders();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_TENANT, e.getMessage());
    }
  }

  @Test
  public void testVerifyOkapiHeadersMissingUrl() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    try {
      okapiHeaders.interopHeaders();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_OKAPI_URL, e.getMessage());
    }
    
    try {
      okapiHeaders.securedInteropHeaders();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_OKAPI_URL, e.getMessage());
    }
  }
}
