import java.net.HttpURLConnection;
import java.net.URI;

// Liveness/startup probe: /auth/me returns 401 without creating a session or loading books.
// Database readiness is checked by PostgreSQL and by Flyway during application startup.
public final class Healthcheck {
    public static void main(String[] args) throws Exception {
        var connection = (HttpURLConnection) URI.create("http://127.0.0.1:8080/auth/me").toURL().openConnection();
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        try {
            System.exit(connection.getResponseCode() == 401 ? 0 : 1);
        } finally {
            connection.disconnect();
        }
    }
}
