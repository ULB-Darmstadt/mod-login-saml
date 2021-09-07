package org.folio.util.model;

import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_OKAPI_URL;
import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_TENANT;
import static org.folio.sso.saml.Constants.Exceptions.MSG_MISSING_HDR_TOKEN;
import static org.junit.Assert.assertEquals;

import org.folio.util.model.OkapiHeaders;
import org.folio.util.model.OkapiHeaders.MissingHeaderException;
import org.junit.Test;

public class OKapiHeadersTest {

  @Test
  public void testVerifyOkapiHeadersAllPresent() throws MissingHeaderException {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setToken("token");
    okapiHeaders.setUrl("url");
    okapiHeaders.verifySecuredInteropValues();
  }

  @Test
  public void testVerifyOkapiHeadersMissingToken() {
    OkapiHeaders okapiHeaders = new OkapiHeaders();
    okapiHeaders.setTenant("tenant");
    okapiHeaders.setUrl("url");
    try {
      okapiHeaders.verifySecuredInteropValues();
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
      okapiHeaders.verifySecuredInteropValues();
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
      okapiHeaders.verifySecuredInteropValues();
    } catch (MissingHeaderException e) {
      assertEquals(MSG_MISSING_HDR_OKAPI_URL, e.getMessage());
    }
  }
}
