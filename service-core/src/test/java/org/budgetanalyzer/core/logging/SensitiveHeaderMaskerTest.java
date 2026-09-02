package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class SensitiveHeaderMaskerTest {

  @Test
  void shouldContainExpectedDefaultSensitiveHeaders() {
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
  void shouldIdentifySensitiveHeader() {
    var sensitiveHeaders = List.of("Authorization", "X-API-Key");
    assertThat(SensitiveHeaderMasker.isSensitive("Authorization", sensitiveHeaders)).isTrue();
  }

  @Test
  void shouldNotIdentifyNonSensitiveHeader() {
    var sensitiveHeaders = List.of("Authorization", "X-API-Key");
    assertThat(SensitiveHeaderMasker.isSensitive("Content-Type", sensitiveHeaders)).isFalse();
  }

  @Test
  void shouldIdentifySensitiveHeadersCaseInsensitively() {
    var sensitiveHeaders = List.of("Authorization");
    assertThat(SensitiveHeaderMasker.isSensitive("authorization", sensitiveHeaders)).isTrue();
    assertThat(SensitiveHeaderMasker.isSensitive("AUTHORIZATION", sensitiveHeaders)).isTrue();
    assertThat(SensitiveHeaderMasker.isSensitive("AuThOrIzAtIoN", sensitiveHeaders)).isTrue();
  }

  @Test
  void shouldNotIdentifySensitiveHeaderWhenListIsEmpty() {
    var sensitiveHeaders = List.<String>of();
    assertThat(SensitiveHeaderMasker.isSensitive("Authorization", sensitiveHeaders)).isFalse();
  }

  @Test
  void shouldReturnMaskedValue() {
    var result = SensitiveHeaderMasker.mask("Bearer token123");
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void shouldReturnMaskedValueWhenInputIsNull() {
    var result = SensitiveHeaderMasker.mask(null);
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void shouldReturnMaskedValueWhenInputIsEmpty() {
    var result = SensitiveHeaderMasker.mask("");
    assertThat(result).isEqualTo("***MASKED***");
  }

  @Test
  void shouldThrowExceptionWhenInvokingConstructor() {
    assertThatThrownBy(
            () -> {
              var constructor = SensitiveHeaderMasker.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
