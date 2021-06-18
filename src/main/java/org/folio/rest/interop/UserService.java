package org.folio.rest.interop;

import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.Instant;

import io.vertx.core.json.JsonObject;

public class UserService {
  
  private static String generateInitialUsername(final String firstName, final String lastName, final String email, @NotNull final String id) {
    String name;
    if (StringUtils.isBlank(lastName)) {
      name = lastName;
      
      // Prepend the first initial if firstname present.
      if (!StringUtils.isBlank(firstName)) {
        name = firstName.substring(0,1) + name;
      }
    } else {
      name = StringUtils.isBlank(email) ? email : id;
    }
    
    // Append a constant string and lowercase.    
    return (name + "-sso-" + Instant.now().getMillis()).toLowerCase();
  }
  
  /**
   * Creates a base use object to be sent to the Users modules in order to
   * add a user in the eventuality, we didn't match an existing one.
   * 
   * @return The user object
   */
  public static JsonObject createUserJSON(final String firstName, final String lastName, final String email) {
    final String uuid = UUID.randomUUID().toString();
    final JsonObject personal = new JsonObject()
      // Add defaults for required properties
      .put("firstName", StringUtils.defaultIfBlank(firstName, "SSO"))
      .put("lastName", StringUtils.defaultIfBlank(firstName, "User"))
    ;
    
    // Default personal info.
    if (StringUtils.isBlank(email)) {
      personal.put("email", email);
    }
    
    return new JsonObject()
      .put("id", uuid)
      .put("username", generateInitialUsername(firstName, lastName, email, uuid))
      .put("active", true)
      .put("personal", personal)
    ;
  }
  
  public UserService() {
    
  }
}
