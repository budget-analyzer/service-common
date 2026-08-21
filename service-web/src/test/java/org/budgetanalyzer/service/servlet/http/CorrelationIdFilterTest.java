package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

  private CorrelationIdFilter correlationIdFilter;

  @BeforeEach
  void setUp() {
    correlationIdFilter = new CorrelationIdFilter();
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void shouldGenerateCorrelationIdWhenNotProvidedInRequest() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var filterChain = new MockFilterChain();

    correlationIdFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).startsWith("req_");
    assertThat(filterChain.getRequest()).isSameAs(request);
    assertThat(filterChain.getResponse()).isSameAs(response);
  }

  @Test
  void shouldUseExistingCorrelationIdFromRequest() throws Exception {
    var existingCorrelationId = "req_abc123def456";
    var request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, existingCorrelationId);
    var response = new MockHttpServletResponse();
    var filterChain = new MockFilterChain();

    correlationIdFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .isEqualTo(existingCorrelationId);
    assertThat(filterChain.getRequest()).isSameAs(request);
    assertThat(filterChain.getResponse()).isSameAs(response);
  }

  @Test
  void shouldTrimExistingCorrelationIdFromRequest() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "  req_trimmed-123  ");
    var response = new MockHttpServletResponse();
    var filterChain = new MockFilterChain();

    correlationIdFilter.doFilterInternal(request, response, filterChain);

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER))
        .isEqualTo("req_trimmed-123");
    assertThat(filterChain.getRequest()).isSameAs(request);
  }

  @Test
  void shouldStoreCorrelationIdInMdc() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          var mdcValue = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertThat(mdcValue).isNotNull();
          assertThat(mdcValue.startsWith("req_")).isTrue();
        });

    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldClearMdcAfterFilterExecution() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(request, response, new MockFilterChain());

    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldClearMdcEvenWhenExceptionIsThrown() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();
    var expectedException = new RuntimeException("Simulated error");

    assertThatThrownBy(
            () ->
                correlationIdFilter.doFilterInternal(
                    request,
                    response,
                    (chainRequest, chainResponse) -> {
                      throw expectedException;
                    }))
        .isSameAs(expectedException);

    assertThat(MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void shouldGenerateUniqueCorrelationIds() throws Exception {
    var correlationIds = new String[10];
    for (int i = 0; i < 10; i++) {
      final int index = i;
      var request = new MockHttpServletRequest();
      var response = new MockHttpServletResponse();
      correlationIdFilter.doFilterInternal(
          request,
          response,
          (req, res) -> {
            correlationIds[index] = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          });
    }

    assertThat(Arrays.stream(correlationIds).distinct().count()).isEqualTo(10);
  }

  @Test
  void shouldHandleEmptyCorrelationIdHeader() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "  ");
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).startsWith("req_");
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderContainsUnsafeCharacters() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "bad value");
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          var correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);
          assertThat(correlationId).isNotNull();
          assertThat(correlationId.startsWith("req_")).isTrue();
          assertThat(correlationId.length()).isEqualTo(36);
        });

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).startsWith("req_");
  }

  @Test
  void shouldGenerateCorrelationIdWhenHeaderExceedsMaxLength() throws Exception {
    var request = new MockHttpServletRequest();
    request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "a".repeat(129));
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(request, response, new MockFilterChain());

    assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).startsWith("req_");
  }

  @Test
  void shouldGenerateCorrelationIdWithCorrectFormat() throws Exception {
    var request = new MockHttpServletRequest();
    var response = new MockHttpServletResponse();

    correlationIdFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          var correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

          // Assert format: req_<32 hex chars>
          assertThat(correlationId).isNotNull();
          assertThat(correlationId.startsWith("req_")).isTrue();
          assertThat(correlationId.length()).isEqualTo(36); // "req_" (4) + 32 hex chars
          assertThat(correlationId.substring(4).matches("[0-9a-f]{32}"))
              .as("Correlation ID should contain 32 hexadecimal characters")
              .isTrue();
        });
  }
}
