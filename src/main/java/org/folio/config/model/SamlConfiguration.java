package org.folio.config.model;

/**
 * POJO for strongly typed configuration client
 *
 * @author Steve Osguthorpe<steve.osguthorpe@k-int.com>
 */
public interface SamlConfiguration {

  public String getIdpUrl();

  public String getKeystore();

  public String getKeystorePassword();

  public String getMetadataInvalidated();

  public String getOkapiUrl();

  public String getPrivateKeyPassword();

  public String getSamlAttribute();

  public String getSamlBinding();
  
  public String getUserCreateMissing();
  
  public boolean hasDefaultUserData();
  
  public String getUserDefaultEmailAttribute();
  
  public String getUserDefaultFirstNameAttribute();
  
  public String getUserDefaultFirstNameDefault();
  
  public String getUserDefaultLastNameAttribute();

  public String getUserDefaultLastNameDefault();

  public String getUserDefaultPatronGroup();

  public String getUserDefaultUsernameAttribute();

  public String getUserProperty();
}
