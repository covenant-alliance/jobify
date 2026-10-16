package com.mcverse.matching.grpc;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.mcverse.matching.v1.JobProfile;
import com.mcverse.matching.v1.MatchServiceGrpc;
import com.mcverse.matching.v1.RequiredSkill;
import com.mcverse.matching.v1.ScoreRequest;
import com.mcverse.matching.v1.SeekerProfile;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.MetadataUtils;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Starts the real server on a free port and calls it over the network, which is what proves the wiring:
 * the service is registered, the token check wraps it, and the health service stays open.
 */
@SpringBootTest(properties = {"server.port=0", "matching.auth.token=test-token"})
class MatchingServerTest {

    private static final int PORT = freePort();
    private static ManagedChannel channel;

    @DynamicPropertySource
    static void port(DynamicPropertyRegistry registry) {
        registry.add("spring.grpc.server.port", () -> PORT);
    }

    @BeforeAll
    static void connect() {
        channel = Grpc.newChannelBuilder("localhost:" + PORT, InsecureChannelCredentials.create()).build();
    }

    @AfterAll
    static void disconnect() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static MatchServiceGrpc.MatchServiceBlockingStub stub(String authorization) {
        MatchServiceGrpc.MatchServiceBlockingStub stub = MatchServiceGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, TimeUnit.SECONDS);
        if (authorization == null) {
            return stub;
        }
        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), authorization);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static ScoreRequest request() {
        return ScoreRequest.newBuilder()
                .setSeeker(SeekerProfile.newBuilder().setSeekerId("s"))
                .setJob(JobProfile.newBuilder().setJobId(1).addRequiredSkills(RequiredSkill.newBuilder().setSkillId("a")))
                .build();
    }

    @Test
    void aCallWithTheRightTokenIsScored() {
        assertEquals(0, stub("Bearer test-token").score(request()).getMatch().getScore());
    }

    @Test
    void aCallWithoutATokenIsUnauthenticated() {
        StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(
                StatusRuntimeException.class, () -> stub(null).score(request()));
        assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode());
    }

    @Test
    void aWrongOrMalformedTokenIsUnauthenticated() {
        for (String header : new String[] {"Bearer wrong", "test-token", "Basic dGVzdA==", "Bearer "}) {
            StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(
                    StatusRuntimeException.class, () -> stub(header).score(request()), header);
            assertEquals(Status.Code.UNAUTHENTICATED, e.getStatus().getCode(), header);
        }
    }

    @Test
    void invalidRequestsStillReachTheAdapterOverTheWire() {
        StatusRuntimeException e = org.junit.jupiter.api.Assertions.assertThrows(StatusRuntimeException.class,
                () -> stub("Bearer test-token").score(ScoreRequest.getDefaultInstance()));
        assertEquals(Status.Code.INVALID_ARGUMENT, e.getStatus().getCode());
    }

    @Test
    void healthIsServingAndNeedsNoToken() {
        HealthCheckResponse response = HealthGrpc.newBlockingStub(channel)
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .check(HealthCheckRequest.getDefaultInstance());
        assertEquals(HealthCheckResponse.ServingStatus.SERVING, response.getStatus());
    }
}
