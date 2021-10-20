package org.folio.util;

import static org.folio.sso.saml.Constants.Config.I18N_DEFAULT_LANG;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.NotNull;
import javax.xml.namespace.QName;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.rest.jaxrs.model.I18n;
import org.folio.rest.jaxrs.model.I18nProperty;
import org.folio.rest.jaxrs.model.Idp;
import org.folio.rest.jaxrs.model.SamlIdpList;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.saml.common.xml.SAMLConstants;
import org.opensaml.saml.ext.saml2mdui.Description;
import org.opensaml.saml.ext.saml2mdui.DisplayName;
import org.opensaml.saml.ext.saml2mdui.UIInfo;
import org.opensaml.saml.metadata.IterableMetadataSource;
import org.opensaml.saml.metadata.resolver.filter.impl.EntityRoleFilter;
import org.opensaml.saml.saml2.metadata.EntityDescriptor;
import org.opensaml.saml.saml2.metadata.Extensions;
import org.opensaml.saml.saml2.metadata.IDPSSODescriptor;

/**
 * Utilities for dealing with the XML metadata used in SAML excahnges.
 * 
 * @author Steve Osguthorpe
 *
 */
public class SamlMetadataUtil {

  private static final Logger log = LogManager.getLogger(SamlMetadataUtil.class);
  
  public static final EntityRoleFilter IDPOnlyFilter = new EntityRoleFilter(
      Arrays.asList(new QName[] {IDPSSODescriptor.DEFAULT_ELEMENT_NAME}));
  
  public static SamlIdpList extractIDPList (@NotNull final IterableMetadataSource mdSource) {
    return extractIDPList(mdSource, Stream.of( I18N_DEFAULT_LANG ).collect(Collectors.toUnmodifiableList()));
  }
  
  public static SamlIdpList extractIDPList (@NotNull final IterableMetadataSource mdSource, @NotNull final List<String> orderedLangCodes) {
    log.debug("Languages requested: {}", orderedLangCodes.toString());
    final List<Idp> idpList = new ArrayList<Idp>();
    
    Iterator<EntityDescriptor> entityDesc;
    entityDesc = mdSource.iterator();
    while (entityDesc.hasNext()) {
      final EntityDescriptor entityDescriptor = entityDesc.next();
      final String descId = entityDescriptor.getEntityID();
      if (descId != null) {
        final IDPSSODescriptor idpDesc =  entityDescriptor.getIDPSSODescriptor(SAMLConstants.SAML20P_NS);
        if (idpDesc != null) {
          
          final String entitytId = entityDescriptor.getEntityID();
          
          // Add the default root elems.
          final Idp idp = new Idp()
            .withId(entitytId);
          
          // Grab the UI extension data if present
          final Extensions ext = idpDesc.getExtensions();
          if (ext != null) {
            
            // Try and resolve the UIInfo XMLObject from the Extension children.
            Iterator<XMLObject> allExt = ext.getOrderedChildren().iterator();
            UIInfo uiInfo = null;
            while (uiInfo == null && allExt.hasNext()) {
              final XMLObject obj = allExt.next();
              if (obj.getElementQName().equals(UIInfo.DEFAULT_ELEMENT_NAME)) {
                
                uiInfo = (UIInfo)obj;

                // Create our object, and set it against the parent.
                final I18n i18n = new I18n();
                idp.setI18n(i18n);
                
                // Found a UIInfo element, Build up our i18n namespaced model.
                // Start with the displayNames.
                for (final DisplayName displayName : uiInfo.getDisplayNames()) {
                  
                  final String langCode = displayName.getXMLLang();
                  
                  // Namespaced PropertyEntry
                  I18nProperty entry = getOrCreateI18nProperty(i18n.getAdditionalProperties(), langCode);
                  final String value = displayName.getValue();
                  entry.setDisplayName(value);
                }
                
                // Descriptions next.
                for (final Description description : uiInfo.getDescriptions()) {
                  
                  final String langCode = description.getXMLLang();
                  
                  // Namespaced PropertyEntry
                  I18nProperty entry = getOrCreateI18nProperty(i18n.getAdditionalProperties(), langCode);
                  final String value = description.getValue();
                  entry.setDescription(value);
                }
              }
            }
          }
          
          // No languages specified in metadata. Use the defaults from our constants.
          if (idp.getI18n() == null || idp.getI18n().getAdditionalProperties().isEmpty()) {
            idp
              .withDisplayName(entitytId)
              .withDescription(String.format("IDP at %s", entitytId))
            
              .setI18n(new I18n()
                .withAdditionalProperty(I18N_DEFAULT_LANG, new I18nProperty()
                  .withDisplayName(idp.getDisplayName())
                  .withDescription(idp.getDescription())
                )
              );
            
          } else if (idp.getI18n().getAdditionalProperties().size() == 1) {
          
            // We'll always match the first...
            final I18nProperty langEntry = 
                idp.getI18n().getAdditionalProperties().values().stream().findFirst().get();
            
              // Set the values.
              idp
                .withDisplayName(langEntry.getDisplayName())
                .withDescription(langEntry.getDescription());
          } else {
            // We have more than 1 entry in the i18n section. Find the best match.

            // Default fallback language to null. We use null to denote that no partial
            // match was found when parsing the UI hints. If this is still null after
            // parsing then we'll use the default from the constants. First partial wins!
            final Map<String, I18nProperty> allLangsMap = idp.getI18n().getAdditionalProperties();
            final Set<String> allLangs = allLangsMap.keySet();
            
            String exactLanguage = null;
            String fallbackLanguage = null;
            for (int i=0; exactLanguage == null && i<orderedLangCodes.size(); i++) {
              final String lang = orderedLangCodes.get(i);
              exactLanguage = allLangs.contains(lang) ? lang : null;
              if (exactLanguage == null && fallbackLanguage == null) {
                // See if a partial match is found.
                final int dash = lang.indexOf('-');
                if (dash > 0) {
                  final String potentialFallback = lang.substring(0, dash);
                  if (allLangs.contains(potentialFallback)) {
                    fallbackLanguage = potentialFallback;
                  }
                }
              }
            }
            
            // Match exact, fallback, default and finally the first if not found
            final I18nProperty langEntry = Stream.of(
              exactLanguage, fallbackLanguage, I18N_DEFAULT_LANG)
                .filter(Objects::nonNull)
                .findFirst()
                .map(selectedLang -> {
                  log.debug("Using lang code from supplied headers {}", selectedLang);
                  return allLangsMap.get(selectedLang);
                })
                // Use the first entry if we cannot match any of the langs.
                .orElse(allLangsMap.values().stream().findFirst().get());
            
            // Set the values.
            idp
              .withDisplayName(langEntry.getDisplayName())
              .withDescription(langEntry.getDescription());
          }
          
          idpList.add(idp);
        }
      }
    }
      
    
    return new SamlIdpList().withIdps(idpList);
  }
  
  private static I18nProperty getOrCreateI18nProperty(final Map<String, I18nProperty> propMap, final String langCode) {
    I18nProperty entry = propMap.get(langCode);
    if (entry == null) {
      entry = new I18nProperty();
      // Add the entry too.
      propMap.put(langCode, entry);
    }
    return entry;
  }
}
