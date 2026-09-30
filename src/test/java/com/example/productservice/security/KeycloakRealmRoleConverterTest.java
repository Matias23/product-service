package com.example.productservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class KeycloakRealmRoleConverterTest {

    private final KeycloakRealmRoleConverter converter = new KeycloakRealmRoleConverter();

    @Test
    void convert_mapsRealmRolesToPrefixedAuthorities() {
        var jwt = jwtWithClaim("realm_access", Map.of("roles", List.of("USER", "ADMIN")));

        assertThat(converter.convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_USER", "ROLE_ADMIN");
    }

    @Test
    void convert_returnsEmptyWhenRealmAccessMissing() {
        assertThat(converter.convert(jwtWithClaim("sub", "123"))).isEmpty();
    }

    @Test
    void convert_returnsEmptyWhenRolesMalformed() {
        assertThat(converter.convert(jwtWithClaim("realm_access", Map.of("roles", "ADMIN")))).isEmpty();
    }

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token").header("alg", "none").claim(name, value).build();
    }
}
