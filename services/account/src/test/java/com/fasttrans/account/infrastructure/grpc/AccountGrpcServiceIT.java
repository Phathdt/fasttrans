package com.fasttrans.account.infrastructure.grpc;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import com.fasttrans.account.support.AbstractPostgresIT;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end gRPC integration test using in-process channel.
 * AccountQueryService uses the real Postgres container (seeded via Flyway).
 */
@SpringBootTest
@ActiveProfiles("test")
class AccountGrpcServiceIT extends AbstractPostgresIT {

    @Autowired AccountQueryService accountQueryService;

    private Server inProcessServer;
    private ManagedChannel inProcessChannel;
    private AccountServiceGrpc.AccountServiceBlockingStub stub;

    @BeforeEach
    void startInProcessServer() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        inProcessServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(new AccountGrpcService(accountQueryService))
                .build()
                .start();

        inProcessChannel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        stub = AccountServiceGrpc.newBlockingStub(inProcessChannel);
    }

    @AfterEach
    void stopServer() throws Exception {
        inProcessChannel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        inProcessServer.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    // Seeded values from V20260703090000 migration
    private static final String ALICE_REF   = "100000000001";
    private static final String ALICE_USER  = "11111111-1111-1111-1111-111111111111";
    private static final String BOB_REF     = "200000000001";

    @Test
    void validateOwnership_aliceOwnsHerAccount_returnsTrue() {
        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef(ALICE_REF)
                        .setUserId(ALICE_USER)
                        .build());

        assertThat(resp.getOwned()).isTrue();
    }

    @Test
    void validateOwnership_aliceDoesNotOwnBobAccount_returnsFalse() {
        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef(BOB_REF)
                        .setUserId(ALICE_USER)
                        .build());

        assertThat(resp.getOwned()).isFalse();
    }

    @Test
    void validateOwnership_unknownAccountRef_returnsFalse() {
        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef("000000000000")
                        .setUserId(ALICE_USER)
                        .build());

        assertThat(resp.getOwned()).isFalse();
    }
}
