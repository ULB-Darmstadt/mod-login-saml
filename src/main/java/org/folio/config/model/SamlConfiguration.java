package org.folio.config.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POJO for strongly typed configuration client
 *
 * @author rsass
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SamlConfiguration {

  private static final String DEFAULT_USER = "samlDefaultUser";
  
  public static final String KEYSTORE_FILE_CODE = "keystore.file";
  public static final String KEYSTORE_PASSWORD_CODE = "keystore.password"; // NOSONAR
  public static final String KEYSTORE_PRIVATEKEY_PASSWORD_CODE = "keystore.privatekey.password"; // NOSONAR
  public static final String IDP_URL_CODE = "idp.url";
  public static final String SAML_BINDING_CODE = "saml.binding";
  public static final String SAML_ATTRIBUTE_CODE = "saml.attribute";
  public static final String USER_PROPERTY_CODE = "user.property";
  public static final String USER_CREATE_MISSING_CODE = "user.createMissing";
  public static final String METADATA_INVALIDATED_CODE = "metadata.invalidated";
  public static final String OKAPI_URL= "okapi.url";

  public static final String DU_UN_ATT = DEFAULT_USER + ".usernameAttribute";
  public static final String DU_EMAIL_ATT = DEFAULT_USER + ".emailAttribute";
  public static final String DU_FIRST_NM_ATT = DEFAULT_USER + ".firstNameAttribute";
  public static final String DU_LAST_NM_ATT = DEFAULT_USER + ".lastNameAttribute";
  public static final String DU_FIRST_NM_DEFAULT = DEFAULT_USER + ".firstNameDefault";
  public static final String DU_LAST_NM_DEFAULT = DEFAULT_USER + ".lastNameDefault";
  public static final String DU_PATRON_GRP = DEFAULT_USER + ".patronGroup";

  @JsonProperty(IDP_URL_CODE)
  private String idpUrl;
  
  @JsonProperty(KEYSTORE_FILE_CODE)
  private String keystore;
  
  @JsonProperty(KEYSTORE_PASSWORD_CODE)
  private String keystorePassword;
  
  @JsonProperty(KEYSTORE_PRIVATEKEY_PASSWORD_CODE)
  private String privateKeyPassword;
  
  @JsonProperty(SAML_BINDING_CODE)
  private String samlBinding;
  
  @JsonProperty(SAML_ATTRIBUTE_CODE)
  private String samlAttribute;
  
  @JsonProperty(USER_PROPERTY_CODE)
  private String userProperty;
  
  @JsonProperty(METADATA_INVALIDATED_CODE)
  private String metadataInvalidated = "true";
  
  @JsonProperty(USER_CREATE_MISSING_CODE)
  private String userCreateMissing = "false";
 
  @JsonProperty(DU_UN_ATT)
  private String userDefaultUsernameAttribute;
  
  @JsonProperty(DU_EMAIL_ATT)
  private String userDefaultEmailAttribute;

  @JsonProperty(DU_FIRST_NM_ATT)
  private String userDefaultFirstNameAttribute;

  @JsonProperty(DU_FIRST_NM_DEFAULT)
  private String userDefaultFirstNameDefault;

  @JsonProperty(DU_LAST_NM_ATT)
  private String userDefaultLastNameAttribute;

  @JsonProperty(DU_LAST_NM_DEFAULT)
  private String userDefaultLastNameDefault;

  @JsonProperty(DU_PATRON_GRP)
  private String userDefaultPatronGroup;

  @JsonProperty(OKAPI_URL)
  private String okapiUrl;

  public String getIdpUrl() {
    return idpUrl;
  }

  public String getKeystore() {
    return keystore;
  }

  public String getKeystorePassword() {
    return keystorePassword;
  }

  public String getMetadataInvalidated() {
    return metadataInvalidated;
  }

  public String getOkapiUrl() {
    return okapiUrl;
  }

  public String getPrivateKeyPassword() {
    return privateKeyPassword;
  }

  public String getSamlAttribute() {
    return samlAttribute;
  }

  public String getSamlBinding() {
    return samlBinding;
  }
  
  public String getUserCreateMissing () {
    return userCreateMissing;
  }
  
  private boolean defaultUserPresent = false;
  public boolean hasDefaultUserData () {
    return defaultUserPresent;
  }
  
  public String getUserDefaultEmailAttribute () {
    return userDefaultEmailAttribute;
  }
  
  public String getUserDefaultFirstNameAttribute () {
    return userDefaultFirstNameAttribute;
  }
  
  public String getUserDefaultFirstNameDefault () {
    return userDefaultFirstNameDefault;
  }
  
  public String getUserDefaultLastNameAttribute () {
    return userDefaultLastNameAttribute;
  }

  public String getUserDefaultLastNameDefault () {
    return userDefaultLastNameDefault;
  }

  public String getUserDefaultPatronGroup () {
    return userDefaultPatronGroup;
  }

  public String getUserDefaultUsernameAttribute () {
    return userDefaultUsernameAttribute;
  }

  public String getUserProperty() {
    return userProperty;
  }

  public void setIdpUrl(String idpUrl) {
    this.idpUrl = idpUrl;
  }

  public void setKeystore(String keystore) {
    this.keystore = keystore;
  }

  public void setKeystorePassword(String keystorePassword) {
    this.keystorePassword = keystorePassword;
  }

  public void setMetadataInvalidated(String metadataInvalidated) {
    this.metadataInvalidated = metadataInvalidated;
  }

  public void setOkapiUrl(String okapiUrl) {
    this.okapiUrl = okapiUrl;
  }

  public void setPrivateKeyPassword(String privateKeyPassword) {
    this.privateKeyPassword = privateKeyPassword;
  }

  public void setSamlAttribute(String samlAttribute) {
    this.samlAttribute = samlAttribute;
  }

  public void setSamlBinding(String samlBinding) {
    this.samlBinding = samlBinding;
  }

  public void setUserCreateMissing (String userCreateMissing) {
    this.userCreateMissing = userCreateMissing;
  }

  public void setUserDefaultEmailAttribute (String userDefaultEmailAttribute) {
    defaultUserPresent = (userDefaultEmailAttribute != null);
    this.userDefaultEmailAttribute = userDefaultEmailAttribute;
  }

  public void setUserDefaultFirstNameAttribute (
      String userDefaultFirstNameAttribute) {
    defaultUserPresent = (userDefaultFirstNameAttribute != null);
    this.userDefaultFirstNameAttribute = userDefaultFirstNameAttribute;
  }

  public void setUserDefaultFirstNameDefault (
      String userDefaultFirstNameDefault) {
    defaultUserPresent = (userDefaultFirstNameDefault != null);
    this.userDefaultFirstNameDefault = userDefaultFirstNameDefault;
  }

  public void setUserDefaultLastNameAttribute (
      String userDefaultLastNameAttribute) {
    defaultUserPresent = (userDefaultLastNameAttribute != null);
    this.userDefaultLastNameAttribute = userDefaultLastNameAttribute;
  }

  public void setUserDefaultLastNameDefault (String userDefaultLastNameDefault) {
    defaultUserPresent = (userDefaultLastNameDefault != null);
    this.userDefaultLastNameDefault = userDefaultLastNameDefault;
  }

  public void setUserDefaultPatronGroup (String userDefaultPatronGroup) {
    defaultUserPresent = (userDefaultPatronGroup != null);
    this.userDefaultPatronGroup = userDefaultPatronGroup;
  }
  
  public void setUserDefaultUsernameAttribute (
      String userDefaultUsernameAttribute) {
    defaultUserPresent = (userDefaultUsernameAttribute != null);
    this.userDefaultUsernameAttribute = userDefaultUsernameAttribute;
  }

  public void setUserProperty(String userProperty) {
    this.userProperty = userProperty;
  }
}
