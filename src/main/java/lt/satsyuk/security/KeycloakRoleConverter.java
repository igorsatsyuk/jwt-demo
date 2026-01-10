package lt.satsyuk.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        Collection<GrantedAuthority> authorities = new ArrayList<>();

        // -----------------------------
        // 1. Realm roles
        // -----------------------------
        Map<String, Object> realmAccess = jwt.getClaim("realm_access");
        if (realmAccess != null) {
            Object rolesObj = realmAccess.get("roles");
            if (rolesObj instanceof List<?> roles) {
                roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
        }

        // -----------------------------
        // 2. Client roles (resource_access.<client>.roles)
        // -----------------------------
        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
        if (resourceAccess != null) {
            resourceAccess.forEach((client, access) -> {
                if (access instanceof Map<?, ?> accessMap) {
                    Object rolesObj = accessMap.get("roles");
                    if (rolesObj instanceof List<?> roles) {
                        roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
                    }
                }
            });
        }

        return authorities;
    }
}