package org.folio.config;

import org.folio.sso.saml.ModuleConfig;
import org.folio.util.model.OkapiHeaders;
import org.junit.Assert;
import org.junit.Test;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class ModuleConfigFromJSONTest {

  private static final String IDP_URL_VALUE = "https://idp.ssocircle.com";
  private static final String KEYSTORE_FILE_VALUE = "keystore file content";
  private static final String KEYSTORE_PASSWORD_VALUE = "p455w0rd";
  private static final String PRIVATEKEY_PASSWORD_VALUE = "p455word";

  @Test
  public void map() {

    JsonArray jsonArray = new JsonArray()
      .add(new JsonObject().put("code", "idp.url").put("value", IDP_URL_VALUE))
      .add(new JsonObject().put("code", "keystore.file").put("value", KEYSTORE_FILE_VALUE))
      .add(new JsonObject().put("code", "keystore.password").put("value", KEYSTORE_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "keystore.privatekey.password").put("value", PRIVATEKEY_PASSWORD_VALUE))
      .add(new JsonObject().put("code", "unknownCode").put("value", "unknownValue"));

    ModuleConfig config = ModuleConfig.fromModConfigJson(
      new OkapiHeaders(), jsonArray
    );

    Assert.assertEquals(IDP_URL_VALUE, config.getIdpUrl());
    Assert.assertEquals(KEYSTORE_FILE_VALUE, config.getKeystore());
    Assert.assertEquals(KEYSTORE_PASSWORD_VALUE, config.getKeystorePassword());
    Assert.assertEquals(PRIVATEKEY_PASSWORD_VALUE, config.getPrivateKeyPassword());
  }
}
