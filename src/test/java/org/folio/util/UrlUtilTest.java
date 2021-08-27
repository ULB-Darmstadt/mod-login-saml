package org.folio.util;

import static org.folio.sso.saml.Constants.Exceptions.*;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import org.folio.junit.rules.HttpMockingVertx;
import org.folio.rest.tools.utils.NetworkUtils;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;

@RunWith(VertxUnitRunner.class)
public class UrlUtilTest {

  @ClassRule
  public static HttpMockingVertx mock = new HttpMockingVertx();

  @Test
  public void checkIdpUrl(TestContext context) {
    UrlUtil.checkIdpUrl("http://localhost:" + mock.vertxPort + "/xmls", mock.vertx)
      .onComplete(context.asyncAssertSuccess(result -> {
        context.assertEquals("", result.getMessage());
      }));
  }

//  @Test
//  public void checkIdpUrlNon200(TestContext context) {
//    // 1001 is a special port for the mock server to respond with a 500.
//    UrlUtil.checkIdpUrl("http://localhost:" + 1001, vertx)
//      .onComplete(context.asyncAssertSuccess(result -> {
//        context.assertTrue(result.getMessage().startsWith(MSG_PRE_UNEXPECTED));
//      }));
//  }
//
//  @Test
//  public void checkIdpUrlBlank(TestContext context) {
//    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/", vertx)
//      .onComplete(context.asyncAssertSuccess(result -> {
//        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
//      }));
//  }
//
//  @Test
//  public void checkIdpUrlJson(TestContext context) {
//    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/json", vertx)
//      .onComplete(context.asyncAssertSuccess(result -> {
//        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
//      }));
//  }
//  
//  @Test
//  public void checkIdpUrlXmlMissreportedType(TestContext context) {
//    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/xml-incorrect-header", vertx)
//      .onComplete(context.asyncAssertSuccess(result -> {
//        context.assertEquals("", result.getMessage());
//      }));
//  }
//  
//  @Test
//  public void checkIdpUrlJsonWithXMLHeader(TestContext context) {
//    UrlUtil.checkIdpUrl("http://localhost:" + MOCK_PORT + "/json-incorrect-header", vertx)
//      .onComplete(context.asyncAssertSuccess(result -> {
//        context.assertEquals(MSG_INVALID_XML_RESPONSE, result.getMessage());
//      }));
//  }
}
