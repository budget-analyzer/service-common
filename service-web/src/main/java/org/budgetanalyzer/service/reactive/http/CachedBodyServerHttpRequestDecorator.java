package org.budgetanalyzer.service.reactive.http;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Decorator that caches the request body for logging while allowing downstream handlers to still
 * read it.
 *
 * <p>In reactive streams, request bodies are Flux&lt;DataBuffer&gt; that can only be consumed once.
 * This decorator caches the body so it can be read for logging AND still passed to the handler.
 */
public class CachedBodyServerHttpRequestDecorator extends ServerHttpRequestDecorator {

  private final Flux<DataBuffer> cachedBody;

  /**
   * Constructs a decorator that caches the request body.
   *
   * @param delegate the original request
   */
  public CachedBodyServerHttpRequestDecorator(ServerHttpRequest delegate) {
    super(delegate);
    this.cachedBody =
        DataBufferUtils.join(delegate.getBody()).flux().cache(); // Cache for multiple subscribers
  }

  @Override
  public Flux<DataBuffer> getBody() {
    return cachedBody;
  }

  /**
   * Reads the cached body as a string.
   *
   * @param maxBytes maximum bytes to read
   * @return Mono with body string
   */
  public Mono<String> getCachedBodyAsString(int maxBytes) {
    return cachedBody
        .next()
        .map(
            dataBuffer -> {
              int readableBytes = Math.min(dataBuffer.readableByteCount(), maxBytes);
              byte[] bytes = new byte[readableBytes];
              dataBuffer.read(bytes);

              var charset = getCharset();
              var body = new String(bytes, charset);

              if (dataBuffer.readableByteCount() > maxBytes) {
                var truncated = dataBuffer.readableByteCount() - maxBytes;
                return body + "... [TRUNCATED - " + truncated + " bytes omitted]";
              }

              return body;
            })
        .defaultIfEmpty("");
  }

  /**
   * Gets the charset from the request content type, or returns UTF-8 as default.
   *
   * @return charset
   */
  private Charset getCharset() {
    var contentType = getDelegate().getHeaders().getContentType();
    if (contentType != null && contentType.getCharset() != null) {
      return contentType.getCharset();
    }
    return StandardCharsets.UTF_8;
  }
}
