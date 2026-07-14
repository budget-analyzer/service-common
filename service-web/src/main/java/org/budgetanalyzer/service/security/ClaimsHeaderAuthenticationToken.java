package org.budgetanalyzer.service.security;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authentication token populated from pre-validated claims headers.
 *
 * <p>Trusted ingress external auth injects canonical claims headers ({@code X-User-Id}, {@code
 * X-Permissions}, {@code X-Roles}) into requests before they reach backend services. This token
 * represents the authenticated user identity extracted from those headers.
 *
 * <p>Extends {@link AbstractAuthenticationToken} (same base class as {@code
 * JwtAuthenticationToken}) to ensure seamless integration with {@code @PreAuthorize}, {@code
 * hasRole()}, {@code hasAuthority()}, and {@code SecurityContextAuditorAware}.
 *
 * @see ClaimsHeaderAuthenticationFilter
 * @see ClaimsHeaderSecurityConfig
 */
public class ClaimsHeaderAuthenticationToken extends AbstractAuthenticationToken {

  private static final long serialVersionUID = 1L;

  private final String userId;
  private final Set<String> roles;

  private ClaimsHeaderAuthenticationToken(
      String userId, Set<String> roles, Collection<? extends GrantedAuthority> authorities) {
    super(Collections.unmodifiableList(copyAuthorities(authorities)));
    this.userId = requireUserId(userId);
    this.roles = Collections.unmodifiableSet(copyRoles(roles));
    setAuthenticated(true);
  }

  /**
   * Creates an authenticated token from validated claims header values.
   *
   * @param userId the user ID from {@code X-User-Id} header
   * @param roles the raw role names (without {@code ROLE_} prefix)
   * @param authorities the granted authorities (permissions + ROLE_-prefixed roles)
   * @return an authenticated token
   * @throws NullPointerException when userId, roles, or authorities is null
   * @throws IllegalArgumentException when userId is blank
   */
  public static ClaimsHeaderAuthenticationToken authenticated(
      String userId, Set<String> roles, Collection<? extends GrantedAuthority> authorities) {
    return new ClaimsHeaderAuthenticationToken(userId, roles, authorities);
  }

  private static String requireUserId(String userId) {
    var validatedUserId = Objects.requireNonNull(userId, "userId must not be null");
    if (validatedUserId.isBlank()) {
      throw new IllegalArgumentException("userId must not be blank");
    }
    return validatedUserId;
  }

  private static Set<String> copyRoles(Set<String> roles) {
    return new LinkedHashSet<>(Objects.requireNonNull(roles, "roles must not be null"));
  }

  private static java.util.ArrayList<GrantedAuthority> copyAuthorities(
      Collection<? extends GrantedAuthority> authorities) {
    return new java.util.ArrayList<>(
        Objects.requireNonNull(authorities, "authorities must not be null"));
  }

  /**
   * Returns the user ID. Keeps {@code SecurityContextAuditorAware} working unchanged.
   *
   * @return the user ID from the {@code X-User-Id} header
   */
  @Override
  public Object getPrincipal() {
    return userId;
  }

  /**
   * Returns empty string since authentication is performed before the request reaches this service.
   *
   * @return empty string
   */
  @Override
  public Object getCredentials() {
    return "";
  }

  /**
   * Returns the user ID. Keeps {@code SecurityContextAuditorAware} working unchanged.
   *
   * @return the user ID from the {@code X-User-Id} header
   */
  @Override
  public String getName() {
    return userId;
  }

  /**
   * Returns the raw role names without the {@code ROLE_} prefix.
   *
   * @return unmodifiable set of role names
   */
  public Set<String> getRoles() {
    return roles;
  }
}
