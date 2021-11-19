package org.folio.sso.saml;

import java.time.Period;

public interface Constants {
  public static final String MODULE_NAME = "LOGIN-SAML";
  public static final int BLOCKING_OP_TIMEOUT_SECONDS = 5;
  public static final String COOKIE_RELAY_STATE = "relayState";
  public static final String QUERY_PARAM_CSRF_TOKEN = "csrfToken";
  
  public static interface Exceptions {
    public static final String MSG_MISSING_HDR_OKAPI_URL = "Missing Okapi URL";
    public static final String MSG_MISSING_HDR_TENANT = "Missing Tenant";
    public static final String MSG_MISSING_HDR_TOKEN = "Missing Token";
    public static final String MSG_INVALID_XML_RESPONSE = "Response is not valid XML";
    public static final String MSG_PRE_ERROR_CONNECTION = "Connection error: ";
    public static final String MSG_PRE_UNEXPECTED = "Unexpected error: ";
  }
  
  public static interface Config {
    public static final String VERSION_PROPERTY = "_version";
    public static final String ENDPOINT_ENTRIES = "/configurations/entries";
    public static final String ENDPOINT_CALLBACK = "/saml/callback";
    
    public static final String I18N_DEFAULT_LANG = "en";
    
    public static final String CONFIG_NAME = "saml";
    
    public static final String DEFAULT_USER = "samlDefaultUser";
    public static final String KEYSTORE_FILE = "keystore.file";
    public static final String KEYSTORE_PASSWORD = "keystore.password"; // NOSONAR
    public static final String KEYSTORE_PRIVATEKEY_PASSWORD = "keystore.privatekey.password"; // NOSONAR
    public static final String IDP_URL = "idp.url";
    public static final String SAML_BINDING = "saml.binding";
    public static final String SAML_ATTRIBUTE = "saml.attribute";
    public static final String USER_PROPERTY = "user.property";
    public static final String USER_CREATE_MISSING = "user.createMissing";
    public static final String METADATA_INVALIDATED = "metadata.invalidated";
    public static final String OKAPI_URL= "okapi.url";
    public static final String PATRON_GRP = "patronGroup";

    public static final String DU_UN_ATT = DEFAULT_USER + ".usernameAttribute";
    public static final String DU_EMAIL_ATT = DEFAULT_USER + ".emailAttribute";
    public static final String DU_FIRST_NM_ATT = DEFAULT_USER + ".firstNameAttribute";
    public static final String DU_LAST_NM_ATT = DEFAULT_USER + ".lastNameAttribute";
    public static final String DU_FIRST_NM_DEFAULT = DEFAULT_USER + ".firstNameDefault";
    public static final String DU_LAST_NM_DEFAULT = DEFAULT_USER + ".lastNameDefault";
    public static final String DU_PATRON_GRP = DEFAULT_USER + "." + PATRON_GRP;
    
    public static final String HOME_INSTITUTION = "homeInstitution.";
    public static final String INST_ID = "id";
    public static final String HI_ID = HOME_INSTITUTION + INST_ID;
    public static final String HI_PATRON_GRP = HOME_INSTITUTION + PATRON_GRP;

    public static final String SELECTED_IDPS = "selectedIdentityProviders";
    
    public static final Period CERTIFICATE_VALIDITY = Period.ofYears(3);
  }
  
  
}
