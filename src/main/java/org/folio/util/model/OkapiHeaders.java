package org.folio.util.model;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Strings;

import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.HeadersMultiMap;

import static org.folio.sso.saml.Constants.Exceptions.*;

import java.io.Serializable;

/**
 * POJO for Okapi headers parsing
 *
 * @author rsass
 */
public class OkapiHeaders implements Serializable {
  private static final long serialVersionUID = -8007366917656628325L;
  
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

  public static class MissingHeaderException extends Exception {
    private static final long serialVersionUID = 7340537453740028325L;

    public MissingHeaderException(String message) {
      super(message);
    }
  }
  
  private void verifySecuredInteropValues() throws MissingHeaderException {
    verifyInteropValues();
    
    if (Strings.isNullOrEmpty(getToken())) {
      throw new MissingHeaderException(MSG_MISSING_HDR_TOKEN);
    }
  }
  
  private void verifyInteropValues() throws MissingHeaderException {
    if (Strings.isNullOrEmpty(getUrl())) {
      throw new MissingHeaderException(MSG_MISSING_HDR_OKAPI_URL);
    }
    if (Strings.isNullOrEmpty(getTenant())) {
      throw new MissingHeaderException(MSG_MISSING_HDR_TENANT);
    }
  }
  
  private MultiMap defaultHeaders() {
    return new HeadersMultiMap()
      .set("Accept", "application/json")
      .set("Content-Type", "application/json");
  }
  
  public MultiMap interopHeaders() throws MissingHeaderException {
    verifyInteropValues();
    
    return defaultHeaders()
      .set(OkapiHeaders.OKAPI_TENANT_HEADER, getTenant());
  }
  
  public MultiMap securedInteropHeaders() throws MissingHeaderException {
    verifySecuredInteropValues();
    
    return defaultHeaders()
      .set(OkapiHeaders.OKAPI_TOKEN_HEADER, getToken())
      .set(OkapiHeaders.OKAPI_TENANT_HEADER, getTenant());
  }
}
