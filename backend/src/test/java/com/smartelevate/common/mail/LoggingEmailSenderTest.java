package com.smartelevate.common.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.env.MockEnvironment;

/** Con el mail apagado, el cuerpo (con los links y sus tokens) solo va al log en dev (#344). */
@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTest {

    private static LoggingEmailSender withProfiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new LoggingEmailSender(environment);
    }

    @Test
    void bodyIsNotLoggedOutsideDev(CapturedOutput output) {
        withProfiles("prod").send("ana@uade.edu.ar", "Verificá tu email", "link?token=secreto-prod");

        assertThat(output.getAll()).contains("Verificá tu email").doesNotContain("secreto-prod");
    }

    @Test
    void bodyIsLoggedInDevToCopyTheLink(CapturedOutput output) {
        withProfiles("dev").send("ana@uade.edu.ar", "Verificá tu email", "link?token=secreto-dev");

        assertThat(output.getAll()).contains("secreto-dev");
    }
}
