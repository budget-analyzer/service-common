package org.budgetanalyzer.service.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.budgetanalyzer.service.api.FieldError;

/** Unit tests for {@link BusinessException}. */
@DisplayName("BusinessException Tests")
class BusinessExceptionTest {

  @Test
  @DisplayName("Should create exception with code")
  void shouldCreateExceptionWithCode() {
    var message = "Transaction amount must be positive";
    var code = "NEGATIVE_AMOUNT";

    var exception = new BusinessException(message, code);

    assertThat(exception.getCode()).isEqualTo(code);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should create exception with code and cause")
  void shouldCreateExceptionWithCodeAndCause() {
    var message = "Budget limit exceeded";
    var code = "BUDGET_EXCEEDED";
    var cause = new IllegalStateException("Current balance insufficient");

    var exception = new BusinessException(message, code, cause);

    assertThat(exception.getCode()).isEqualTo(code);
    assertThat(exception.getCause()).isSameAs(cause);
  }

  @Test
  @DisplayName("Should extend ServiceException")
  void shouldExtendServiceException() {
    var exception = new BusinessException("Error", "ERROR_CODE");

    assertThat(exception).isInstanceOf(ServiceException.class);
  }

  @Test
  @DisplayName("Should be instance of RuntimeException")
  void shouldBeRuntimeException() {
    var exception = new BusinessException("Error", "ERROR_CODE");

    assertThat(exception).isInstanceOf(RuntimeException.class);
  }

  @Test
  @DisplayName("Should handle null code")
  void shouldHandleNullCode() {
    var message = "Business rule violation";

    var exception = new BusinessException(message, null);

    assertThat(exception.getCode()).isNull();
  }

  @Test
  @DisplayName("Should handle null cause")
  void shouldHandleNullCause() {
    var message = "Business rule violation";
    var code = "RULE_VIOLATION";

    var exception = new BusinessException(message, code, (Throwable) null);

    assertThat(exception.getCode()).isEqualTo(code);
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should handle all null values with cause constructor")
  void shouldHandleAllNullValuesWithCauseConstructor() {
    var exception = new BusinessException(null, null, (Throwable) null);

    assertThat(exception.getCode()).isNull();
    assertThat(exception.getCause()).isNull();
  }

  @Test
  @DisplayName("Should preserve error code for negative amount scenario")
  void shouldPreserveErrorCodeForNegativeAmount() {
    var exception = new BusinessException("Amount cannot be negative", "NEGATIVE_AMOUNT");

    assertThat(exception.getCode()).isEqualTo("NEGATIVE_AMOUNT");
  }

  @Test
  @DisplayName("Should preserve error code for budget exceeded scenario")
  void shouldPreserveErrorCodeForBudgetExceeded() {
    var exception = new BusinessException("Budget limit of $1000 exceeded", "BUDGET_EXCEEDED");

    assertThat(exception.getCode()).isEqualTo("BUDGET_EXCEEDED");
  }

  @Test
  @DisplayName("Should preserve error code for duplicate detection scenario")
  void shouldPreserveErrorCodeForDuplicateDetection() {
    var exception =
        new BusinessException("Duplicate transaction detected", "DUPLICATE_TRANSACTION");

    assertThat(exception.getCode()).isEqualTo("DUPLICATE_TRANSACTION");
  }

  @Test
  @DisplayName("Should handle snake_case error codes")
  void shouldHandleSnakeCaseErrorCodes() {
    var exception = new BusinessException("Invalid state transition", "INVALID_STATE_TRANSITION");

    assertThat(exception.getCode()).isEqualTo("INVALID_STATE_TRANSITION");
  }

  @Test
  @DisplayName("Should handle dot-notation error codes")
  void shouldHandleDotNotationErrorCodes() {
    var exception = new BusinessException("Validation failed", "validation.amount.negative");

    assertThat(exception.getCode()).isEqualTo("validation.amount.negative");
  }

  @Test
  @DisplayName("Should handle numeric error codes")
  void shouldHandleNumericErrorCodes() {
    var exception = new BusinessException("Application error", "ERR_1001");

    assertThat(exception.getCode()).isEqualTo("ERR_1001");
  }

  // ==================== Field Errors Tests ====================

  @Test
  @DisplayName("Should create exception with field errors")
  void shouldCreateExceptionWithFieldErrors() {
    var fieldErrors =
        List.of(
            FieldError.forIndexedField(0, "amount", "must not be null", null),
            FieldError.forIndexedField(2, "date", "must be a valid date", "invalid-date"));

    var exception =
        new BusinessException("Batch validation failed", "BATCH_VALIDATION_FAILED", fieldErrors);

    assertThat(exception.getCode()).isEqualTo("BATCH_VALIDATION_FAILED");
    assertThat(exception.hasFieldErrors()).isTrue();
    assertThat(exception.getFieldErrors().size()).isEqualTo(2);
    assertThat(exception.getFieldErrors().get(0).getIndex()).isEqualTo(0);
    assertThat(exception.getFieldErrors().get(0).getField()).isEqualTo("amount");
  }

  @Test
  @DisplayName("Should have empty field errors by default")
  void shouldHaveEmptyFieldErrorsByDefault() {
    var exception = new BusinessException("Error", "CODE");

    assertThat(exception.hasFieldErrors()).isFalse();
    assertThat(exception.getFieldErrors()).isEmpty();
  }

  @Test
  @DisplayName("Should have empty field errors with cause constructor")
  void shouldHaveEmptyFieldErrorsWithCauseConstructor() {
    var exception = new BusinessException("Error", "CODE", new RuntimeException("cause"));

    assertThat(exception.hasFieldErrors()).isFalse();
    assertThat(exception.getFieldErrors()).isEmpty();
  }

  @Test
  @DisplayName("Should return immutable field errors list")
  void shouldReturnImmutableFieldErrorsList() {
    var fieldErrors = List.of(FieldError.forIndexedField(0, "field", "error", null));

    var exception = new BusinessException("Error", "CODE", fieldErrors);

    var returnedErrors = exception.getFieldErrors();
    assertThatThrownBy(
            () -> returnedErrors.add(FieldError.forIndexedField(1, "other", "error", null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("Should handle null field errors list")
  void shouldHandleNullFieldErrorsList() {
    var exception = new BusinessException("Error", "CODE", (List<FieldError>) null);

    assertThat(exception.hasFieldErrors()).isFalse();
    assertThat(exception.getFieldErrors()).isEmpty();
  }
}
