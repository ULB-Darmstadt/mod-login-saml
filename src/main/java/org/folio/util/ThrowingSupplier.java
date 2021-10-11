/**
 * 
 */
package org.folio.util;

/**
 * @author Steve Osguthorpe
 *
 */
@FunctionalInterface
public interface ThrowingSupplier<R> {
  R get() throws Throwable;
}