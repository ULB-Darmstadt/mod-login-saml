/**
 * 
 */
package org.folio.util;

/**
 * @author Steve Osguthorpe
 *
 */
@FunctionalInterface
public interface ThrowingHandler<E> {
  void handle(E event) throws Throwable;
}