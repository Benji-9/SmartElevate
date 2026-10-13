package com.smartelevate.common.security;

import com.smartelevate.user.repository.UserRepository;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Para los {@code @WebMvcTest}: carga el {@link SecurityConfig} real (así se prueban las reglas de acceso
 * de verdad) con el decoder y el repositorio de usuarios mockeados. Los tests con token usan
 * {@code SecurityMockMvcRequestPostProcessors.jwt()} o {@code authentication(...)}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({SecurityConfig.class, UserAuthenticationConverter.class})
@MockitoBean(types = {JwtDecoder.class, UserRepository.class})
public @interface WithAppSecurity {
}
