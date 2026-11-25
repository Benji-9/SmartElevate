package com.smartelevate.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Dev y tests: no manda nada, deja el mail en el log para poder copiar el link. El cuerpo trae links con tokens de
 * verificación y de reset: solo se muestra en el perfil dev. Si alguien deja {@code APP_MAIL_ENABLED=false} en otro
 * entorno, el log muestra a quién iba y el asunto, pero no el link (#344).
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private final boolean logBody;

    public LoggingEmailSender(Environment environment) {
        this.logBody = environment.acceptsProfiles(Profiles.of("dev"));
    }

    @Override
    public void send(String to, String subject, String body) {
        if (logBody) {
            log.info("Mail NO enviado (app.mail.enabled=false). Para: {} | Asunto: {}\n{}", to, subject, body);
        } else {
            log.info("Mail NO enviado (app.mail.enabled=false). Para: {} | Asunto: {} | Cuerpo omitido: "
                    + "solo se muestra en el perfil dev", to, subject);
        }
    }
}
