package com.adrian.volumes.user;

import com.adrian.volumes.user.api.RegistrationRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RegistrationTest {
    @LocalServerPort int port;
    @Autowired JdbcClient jdbc;
    @Autowired PasswordEncoder passwords;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PASSWORD = " a long passphrase with spaces ";

    private HttpResponse<String> post(String body) throws Exception {
        try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/auth/register"))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private HttpResponse<String> register(String email, String password) throws Exception {
        return post(json.writeValueAsString(Map.of("email", email, "password", password)));
    }

    @Test
    void storesSaltedHashesAndReturnsOnlyAccountIdentity() throws Exception {
        var response = register("  Reader@Example.com  ", PASSWORD);
        assertEquals(201, response.statusCode());
        var body = json.readTree(response.body());
        assertEquals(2, body.size());
        assertNotNull(java.util.UUID.fromString(body.get("id").asText()));
        assertEquals("reader@example.com", body.get("email").asText());
        assertEquals(201, register("second@example.com", PASSWORD).statusCode());
        var hashes = jdbc.sql("SELECT password_hash FROM users ORDER BY email").query(String.class).list();
        assertNotEquals(hashes.get(0), hashes.get(1));
        for (String hash : hashes) {
            assertNotEquals(PASSWORD, hash);
            assertTrue(passwords.matches(PASSWORD, hash));
            assertFalse(passwords.matches(PASSWORD.strip(), hash));
            assertFalse(response.body().contains(hash));
        }
        assertFalse(new RegistrationRequest("reader@example.com", PASSWORD).toString().contains(PASSWORD));
        assertFalse(json.writeValueAsString(new RegistrationRequest("reader@example.com", PASSWORD)).contains(PASSWORD));
    }

    @Test
    void rejectsInvalidInputWithoutSavingAccountsOrEchoingPasswords() throws Exception {
        for (String body : new String[] {"{}", "null", "{", "{\"email\":null,\"password\":null}"}) {
            assertEquals(400, post(body).statusCode());
        }
        assertEquals(400, register("not-an-email", PASSWORD).statusCode());
        for (String password : new String[] {"", "short", " ".repeat(20), "x".repeat(73), "é".repeat(37)}) {
            var response = register("reader@example.com", password);
            assertEquals(400, response.statusCode());
            if (!password.isEmpty()) assertFalse(response.body().contains(password));
        }
        assertEquals(0L, jdbc.sql("SELECT count(*) FROM users").query(Long.class).single());
        assertEquals(201, register("boundary@example.com", "é".repeat(36)).statusCode());
    }

    @Test
    void concurrentNormalizedDuplicatesCreateExactlyOneAccount() throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = IntStream.range(0, 4).mapToObj(i -> executor.submit(() ->
                register(i % 2 == 0 ? " Reader@Example.com " : "reader@example.com", PASSWORD).statusCode())).toList();
            int created = 0;
            for (Future<Integer> task : tasks) {
                int status = task.get();
                assertTrue(status == 201 || status == 409);
                if (status == 201) created++;
            }
            assertEquals(1, created);
            assertEquals(1L, jdbc.sql("SELECT count(*) FROM users").query(Long.class).single());
        }
    }
}
