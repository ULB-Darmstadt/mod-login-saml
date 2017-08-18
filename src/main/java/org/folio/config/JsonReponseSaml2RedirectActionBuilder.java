package org.folio.config;

import io.vertx.core.json.Json;
import net.shibboleth.utilities.java.support.codec.Base64Support;
import net.shibboleth.utilities.java.support.xml.SerializeSupport;
import org.folio.rest.jaxrs.model.SamlLogin;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.util.XMLObjectSupport;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.saml2.core.AuthnRequest;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.exception.HttpAction;
import org.pac4j.core.redirect.RedirectAction;
import org.pac4j.core.redirect.RedirectActionBuilder;
import org.pac4j.core.util.CommonHelper;
import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;
import org.pac4j.saml.context.SAML2MessageContext;
import org.pac4j.saml.sso.impl.SAML2AuthnRequestBuilder;
import org.pac4j.saml.transport.Pac4jSAMLResponse;
import org.w3c.dom.Element;

import java.nio.charset.StandardCharsets;

/**
 * Builds a {@link RedirectAction} that contains a JSON-serialized {@link SamlLogin} object instead of
 * HTML content. Always contains content (in redirect binding case too).
 *
 * @author rsass
 */
public class JsonReponseSaml2RedirectActionBuilder implements RedirectActionBuilder {

  private final SAML2Client client;
  private final SAML2AuthnRequestBuilder saml2ObjectBuilder;

  public JsonReponseSaml2RedirectActionBuilder(final SAML2Client client) {
    CommonHelper.assertNotNull("client", client);
    this.client = client;
    final SAML2ClientConfiguration cfg = client.getConfiguration();
    this.saml2ObjectBuilder = new SAML2AuthnRequestBuilder(cfg.isForceAuth(),
      cfg.getComparisonType(), cfg.getDestinationBindingType(), cfg.getAuthnContextClassRef(),
      cfg.getNameIdPolicyFormat());
  }

  @Override
  public RedirectAction redirect(WebContext webContext) throws HttpAction {

    final SAML2MessageContext context = this.client.getContextProvider().buildContext(webContext);
    final String relayState = this.client.getStateParameter(webContext);

    final AuthnRequest authnRequest = this.saml2ObjectBuilder.build(context);
    String destination = authnRequest.getDestination();

    try {
      Element marshalledAuthnRequest = XMLObjectSupport.marshall(authnRequest);
      String authnResuestAsString = SerializeSupport.nodeToString(marshalledAuthnRequest);
      String b64authnRequest = Base64Support.encode(authnResuestAsString.getBytes(StandardCharsets.UTF_8), Base64Support.UNCHUNKED);

      SamlLogin samlLogin = new SamlLogin();
      if (this.client.getConfiguration().getDestinationBindingType().equalsIgnoreCase(SAMLConstants.SAML2_POST_BINDING_URI)) {
        samlLogin.setBindingMethod(SamlLogin.BindingMethod.POST);
        samlLogin.setLocation(destination);
        samlLogin.setSamlRequest(b64authnRequest);
        samlLogin.setRelayState(relayState);
      } else {
        samlLogin.setBindingMethod(SamlLogin.BindingMethod.GET);
        samlLogin.setLocation(buildRedirectUrl(context, authnRequest, relayState));
      }

      return RedirectAction.success(Json.encode(samlLogin));

    } catch (MarshallingException e) {
      throw HttpAction.status("Cannot marshal SAML request: " + e.getMessage(), 500, webContext);
    } catch (RuntimeException e) {
      throw HttpAction.status("Runtime exception while processing saml login request: " + e.getMessage(), 500, webContext);
    }

  }

  /**
   * Redirect URL contains Signing and other params that is generated by Pac4j correctly.
   */
  private String buildRedirectUrl(SAML2MessageContext context, AuthnRequest authnRequest, String relayState) {
    this.client.getProfileHandler().send(context, authnRequest, relayState);

    final Pac4jSAMLResponse adapter = context.getProfileRequestContextOutboundMessageTransportResponse();
    return adapter.getRedirectUrl();
  }
}
