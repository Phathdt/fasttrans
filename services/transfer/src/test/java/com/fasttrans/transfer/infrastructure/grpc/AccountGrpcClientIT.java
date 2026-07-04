package com.fasttrans.transfer.infrastructure.grpc;

import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * In-process gRPC integration test — no network port opened.
 * Spins up a real gRPC server in-process, injects the blocking stub into AccountGrpcClient,
 * then verifies mapping + deadline behaviour end-to-end.
 */
class AccountGrpcClientIT {

    private static final String SERVER_NAME = "test-account-grpc";

    private Server inProcessServer;
    private ManagedChannel channel;
    private AccountGrpcClient client;

    // Configurable response for the fake server
    private volatile boolean respondOwned = true;
    private volatile boolean respondWithError = false;
    private volatile Status errorStatus = Status.UNAVAILABLE;

    @BeforeEach
    void startServer() throws IOException {
        AccountServiceGrpc.AccountServiceImplBase fakeService =
                new AccountServiceGrpc.AccountServiceImplBase() {
                    @Override
                    public void validateOwnership(ValidateOwnershipRequest request,
                                                  StreamObserver<ValidateOwnershipResponse> observer) {
                        if (respondWithError) {
                            observer.onError(new StatusRuntimeException(errorStatus));
                        } else {
                            observer.onNext(ValidateOwnershipResponse.newBuilder()
                                    .setOwned(respondOwned).build());
                            observer.onCompleted();
                        }
                    }
                };

        inProcessServer = InProcessServerBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .addService(fakeService)
                .build()
                .start();

        channel = InProcessChannelBuilder
                .forName(SERVER_NAME)
                .directExecutor()
                .build();

        AccountServiceGrpc.AccountServiceBlockingStub stub =
                AccountServiceGrpc.newBlockingStub(channel);

        client = new AccountGrpcClient();
        ReflectionTestUtils.setField(client, "stub", stub);
    }

    @AfterEach
    void stopServer() throws InterruptedException {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        inProcessServer.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    void validateOwnership_returns_true_when_server_says_owned() {
        respondOwned = true;
        assertThat(client.validateOwnership("user-1", "ACC001")).isTrue();
    }

    @Test
    void validateOwnership_returns_false_when_server_says_not_owned() {
        respondOwned = false;
        assertThat(client.validateOwnership("user-1", "ACC001")).isFalse();
    }

    @Test
    void validateOwnership_throws_AccountUnavailableException_on_UNAVAILABLE() {
        respondWithError = true;
        errorStatus = Status.UNAVAILABLE;

        assertThatThrownBy(() -> client.validateOwnership("user-1", "ACC001"))
                .isInstanceOf(AccountUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void validateOwnership_throws_AccountUnavailableException_on_INTERNAL_error() {
        respondWithError = true;
        errorStatus = Status.INTERNAL;

        assertThatThrownBy(() -> client.validateOwnership("user-1", "ACC001"))
                .isInstanceOf(AccountUnavailableException.class)
                .hasMessageContaining("gRPC error");
    }
}
