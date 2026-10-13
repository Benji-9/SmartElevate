package com.smartelevate.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.smartelevate.common.db.PostgresTestcontainersConfig;
import com.smartelevate.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * La carrera del registro contra Postgres real (H2 no se comporta igual ante un INSERT que choca).
 * Sin Docker se saltea; en CI corre siempre.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(PostgresTestcontainersConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class RegistrationPostgresTest {

    private static final String BODY = """
            {"fullName":"Ana Pérez","email":"ana@uade.edu.ar","legajo":"1111111","password":"una-clave-larga",
             "declaredUserType":"STUDENT"}""";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository users;

    @AfterEach
    void cleanUsers() {
        users.deleteAll();
    }

    @Test
    void whenManyRequestsRegisterTheSameAccountExactlyOneWinsAndTheRestGetConflict() throws Exception {
        int requests = 12;
        ExecutorService pool = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(BODY))
                        .andReturn().getResponse().getStatus();
            }));
        }

        start.countDown(); // todos a la vez
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> result : results) {
            statuses.add(result.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(statuses).filteredOn(status -> status == 201).hasSize(1);
        assertThat(statuses).filteredOn(status -> status == 409).hasSize(requests - 1);
        assertThat(users.count()).isEqualTo(1);
    }
}
