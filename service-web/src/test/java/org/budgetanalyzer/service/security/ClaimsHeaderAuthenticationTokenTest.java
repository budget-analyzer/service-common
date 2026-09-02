package org.budgetanalyzer.service.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for {@link ClaimsHeaderAuthenticationToken}.
 *
 * <p>Verifies the authentication token correctly stores user identity and authorities.
 */
class ClaimsHeaderAuthenticationTokenTest {

  @Test
  void authenticatedSetsAuthoritiesCorrectly() {
    var authorities =
        List.<GrantedAuthority>of(
            new SimpleGrantedAuthority("transactions:read"),
            new SimpleGrantedAuthority("ROLE_USER"));

    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), authorities);

    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("transactions:read", "ROLE_USER");
  }

  @Test
  void getNameReturnsUserId() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getName()).isEqualTo("usr_abc123");
  }

  @Test
  void getPrincipalReturnsUserId() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getPrincipal()).isEqualTo("usr_abc123");
  }

  @Test
  void getCredentialsReturnsEmptyString() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getCredentials()).isEqualTo("");
  }

  @Test
  void isAuthenticatedReturnsTrue() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void getRolesReturnsRoleSet() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated(
            "usr_abc123", Set.of("ADMIN", "USER"), List.of());

    assertThat(token.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
  }

  @Test
  void getRolesReturnsUnmodifiableSet() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThatThrownBy(() -> token.getRoles().add("ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getAuthoritiesReturnsUnmodifiableCollection() {
    var authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("transactions:read"));

    var token = ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), authorities);

    assertThatThrownBy(() -> token.getAuthorities().add(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void authenticatedHandlesEmptyPermissionsAndRoles() {
    var token = ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), List.of());

    assertThat(token.getAuthorities()).isEmpty();
    assertThat(token.getRoles()).isEmpty();
    assertThat(token.getName()).isEqualTo("usr_abc123");
    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void authenticatedRejectsNullUserId() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated(null, Set.of(), List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void authenticatedRejectsBlankUserId() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("   ", Set.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void authenticatedRejectsNullRoles() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", null, List.of()))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void authenticatedRejectsNullAuthorities() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void authenticatedDefensivelyCopiesRolesAndAuthorities() {
    var roles = new LinkedHashSet<String>();
    roles.add("USER");
    var authorities = new ArrayList<GrantedAuthority>();
    authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

    var token = ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", roles, authorities);

    roles.add("ADMIN");
    authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));

    assertThat(token.getRoles()).containsExactly("USER");
    assertThat(token.getAuthorities())
        .extracting(GrantedAuthority::getAuthority)
        .containsExactly("ROLE_USER");
  }

  @Test
  void constructorIsPrivate() throws Exception {
    var constructor =
        ClaimsHeaderAuthenticationToken.class.getDeclaredConstructor(
            String.class, Set.class, java.util.Collection.class);

    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
  }
}
