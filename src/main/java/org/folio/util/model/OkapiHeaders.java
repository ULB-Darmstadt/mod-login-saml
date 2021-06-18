package org.folio.util.model;

import org.apache.commons.lang3.StringUtils;

/**
 * POJO for Okapi headers parsing
 *
 * @author rsass
 */
public class OkapiHeaders {

  public static final String OKAPI_URL_HEADER = "X-Okapi-URL";
  public static final String OKAPI_TOKEN_HEADER = "X-Okapi-Token";
  public static final String OKAPI_TENANT_HEADER = "X-Okapi-Tenant";
  public static final String OKAPI_PERMISSIONS_HEADER = "X-Okapi-Permissions";

  private String url;
  private String token;
  private String tenant;
  private String permissions;
  
  private final String urlOverride = StringUtils.defaultIfBlank(System.getProperty("OKAPI_URL"), StringUtils.defaultIfBlank(System.getenv("OKAPI_URL"), null));

  public String getUrl() {
    return StringUtils.defaultIfBlank(urlOverride, url);
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getTenant() {
    return tenant;
  }

  public void setTenant(String tenant) {
    this.tenant = tenant;
  }

  public String getPermissions() {
    return permissions;
  }

  public void setPermissions(String permissions) {
    this.permissions = permissions;
  }
}
