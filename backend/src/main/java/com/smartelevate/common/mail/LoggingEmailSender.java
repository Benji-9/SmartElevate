package com.smartelevate.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Con el mail apagado no manda nada y deja el mail en el log. El cuerpo (con los links y sus tokens) solo en dev,
 * para poder copiar el link; en cualquier otro perfil, solo destinatario y asunto (#344).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private final Environment environment;

    @Override
    public void send(String to, String subject, String body) {
        if (environment.matchesProfiles("dev")) {
            log.info("Mail NO enviado (app.mail.enabled=false). Para: {} | Asunto: {}\n{}", to, subject, body);
        } else {
            log.info("Mail NO enviado (app.mail.enabled=false). Para: {} | Asunto: {}", to, subject);
        }
    }
}
