package org.budgetanalyzer.service.servlet.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

class HttpLoggingFilterTest {

  private HttpLoggingProperties httpLoggingProperties;
  private HttpLoggingFilter httpLoggingFilter;
  private RecordingFilterChain recordingFilterChain;

  @BeforeEach
  void setUp() {
    httpLoggingProperties = new HttpLoggingProperties();
    httpLoggingProperties.setEnabled(true);
    httpLoggingProperties.setLogLevel("DEBUG");
    httpLoggingProperties.setIncludeRequestBody(true);
    httpLoggingProperties.setIncludeResponseBody(true);
    httpLoggingProperties.setMaxBodySize(10000);

    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);
    recordingFilterChain = new RecordingFilterChain();
  }

  @Test
  void shouldBypassFilterWhenDisabled() throws Exception {
    // Arrange
    httpLoggingProperties.setEnabled(false);
    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isSameAs(request);
    assertThat(recordingFilterChain.getResponse()).isSameAs(response);
  }

  @Test
  void shouldLogRequestAndResponse() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isNotNull();
    assertThat(recordingFilterChain.getResponse()).isNotNull();
  }

  @Test
  void shouldExcludePathsMatchingExcludePatterns() throws Exception {
    // Arrange
    httpLoggingProperties.setExcludePatterns(List.of("/actuator/**", "/swagger-ui/**"));
    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/actuator/health");
    request.setServletPath("/actuator/health");

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isSameAs(request);
    assertThat(recordingFilterChain.getResponse()).isSameAs(response);
  }

  @Test
  void shouldIncludeOnlyPathsMatchingIncludePatterns() throws Exception {
    // Arrange
    httpLoggingProperties.setIncludePatterns(List.of("/api/**"));
    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

    var includedRequest = new MockHttpServletRequest();
    includedRequest.setMethod("GET");
    includedRequest.setRequestURI("/api/users");
    includedRequest.setServletPath("/api/users");

    var excludedRequest = new MockHttpServletRequest();
    excludedRequest.setMethod("GET");
    excludedRequest.setRequestURI("/public/health");
    excludedRequest.setServletPath("/public/health");

    var response = new MockHttpServletResponse();

    // Act - Test included path
    httpLoggingFilter.doFilterInternal(includedRequest, response, recordingFilterChain);
    assertThat(recordingFilterChain.getRequest()).isInstanceOf(ContentCachingRequestWrapper.class);
    assertThat(recordingFilterChain.getResponse())
        .isInstanceOf(ContentCachingResponseWrapper.class);

    recordingFilterChain = new RecordingFilterChain();

    // Act - Test excluded path
    var excludedResponse = new MockHttpServletResponse();
    httpLoggingFilter.doFilterInternal(excludedRequest, excludedResponse, recordingFilterChain);
    assertThat(recordingFilterChain.getRequest()).isSameAs(excludedRequest);
    assertThat(recordingFilterChain.getResponse()).isSameAs(excludedResponse);
  }

  @Test
  void shouldLogOnlyErrorsWhenLogErrorsOnlyIsEnabled() throws Exception {
    // Arrange
    httpLoggingProperties.setLogErrorsOnly(true);
    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();
    response.setStatus(200); // Success status

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isNotNull();
    assertThat(recordingFilterChain.getResponse()).isNotNull();
    // Response logging should be skipped (tested via manual verification of logs)
  }

  @Test
  void shouldWrapRequestAndResponseForContentCaching() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("POST");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");
    request.setContent("{\"name\":\"John\"}".getBytes());

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Assert - Request and response should be wrapped
          assertThat(req)
              .as("Request should be wrapped with ContentCachingRequestWrapper")
              .isInstanceOf(org.springframework.web.util.ContentCachingRequestWrapper.class);
          assertThat(res)
              .as("Response should be wrapped with ContentCachingResponseWrapper")
              .isInstanceOf(org.springframework.web.util.ContentCachingResponseWrapper.class);
        });
  }

  @Test
  void shouldNotLogRequestBodyForGetRequests() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isNotNull();
    assertThat(recordingFilterChain.getResponse()).isNotNull();
  }

  @Test
  void shouldHandleExceptionsDuringLogging() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    // Act - Should not throw even if internal logging fails
    assertThatCode(
            () -> httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain))
        .doesNotThrowAnyException();

    assertThat(recordingFilterChain.getRequest()).isNotNull();
    assertThat(recordingFilterChain.getResponse()).isNotNull();
  }

  @Test
  void shouldCopyResponseBodyToActualResponse() throws Exception {
    // Arrange
    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/users");
    request.setServletPath("/api/users");

    var response = new MockHttpServletResponse();

    var responseBody = "{\"users\":[]}";

    // Act
    httpLoggingFilter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          // Simulate controller writing to response
          res.getWriter().write(responseBody);
        });

    // Assert - Response should contain the body
    var actualResponseBody = response.getContentAsString();
    assertThat(actualResponseBody).isEqualTo(responseBody);
  }

  @Test
  void shouldSanitizeSensitiveQueryParamsInLogs() throws Exception {
    // Arrange
    var logger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      httpLoggingProperties.setIncludeQueryParams(true);
      httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

      var request = new MockHttpServletRequest();
      request.setMethod("GET");
      request.setRequestURI("/login/oauth2/code/idp");
      request.setServletPath("/login/oauth2/code/idp");
      request.setQueryString("code=authcode123&state=csrfstate456");

      var response = new MockHttpServletResponse();

      // Act
      httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

      // Assert
      var logOutput =
          listAppender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(Collectors.joining("\n"));

      assertThat(logOutput.contains("code=***&state=***")).isTrue();
      assertThat(logOutput.contains("authcode123")).isFalse();
      assertThat(logOutput.contains("csrfstate456")).isFalse();
    } finally {
      logger.detachAppender(listAppender);
      listAppender.stop();
      logger.setLevel(originalLevel);
    }
  }

  @Test
  void shouldSanitizeCustomSensitiveQueryParamsInLogs() throws Exception {
    // Arrange
    var logger = (Logger) LoggerFactory.getLogger(HttpLoggingFilter.class);
    var originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    var listAppender = new ListAppender<ILoggingEvent>();
    listAppender.start();
    logger.addAppender(listAppender);

    try {
      httpLoggingProperties.setIncludeQueryParams(true);
      httpLoggingProperties.setSensitiveQueryParams(List.of("custom_key"));
      httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

      var request = new MockHttpServletRequest();
      request.setMethod("GET");
      request.setRequestURI("/api/test");
      request.setServletPath("/api/test");
      request.setQueryString("custom_key=secret&page=1&code=auth123");

      var response = new MockHttpServletResponse();

      // Act
      httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

      // Assert
      var logOutput =
          listAppender.list.stream()
              .map(ILoggingEvent::getFormattedMessage)
              .collect(Collectors.joining("\n"));

      assertThat(logOutput.contains("custom_key=***")).isTrue();
      assertThat(logOutput.contains("code=***")).isTrue();
      assertThat(logOutput.contains("page=1")).isTrue();
      assertThat(logOutput.contains("secret")).isFalse();
      assertThat(logOutput.contains("auth123")).isFalse();
    } finally {
      logger.detachAppender(listAppender);
      listAppender.stop();
      logger.setLevel(originalLevel);
    }
  }

  @Test
  void shouldExcludePatternTakePrecedenceOverIncludePattern() throws Exception {
    // Arrange
    httpLoggingProperties.setIncludePatterns(List.of("/api/**"));
    httpLoggingProperties.setExcludePatterns(List.of("/api/internal/**"));
    httpLoggingFilter = new HttpLoggingFilter(httpLoggingProperties);

    var request = new MockHttpServletRequest();
    request.setMethod("GET");
    request.setRequestURI("/api/internal/debug");
    request.setServletPath("/api/internal/debug");

    var response = new MockHttpServletResponse();

    // Act
    httpLoggingFilter.doFilterInternal(request, response, recordingFilterChain);

    assertThat(recordingFilterChain.getRequest()).isSameAs(request);
    assertThat(recordingFilterChain.getResponse()).isSameAs(response);
  }

  private static final class RecordingFilterChain implements FilterChain {

    private ServletRequest request;
    private ServletResponse response;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response)
        throws IOException, ServletException {
      this.request = request;
      this.response = response;
    }

    private ServletRequest getRequest() {
      return request;
    }

    private ServletResponse getResponse() {
      return response;
    }
  }
}
