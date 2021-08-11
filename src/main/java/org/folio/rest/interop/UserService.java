package org.folio.rest.interop;

import java.util.List;
import java.util.UUID;

import javax.validation.constraints.NotNull;

import org.apache.commons.lang3.StringUtils;
import org.folio.sso.saml.SamlConfiguration;
import org.joda.time.Instant;
import org.pac4j.core.profile.CommonProfile;

import io.vertx.core.json.JsonObject;

public class UserService {
  
  private static String generateInitialUsername(final String firstName, final String lastName, final String email, @NotNull final String id) {
    String name;
    if (!StringUtils.isBlank(lastName)) {
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
  public static JsonObject createUserJSON(
      @NotNull final String uuid,
      @NotNull final String username,
      @NotNull final String firstName,
      @NotNull final String lastName,
      @NotNull final String patgronGroupId,
      final String email) {
    
    final JsonObject personal = new JsonObject()
      // Add defaults for required properties
      .put("firstName", firstName)
      .put("lastName", lastName)
    ;
    
    // Default personal info.
    if (StringUtils.isBlank(email)) {
      personal.put("email", email);
    }
    
    return new JsonObject()
      .put("id", uuid)
      .put("username", username)
      .put("active", true)
      .put("patronGroup", patgronGroupId)
      .put("personal", personal)
    ;
  }
  
  private static String getStrAtt(final CommonProfile profile, final String attribute) {
    List<?> samlAttributeList = profile.getAttribute(attribute, List.class);
    if (samlAttributeList == null || samlAttributeList.isEmpty()) {
      return null;
    }
    
    // Return the first hit.
    return samlAttributeList.get(0).toString();
  }
  
  private static String getOrDefaultAttribute(final CommonProfile profile, final String attribute, final String defaultValue) {
    return StringUtils.defaultIfBlank(
      getStrAtt(profile, attribute),
      defaultValue
    );
  }

  /**
   * Creates a base user object from a pac4j common profile and the config for this module.
   * {@link #createUserJSON(firstName, lastName, email) }
   * @param configuration 
   * @return The user object
   */
  public static JsonObject createUserJSON(final CommonProfile profile, final SamlConfiguration configuration) {
    
    final String uuid = UUID.randomUUID().toString();
    
    final String firstName = getOrDefaultAttribute(
      profile,
      configuration.getUserDefaultFirstNameAttribute(),
      configuration.getUserDefaultFirstNameDefault()
    );
    
    final String lastName = getOrDefaultAttribute(
      profile,
      configuration.getUserDefaultLastNameAttribute(),
      configuration.getUserDefaultLastNameDefault()
    );

    final String email = getStrAtt(
      profile,
      configuration.getUserDefaultEmailAttribute());
    
    final String patronGroupId = configuration.getUserDefaultPatronGroup();

    String username = getStrAtt(
      profile,
      configuration.getUserDefaultUsernameAttribute());
    if (username == null) {
      username = generateInitialUsername(
        getStrAtt(
          profile,
          configuration.getUserDefaultUsernameAttribute()
        ),
        lastName,
        email,
        uuid
      );
    }

    return createUserJSON(
      uuid, username, firstName, lastName, patronGroupId, email 
    );
  }
  
  public UserService() {
    
  }
}
