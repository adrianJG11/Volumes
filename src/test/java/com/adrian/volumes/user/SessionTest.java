package com.adrian.volumes.user;

import com.adrian.volumes.support.ApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.address=127.0.0.1")
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SessionTest {
    @LocalServerPort int port;
    @Autowired UserRegistration registration;
    private final JsonMapper json = JsonMapper.builder().build();
    private static final String PASSWORD = "a session test passphrase";

    @Test
    void loginRotatesSessionAndCsrfAndLogoutRevokesAuthentication() throws Exception {
        var user = registration.register("reader@example.com", PASSWORD);
        try (var client = new ApiClient(port)) {
            assertEquals(401, client.request("GET", "/auth/me", null).statusCode());
            var csrfResponse = client.request("GET", "/auth/csrf", null);
            String oldToken = json.readTree(csrfResponse.body()).get("token").asText();
            String oldCookie = csrfResponse.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            assertTrue(csrfResponse.headers().firstValue("Set-Cookie").orElseThrow().contains("HttpOnly"));
            var login = client.login(" Reader@Example.com ", PASSWORD);
            assertEquals(204, login.statusCode());
            String newCookie = login.headers().firstValue("Set-Cookie").orElseThrow().split(";", 2)[0];
            assertNotEquals(oldCookie, newCookie);
            var me = client.request("GET", "/auth/me", null);
            assertEquals(200, me.statusCode());
            assertEquals(user, json.readValue(me.body(), User.class));
            assertEquals(403, client.send("POST", "/auth/logout", null, null, oldToken).statusCode());
            assertEquals(403, client.send("POST", "/auth/logout", null, null, null).statusCode());
            assertEquals(200, client.request("GET", "/auth/me", null).statusCode());
            assertEquals(204, client.request("POST", "/auth/logout", null).statusCode());
            assertEquals(401, client.request("GET", "/auth/me", null).statusCode());
        }
    }

    @Test
    void invalidLoginIsGenericAndDoesNotAuthenticate() throws Exception {
        registration.register("reader@example.com", PASSWORD);
        try (var client = new ApiClient(port)) {
            var wrongPassword = client.login("reader@example.com", "wrong password");
            var missingAccount = client.login("missing@example.com", PASSWORD);
            assertEquals(401, wrongPassword.statusCode());
            assertEquals(401, missingAccount.statusCode());
            assertEquals(wrongPassword.body(), missingAccount.body());
            assertFalse(wrongPassword.body().contains("reader@example.com"));
            assertEquals(401, client.login("reader@example.com", "x".repeat(100)).statusCode());
            assertEquals(401, client.request("GET", "/auth/me", null).statusCode());
        }
    }

    @Test
    void csrfAndAuthenticationProtectMutationsWhileCatalogReadsArePublic() throws Exception {
        try (var client = new ApiClient(port)) {
            assertEquals(200, client.request("GET", "/books", null).statusCode());
            for (String path : new String[] {"/auth/register", "/auth/login", "/books"}) {
                assertEquals(403, client.send("POST", path, "{}", "application/json", null).statusCode());
            }
            assertEquals(401, client.request("POST", "/books", "{}").statusCode());
            assertEquals("[]", client.request("GET", "/books", null).body());
        }
    }

    @Test
    void separateSessionsReturnTheirOwnAccountIgnoringSuppliedIdentity() throws Exception {
        var alice = registration.register("alice@example.com", PASSWORD);
        var bob = registration.register("bob@example.com", PASSWORD);
        try (var a = new ApiClient(port); var b = new ApiClient(port)) {
            assertEquals(204, a.login(alice.email(), PASSWORD).statusCode());
            assertEquals(204, b.login(bob.email(), PASSWORD).statusCode());
            assertEquals(alice, json.readValue(a.request("GET", "/auth/me?userId=" + bob.id(), null).body(), User.class));
            assertEquals(bob, json.readValue(b.request("GET", "/auth/me", null).body(), User.class));
        }
    }
}
