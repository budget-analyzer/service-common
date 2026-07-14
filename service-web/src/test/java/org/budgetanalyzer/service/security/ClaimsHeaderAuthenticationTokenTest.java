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
  void authenticated_shouldSetAuthoritiesCorrectly() {
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
  void getName_shouldReturnUserId() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getName()).isEqualTo("usr_abc123");
  }

  @Test
  void getPrincipal_shouldReturnUserId() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getPrincipal()).isEqualTo("usr_abc123");
  }

  @Test
  void getCredentials_shouldReturnEmptyString() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.getCredentials()).isEqualTo("");
  }

  @Test
  void isAuthenticated_shouldReturnTrue() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void getRoles_shouldReturnRoleSet() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated(
            "usr_abc123", Set.of("ADMIN", "USER"), List.of());

    assertThat(token.getRoles()).containsExactlyInAnyOrder("ADMIN", "USER");
  }

  @Test
  void getRoles_shouldReturnUnmodifiableSet() {
    var token =
        ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of("USER"), List.of());

    assertThatThrownBy(() -> token.getRoles().add("ADMIN"))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getAuthorities_shouldReturnUnmodifiableCollection() {
    var authorities = List.<GrantedAuthority>of(new SimpleGrantedAuthority("transactions:read"));

    var token = ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), authorities);

    assertThatThrownBy(() -> token.getAuthorities().add(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void authenticated_shouldHandleEmptyPermissionsAndRoles() {
    var token = ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), List.of());

    assertThat(token.getAuthorities()).isEmpty();
    assertThat(token.getRoles()).isEmpty();
    assertThat(token.getName()).isEqualTo("usr_abc123");
    assertThat(token.isAuthenticated()).isTrue();
  }

  @Test
  void authenticated_shouldRejectNullUserId() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated(null, Set.of(), List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("userId must not be null");
  }

  @Test
  void authenticated_shouldRejectBlankUserId() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("   ", Set.of(), List.of()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("userId must not be blank");
  }

  @Test
  void authenticated_shouldRejectNullRoles() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", null, List.of()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("roles must not be null");
  }

  @Test
  void authenticated_shouldRejectNullAuthorities() {
    assertThatThrownBy(
            () -> ClaimsHeaderAuthenticationToken.authenticated("usr_abc123", Set.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("authorities must not be null");
  }

  @Test
  void authenticated_shouldDefensivelyCopyRolesAndAuthorities() {
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
  void constructor_shouldBePrivate() throws Exception {
    var constructor =
        ClaimsHeaderAuthenticationToken.class.getDeclaredConstructor(
            String.class, Set.class, java.util.Collection.class);

    assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
  }
}
