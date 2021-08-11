package org.folio.sso.saml;

public interface Constants {
  public static final String MODULE_NAME = "LOGIN-SAML";
  
  public interface Config {
    public static final String ENTRIES_ENDPOINT_URL = "/configurations/entries";
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

    public static final String DU_UN_ATT = DEFAULT_USER + ".usernameAttribute";
    public static final String DU_EMAIL_ATT = DEFAULT_USER + ".emailAttribute";
    public static final String DU_FIRST_NM_ATT = DEFAULT_USER + ".firstNameAttribute";
    public static final String DU_LAST_NM_ATT = DEFAULT_USER + ".lastNameAttribute";
    public static final String DU_FIRST_NM_DEFAULT = DEFAULT_USER + ".firstNameDefault";
    public static final String DU_LAST_NM_DEFAULT = DEFAULT_USER + ".lastNameDefault";
    public static final String DU_PATRON_GRP = DEFAULT_USER + ".patronGroup";
  }
  
  
}
