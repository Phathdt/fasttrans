package com.fasttrans.account.infrastructure.grpc;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountGrpcServiceTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    @Mock AccountQueryService accountQueryService;

    AccountServiceGrpc.AccountServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        String serverName = InProcessServerBuilder.generateName();

        grpcCleanup.register(
                InProcessServerBuilder.forName(serverName)
                        .directExecutor()
                        .addService(new AccountGrpcService(accountQueryService))
                        .build()
                        .start()
        );

        stub = AccountServiceGrpc.newBlockingStub(
                grpcCleanup.register(
                        InProcessChannelBuilder.forName(serverName)
                                .directExecutor()
                                .build()
                )
        );
    }

    @Test
    void validateOwnership_ownedAccount_returnsTrue() {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.validateOwnership("ref1", userId)).thenReturn(true);

        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef("ref1")
                        .setUserId(userId.toString())
                        .build()
        );

        assertThat(resp.getOwned()).isTrue();
    }

    @Test
    void validateOwnership_notOwnedAccount_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.validateOwnership("ref2", userId)).thenReturn(false);

        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef("ref2")
                        .setUserId(userId.toString())
                        .build()
        );

        assertThat(resp.getOwned()).isFalse();
    }

    @Test
    void validateOwnership_serviceThrowsException_returnsFalse() {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.validateOwnership(any(), eq(userId)))
                .thenThrow(new RuntimeException("db error"));

        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef("any-ref")
                        .setUserId(userId.toString())
                        .build()
        );

        // Exception is swallowed — owned=false is returned, no gRPC error
        assertThat(resp.getOwned()).isFalse();
    }

    @Test
    void validateOwnership_invalidUuidInRequest_returnsFalse() {
        // UUID.fromString will throw; the service should swallow it and return owned=false
        ValidateOwnershipResponse resp = stub.validateOwnership(
                ValidateOwnershipRequest.newBuilder()
                        .setAccountRef("any-ref")
                        .setUserId("not-a-uuid")
                        .build()
        );

        assertThat(resp.getOwned()).isFalse();
    }
}
