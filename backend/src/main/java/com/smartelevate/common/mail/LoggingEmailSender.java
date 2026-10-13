package com.smartelevate.common.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Dev y tests: no manda nada, deja el mail en el log para poder copiar el link. */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(String to, String subject, String body) {
        log.info("Mail NO enviado (app.mail.enabled=false). Para: {} | Asunto: {}\n{}", to, subject, body);
    }
}
