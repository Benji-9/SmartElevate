package com.smartelevate.common.security;

import com.smartelevate.user.model.User;
import com.smartelevate.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.stereotype.Component;

/**
 * Convierte un JWT ya verificado en la autenticación de la app. El token solo trae el id (claim {@code sub}):
 * el rol se lee de la base en cada request (ADR 0009), así que un cambio de rol o una cuenta borrada rigen
 * enseguida y nunca se confía en un claim. Es una consulta por id.
 */
@Component
@RequiredArgsConstructor
public class UserAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final UserRepository users;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        User user = null;
        try {
            user = users.findById(UUID.fromString(jwt.getSubject())).orElse(null);
        } catch (IllegalArgumentException e) {
            // subject que no es un UUID: se trata igual que un usuario inexistente
        }
        if (user == null) {
            throw new InvalidBearerTokenException("Token inválido");
        }
        CurrentUser principal = new CurrentUser(user.getId(), user.getRole());
        return new UsernamePasswordAuthenticationToken(principal, jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
