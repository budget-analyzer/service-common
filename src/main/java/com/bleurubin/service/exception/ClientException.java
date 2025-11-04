package com.bleurubin.service.exception;

/**
 * Exception indicating a client error in the request.
 *
 * <p>This exception represents client-side errors (HTTP 400 Bad Request) where the request is
 * malformed, missing required parameters, or contains invalid data. This is the base class for more
 * specific client error exceptions like {@link InvalidRequestException} and {@link
 * ResourceNotFoundException}.
 */
public class ClientException extends ServiceException {

  /**
   * Constructs a new client exception with the specified detail message.
   *
   * @param message the detail message explaining what was wrong with the client request
   */
  public ClientException(String message) {
    super(message);
  }

  /**
   * Constructs a new client exception with the specified detail message and cause.
   *
   * @param message the detail message explaining what was wrong with the client request
   * @param cause the underlying cause of this exception
   */
  public ClientException(String message, Throwable cause) {
    super(message, cause);
  }
}
