package org.budgetanalyzer.core.logging;

import java.util.UUID;

/**
 * Utility for generating correlation IDs for distributed tracing.
 *
 * <p>Generates unique, short correlation IDs in the format: req_{16-hex-chars}
 */
public final class CorrelationIdGenerator {

  private static final String CORRELATION_ID_PREFIX = "req_";

  private CorrelationIdGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Generates a new correlation ID.
   *
   * <p>Format: req_{16-hex-chars}
   *
   * @return correlation ID string
   */
  public static String generate() {
    var uuid = UUID.randomUUID().toString().replace("-", "");
    return CORRELATION_ID_PREFIX + uuid.substring(0, 16);
  }
}
