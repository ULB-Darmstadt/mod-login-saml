/**
 * 
 */
package org.folio.services.impl;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.checkerframework.checker.index.qual.NonNegative;
import org.folio.rest.jaxrs.model.SamlValidateResponse;
import org.folio.services.IdpMetadataService;
import org.folio.util.ErrorHandlingUtil;
import org.folio.util.SamlMetadataUtil;
import org.joda.time.DateTime;
import org.opensaml.saml.metadata.resolver.impl.DOMMetadataResolver;
import org.opensaml.saml.metadata.resolver.index.impl.RoleMetadataIndex;
import org.pac4j.saml.util.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import net.shibboleth.utilities.java.support.xml.ParserPool;

/**
 * Responsible for the initial validation and parse of the metadata.
 * The initial results are cached (as files can be large) for 1 minute from
 * last cache read. The cache is also invalidated based on the validity entry at
 * the root of the downloaded metadata. So if the metadata is read from cache but
 * the document specified it expired in 30 seconds it will be purged accordingly.
 * 
 * We use a different implementation for the PAC4J integration that
 * automatically refreshes and keeps a file backing the MD. This is
 * for validation and initial read only.
 * 
 * @author Steve Osguthorpe
 *
 */
public class OpenSamlIdpMetadataService implements IdpMetadataService {
  
  private static ParserPool _parserPool = null;
  private ParserPool getParserPool() {
    if (_parserPool == null) {
      _parserPool = Configuration.getParserPool();
    }
    return _parserPool;
  }
  
  private Cache<String, CacheVal> cache = Caffeine.newBuilder()
    .maximumSize(5)
    .expireAfter(new Expiry<String, CacheVal>() {
      
      final long defaultDuration = 60L * 1_000_000_000L; // 1 minute
      
      long bumpDuration(final CacheVal value, final long currentDuration) {
        
        long current = System.currentTimeMillis();
        long docDuration = value.cacheExpire > 0 && (current < value.cacheExpire) ? (1000000 * (value.cacheExpire - current)) : 0;
        
        // If the document duration is 0 then just leave as is or default.
        if (docDuration <= 0) {
          return currentDuration <= 0 ? defaultDuration : currentDuration;
        }
        
        // Otherwise we return the smallest value between the defaultDuration and docDuration.
        return docDuration < defaultDuration ? docDuration : defaultDuration;
      }

      @Override
      public long expireAfterCreate (String key,
          CacheVal value, long currentTime) {
        return bumpDuration(value, 0);
      }

      @Override
      public long expireAfterUpdate (String key,
          CacheVal value, long currentTime,
          @NonNegative long currentDuration) {
        return bumpDuration(value, currentDuration);
      }

      @Override
      public long expireAfterRead (String key,
          CacheVal value, long currentTime,
          @NonNegative long currentDuration) {
        return bumpDuration(value, currentDuration);
      }
    })
    .build();


  // Embedded class to use as a cache entry value. Stores the parsed expiration along with the
  // response.
  private static final class CacheVal {
    SamlValidateResponse resp;
    long cacheExpire = 0;
  }
  
  private Future<CacheVal> parse (@NotNull Resource url, List<String> langs) {

    return ErrorHandlingUtil.blockingCheckedFuture(Vertx.currentContext(), (Promise<CacheVal> handler) -> {
      
      final DOMMetadataResolver resolver;

      // Always close the stream.
      try (InputStream in = url.getInputStream()) {
        
        // Grab the MD.
        final Document inCommonMDDoc = Configuration.getParserPool().parse(in);
        final Element metadataRoot = inCommonMDDoc.getDocumentElement();
        resolver = new DOMMetadataResolver(metadataRoot);
        try {
          resolver.setIndexes(Collections.singleton(new RoleMetadataIndex()));
          resolver.setParserPool(getParserPool());
          resolver.setFailFastInitialization(true);
          resolver.setRequireValidMetadata(true);
          resolver.setMetadataFilter(SamlMetadataUtil.IDPOnlyFilter);
          resolver.setId(resolver.getClass().getCanonicalName());
          resolver.initialize();
          
          // If we get this far the file is likely valid.
          SamlValidateResponse resp = new SamlValidateResponse();
          resp.withValid(true)
            .withIdps(SamlMetadataUtil.extractIDPList(resolver, langs != null ? langs : Collections.emptyList()).getIdps());
          CacheVal val = new CacheVal();
          val.resp = resp;
          
          DateTime validity = resolver.getRootValidUntil();
          val.cacheExpire = validity != null ? validity.getMillis() : 0; // No cache.
          
          handler.complete(val);
        } finally {
          // Destroy to clean up.
          resolver.destroy();
        }
      }
      
    }).recover(throwable -> {
      CacheVal val = new CacheVal();
      val.resp = new SamlValidateResponse()
        .withValid(false)
        .withError(throwable.getMessage());
      
      return Future.succeededFuture(val);
    });
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public Future<SamlValidateResponse> parse (@NotNull String url, List<String> langs) {
    
    final String cacheKey = url + (langs != null ? String.join(",", langs) : "");
    
    CacheVal cachedResponse = cache.getIfPresent(cacheKey);
    if (cachedResponse != null) return Future.succeededFuture(cachedResponse.resp);
    
    return ErrorHandlingUtil.checkedFuture((Promise<Resource> handler) -> {
      
      handler.complete(new UrlResource(url));
      
    })
      .compose(theResource -> parse(theResource, langs))
      .onSuccess(val -> cache.put(cacheKey, val))
      .compose(val -> Future.succeededFuture(val.resp), throwable -> Future.succeededFuture(
        new SamlValidateResponse()
          .withValid(false)
          .withError(throwable.getMessage())
      ));
  }

}
