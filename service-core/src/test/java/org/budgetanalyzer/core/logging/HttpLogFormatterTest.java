package org.budgetanalyzer.core.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HttpLogFormatterTest {

  @Test
  void testFormatLogMessage_withPrefixAndDetails() {
    Map<String, Object> details = Map.of("method", "GET", "uri", "/api/test");
    var result = HttpLogFormatter.formatLogMessage("HTTP Request", details, null);

    assertThat(result).contains("HTTP Request -");
    assertThat(result).contains("\"method\"");
    assertThat(result).contains("GET");
    assertThat(result).contains("\"uri\"");
    assertThat(result).contains("/api/test");
  }

  @Test
  void testFormatLogMessage_withBody() {
    Map<String, Object> details = Map.of("status", 200);
    var body = "{\"name\":\"test\"}";
    var result = HttpLogFormatter.formatLogMessage("HTTP Response", details, body);

    assertThat(result).contains("HTTP Response -");
    assertThat(result).contains("\"status\"");
    assertThat(result).contains("200");
    assertThat(result).contains("\nBody: ");
    assertThat(result).contains("{\"name\":\"test\"}");
  }

  @Test
  void testFormatLogMessage_withNullBody() {
    Map<String, Object> details = Map.of("key", "value");
    var result = HttpLogFormatter.formatLogMessage("Test", details, null);

    assertThat(result).contains("Test -");
    assertThat(result).doesNotContain("\nBody:");
  }

  @Test
  void testFormatLogMessage_withEmptyBody() {
    Map<String, Object> details = Map.of("key", "value");
    var result = HttpLogFormatter.formatLogMessage("Test", details, "");

    assertThat(result).contains("Test -");
    assertThat(result).doesNotContain("\nBody:");
  }

  @Test
  void testFormatLogMessage_withEmptyDetails() {
    var details = Map.<String, Object>of();
    var result = HttpLogFormatter.formatLogMessage("Empty", details, null);

    assertThat(result).contains("Empty -");
  }

  @Test
  void testFormatLogMessage_withComplexDetails() {
    Map<String, Object> details =
        Map.of("method", "POST", "uri", "/api/users", "status", 201, "duration", 45.5);
    var result = HttpLogFormatter.formatLogMessage("HTTP Request", details, null);

    assertThat(result).contains("HTTP Request -");
    assertThat(result).contains("POST");
    assertThat(result).contains("/api/users");
    assertThat(result).contains("201");
    assertThat(result).contains("45.5");
  }

  @Test
  void testConstructor_throwsException() {
    assertThatThrownBy(
            () -> {
              var constructor = HttpLogFormatter.class.getDeclaredConstructor();
              constructor.setAccessible(true);
              constructor.newInstance();
            })
        .hasCauseInstanceOf(UnsupportedOperationException.class);
  }
}
