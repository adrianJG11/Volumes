package com.adrian.volumes.support;

import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** A real HTTP client with its own cookies, like one browser session. */
public class ApiClient implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;
    private String csrf;

    public ApiClient(int port) { base = "http://127.0.0.1:" + port; }

    public void refreshCsrf() throws Exception {
        var response = send("GET", "/auth/csrf", null, null, null);
        csrf = JsonMapper.builder().build().readTree(response.body()).get("token").asText();
    }

    public HttpResponse<String> request(String method, String path, String body) throws Exception {
        if (!method.equals("GET") && csrf == null) refreshCsrf();
        return send(method, path, body, "application/json", csrf);
    }

    public HttpResponse<String> login(String email, String password) throws Exception {
        refreshCsrf();
        var response = send("POST", "/auth/login", "email=" + encode(email) + "&password=" + encode(password),
            "application/x-www-form-urlencoded", csrf);
        if (response.statusCode() == 204) refreshCsrf();
        return response;
    }

    public HttpResponse<String> send(String method, String path, String body, String type, String token) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15))
            .header("Accept", "application/json, application/problem+json");
        if (type != null) request.header("Content-Type", type);
        if (token != null) request.header("X-CSRF-TOKEN", token);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    public void close() { client.close(); }
}
