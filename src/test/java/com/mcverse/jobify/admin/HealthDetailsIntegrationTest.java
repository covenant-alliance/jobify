package com.mcverse.jobify.admin;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.test.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Health is public for load balancers and the Docker health check, but only says UP or DOWN. Components and details
 * (database product, disk paths and sizes) are for administrators. Runs over real HTTP because the actuator checks
 * the caller's role through the servlet request, which MockMvc does not model faithfully.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:health-test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
class HealthDetailsIntegrationTest {

    @LocalServerPort
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String tokenOf(String username) throws Exception {
        HttpResponse<String> login = send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"" + username + "\",\"password\":\"password\"}")));
        return JsonPath.read(login.body(), "$.token");
    }

    private HttpResponse<String> health(String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"));
        if (token != null) request.header("Authorization", "Bearer " + token);
        return send(request);
    }

    @Test
    void anonymousCallersSeeOnlyTheStatus() throws Exception {
        HttpResponse<String> response = health(null);
        assertEquals(200, response.statusCode());
        assertEquals("UP", JsonPath.read(response.body(), "$.status"));
        assertFalse(response.body().contains("components"), response.body());
        assertFalse(response.body().contains("diskSpace"), response.body());
    }

    @Test
    void ordinaryUsersSeeOnlyTheStatus() throws Exception {
        HttpResponse<String> response = health(tokenOf("alice_s"));
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("components"), response.body());
    }

    @Test
    void administratorsSeeTheComponents() throws Exception {
        HttpResponse<String> response = health(tokenOf("admin"));
        assertEquals(200, response.statusCode());
        assertEquals("UP", JsonPath.read(response.body(), "$.components.db.status"));
        assertTrue(response.body().contains("diskSpace"), response.body());
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        for (String path : new String[] {"/actuator/env", "/actuator/beans", "/actuator/heapdump", "/actuator/metrics"}) {
            HttpResponse<String> response = send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + path)).header("Authorization", "Bearer " + tokenOf("admin")));
            assertTrue(response.statusCode() == 404 || response.statusCode() == 401 || response.statusCode() == 403,
                    path + " answered " + response.statusCode());
        }
    }
}
