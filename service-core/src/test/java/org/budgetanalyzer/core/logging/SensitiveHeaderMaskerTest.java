package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SensitiveHeaderMaskerTest {

  @Test
  void testDefaultSensitiveHeaders_containsExpectedHeaders() {
    assertThat(SensitiveHeaderMasker.DEFAULT_SENSITIVE_HEADERS)
        .contains(
            "Authorization",
            "Cookie",
            "Set-Cookie",
            "X-API-Key",
            "X-Auth-Token",
            "Proxy-Authorization",
            "WWW-Authenticate");
  }

  @Test
  void testIsSensitive_withSensitiveHeader() {
    var sensitiveHeaders = List.of("Authorization", "X-API-Key");
    assertThat(SensitiveHeaderMasker.isSensitive("Authorization", sensitiveHeaders)).isTrue();
  }

  @Test
  void testIsSensitive_withNonSensitiveHeader() {
    var sensitiveHeaders = List.of("Authorization", "X-API-Key");
    assertThat(SensitiveHeaderMasker.isSensitive("Content-Type", sensitiveHeaders)).isFalse();
  }

  @Test
  void testIsSensitive_caseInsensitive() {
    var sensitiveHeaders = List.of("Authorization");
    assertThat(SensitiveHeaderMasker.isSensitive("authorization", sensitiveHeaders)).isTrue();
    assertThat(SensitiveHeaderMasker.isSensitive("AUTHORIZATION", sensitiveHeaders)).isTrue();
    assertThat(SensitiveHeaderMasker.isSensitive("AuThOrIzAtIoN", sensitiveHeaders)).isTrue();
  }

  @Test
  void testIsSensitive_withEmptyList() {
    var sensitiveHeaders = List.<String>of();
    assertThat(SensitiveHeaderMasker.isSensitive("Authorization", sensitiveHeaders)).isFalse();
  }

  @Test
  void testMask_returnsExpectedValue() {
    var result = SensitiveHeaderMasker.mask("Bearer token123");
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void testMask_withNullValue() {
    var result = SensitiveHeaderMasker.mask(null);
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void testMask_withEmptyValue() {
    var result = SensitiveHeaderMasker.mask("");
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void testConstructor_throwsException() {
    assertThatThrownBy(
            () -> {
              var constructor = SensitiveHeaderMasker.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
