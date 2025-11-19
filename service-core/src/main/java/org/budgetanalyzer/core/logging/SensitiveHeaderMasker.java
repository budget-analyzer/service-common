package org.budgetanalyzer.core.logging;

import java.util.List;

/**
 * Utility for masking sensitive HTTP headers in logs.
 *
 * <p>Provides a default list of commonly sensitive headers and methods to check and mask header
 * values.
 */
public final class SensitiveHeaderMasker {

  /**
   * Default list of sensitive header names.
   *
   * <p>These headers should be masked in logs to prevent credential leakage.
   */
  public static final List<String> DEFAULT_SENSITIVE_HEADERS =
      List.of(
          "Authorization",
          "Cookie",
          "Set-Cookie",
          "X-API-Key",
          "X-Auth-Token",
          "Proxy-Authorization",
          "WWW-Authenticate");

  private SensitiveHeaderMasker() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Checks if a header name is sensitive.
   *
   * @param headerName header name to check
   * @param sensitiveHeaders list of sensitive header names
   * @return true if sensitive
   */
  public static boolean isSensitive(String headerName, List<String> sensitiveHeaders) {
    return sensitiveHeaders.stream().anyMatch(sensitive -> sensitive.equalsIgnoreCase(headerName));
  }

  /**
   * Masks a header value.
   *
   * @param value original value
   * @return masked value
   */
  public static String mask(String value) {
    return "***MASKED***";
  }
}
