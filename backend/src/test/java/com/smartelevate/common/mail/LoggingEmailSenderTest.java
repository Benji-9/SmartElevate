package com.smartelevate.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/** El log del mail no filtra los links con token fuera del perfil dev (#344). */
class LoggingEmailSenderTest {

    private static final String BODY = "Confirmá tu email: https://app.test/verificar-email?token=TOKEN-SECRETO";

    private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingEmailSender.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void capture() {
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void release() {
        logger.detachAppender(logs);
    }

    private List<String> sendWithProfile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        new LoggingEmailSender(environment).send("ana@uade.edu.ar", "Verificá tu email", BODY);
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void inDevItLogsTheBodySoTheLinkCanBeCopied() {
        assertThat(sendWithProfile("dev")).singleElement().asString().contains("TOKEN-SECRETO");
    }

    @Test
    void outsideDevItLogsWhoAndWhatButNotTheLink() {
        assertThat(sendWithProfile("prod")).singleElement().asString()
                .contains("ana@uade.edu.ar", "Verificá tu email")
                .doesNotContain("TOKEN-SECRETO", "verificar-email");
    }
}
