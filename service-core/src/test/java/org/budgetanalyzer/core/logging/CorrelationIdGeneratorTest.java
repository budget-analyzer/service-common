package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CorrelationIdGeneratorTest {

  @Test
  void shouldGenerateNonNullValue() {
    var correlationId = CorrelationIdGenerator.generate();
    assertThat(correlationId).isNotNull();
  }

  @Test
  void shouldGenerateIdWithRequestPrefix() {
    var correlationId = CorrelationIdGenerator.generate();
    assertThat(correlationId).startsWith("req_");
  }

  @Test
  void shouldGenerateIdWithCorrectLength() {
    var correlationId = CorrelationIdGenerator.generate();
    // Format: "req_" (4 chars) + 32 hex chars = 36 total
    assertThat(correlationId).hasSize(36);
  }

  @Test
  void shouldGenerateIdContainingOnlyHexCharactersAfterPrefix() {
    var correlationId = CorrelationIdGenerator.generate();
    var hexPart = correlationId.substring(4); // Skip "req_" prefix
    assertThat(hexPart).matches("[0-9a-f]{32}");
  }

  @Test
  void shouldGenerateUniqueValues() {
    var id1 = CorrelationIdGenerator.generate();
    var id2 = CorrelationIdGenerator.generate();
    var id3 = CorrelationIdGenerator.generate();

    assertThat(id1).isNotEqualTo(id2);
    assertThat(id1).isNotEqualTo(id3);
    assertThat(id2).isNotEqualTo(id3);
  }

  @Test
  void shouldThrowExceptionWhenInvokingConstructor() {
    assertThatThrownBy(
            () -> {
              var constructor = CorrelationIdGenerator.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
