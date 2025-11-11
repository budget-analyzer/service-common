package org.budgetanalyzer.service.integration.fixture;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import org.budgetanalyzer.service.exception.BusinessException;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/** Test REST controller for exception handling integration tests. */
@RestController
@RequestMapping("/api/test")
public class TestController {

  /**
   * Endpoint that throws ResourceNotFoundException for testing.
   *
   * @return never returns normally
   * @throws ResourceNotFoundException always thrown
   */
  @GetMapping("/not-found")
  public ResponseEntity<String> throwNotFound() {
    throw new ResourceNotFoundException("Test resource not found");
  }

  /**
   * Endpoint that throws BusinessException for testing.
   *
   * @return never returns normally
   * @throws BusinessException always thrown
   */
  @GetMapping("/business-error")
  public ResponseEntity<String> throwBusinessException() {
    throw new BusinessException("Business rule violation", "BUSINESS_RULE_VIOLATION");
  }

  /**
   * Endpoint that throws InvalidRequestException for testing.
   *
   * @return never returns normally
   * @throws InvalidRequestException always thrown
   */
  @GetMapping("/invalid-request")
  public ResponseEntity<String> throwInvalidRequest() {
    throw new InvalidRequestException("Invalid request parameters");
  }

  /**
   * Endpoint that throws ServiceException for testing.
   *
   * @return never returns normally
   * @throws ServiceException always thrown
   */
  @GetMapping("/service-error")
  public ResponseEntity<String> throwServiceException() {
    throw new ServiceException("Internal service error");
  }

  /**
   * Endpoint that throws ServiceUnavailableException for testing.
   *
   * @return never returns normally
   * @throws ServiceUnavailableException always thrown
   */
  @GetMapping("/service-unavailable")
  public ResponseEntity<String> throwServiceUnavailable() {
    throw new ServiceUnavailableException("Service temporarily unavailable");
  }

  /**
   * Endpoint that throws RuntimeException for testing.
   *
   * @return never returns normally
   * @throws RuntimeException always thrown
   */
  @GetMapping("/runtime-error")
  public ResponseEntity<String> throwRuntimeException() {
    throw new RuntimeException("Unexpected runtime error");
  }

  /**
   * Endpoint for testing request validation.
   *
   * @param request the request to validate
   * @return response with validated request name
   */
  @PostMapping("/validate")
  public ResponseEntity<String> validateRequest(@Valid @RequestBody TestRequest request) {
    return ResponseEntity.ok("Valid request: " + request.getName());
  }

  /** Test request DTO for validation testing. */
  public static class TestRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 3, max = 50, message = "Name must be between 3 and 50 characters")
    private String name;

    /** Default constructor. */
    public TestRequest() {}

    /**
     * Constructor with name.
     *
     * @param name the name
     */
    public TestRequest(String name) {
      this.name = name;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name to set
     */
    public void setName(String name) {
      this.name = name;
    }
  }
}
