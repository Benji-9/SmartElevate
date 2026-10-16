package com.smartelevate.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/** Hilos y cola del executor de mails se validan al arrancar. */
class MailAsyncPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(AsyncConfig.class);

    @Test
    void startsWithTheDefaultsAndWithAZeroQueue() {
        runner.run(ctx -> assertThat(ctx.getBean(MailAsyncProperties.class)).isEqualTo(new MailAsyncProperties(2, 50)));
        runner.withPropertyValues("app.mail.async.threads=1", "app.mail.async.queue=0")
                .run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void failsToStartWithoutThreadsOrWithANegativeQueue() {
        runner.withPropertyValues("app.mail.async.threads=0").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("app.mail.async.queue=-1").run(ctx -> assertThat(ctx).hasFailed());
    }
}
