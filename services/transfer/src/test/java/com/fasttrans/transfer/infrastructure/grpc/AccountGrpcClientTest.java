package com.fasttrans.transfer.infrastructure.grpc;

import com.fasttrans.account.grpc.AccountServiceGrpc;
import com.fasttrans.account.grpc.ValidateOwnershipRequest;
import com.fasttrans.account.grpc.ValidateOwnershipResponse;
import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountGrpcClientTest {

    // We stub the blocking stub directly via a mock; the client uses it via @GrpcClient
    // which we inject via ReflectionTestUtils to avoid needing a gRPC server.
    @Mock
    private AccountServiceGrpc.AccountServiceBlockingStub blockingStub;

    @Mock
    private AccountServiceGrpc.AccountServiceBlockingStub stubbedWithDeadline;

    private AccountGrpcClient client;

    @BeforeEach
    void setUp() {
        client = new AccountGrpcClient();
        ReflectionTestUtils.setField(client, "stub", blockingStub);
        // withDeadlineAfter always returns stubbedWithDeadline in these tests
        when(blockingStub.withDeadlineAfter(anyLong(), any(TimeUnit.class)))
                .thenReturn(stubbedWithDeadline);
    }

    @Test
    void validateOwnership_returns_true_when_owned() {
        when(stubbedWithDeadline.validateOwnership(any(ValidateOwnershipRequest.class)))
                .thenReturn(ValidateOwnershipResponse.newBuilder().setOwned(true).build());

        boolean result = client.validateOwnership("user-1", "ACC001");

        assertThat(result).isTrue();
    }

    @Test
    void validateOwnership_returns_false_when_not_owned() {
        when(stubbedWithDeadline.validateOwnership(any(ValidateOwnershipRequest.class)))
                .thenReturn(ValidateOwnershipResponse.newBuilder().setOwned(false).build());

        boolean result = client.validateOwnership("user-1", "ACC001");

        assertThat(result).isFalse();
    }

    @Test
    void validateOwnership_throws_unavailable_on_UNAVAILABLE_status() {
        StatusRuntimeException cause = new StatusRuntimeException(Status.UNAVAILABLE);
        when(stubbedWithDeadline.validateOwnership(any(ValidateOwnershipRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> client.validateOwnership("user-1", "ACC001"))
                .isInstanceOf(AccountUnavailableException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void validateOwnership_throws_unavailable_on_DEADLINE_EXCEEDED_status() {
        StatusRuntimeException cause = new StatusRuntimeException(Status.DEADLINE_EXCEEDED);
        when(stubbedWithDeadline.validateOwnership(any(ValidateOwnershipRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> client.validateOwnership("user-1", "ACC001"))
                .isInstanceOf(AccountUnavailableException.class);
    }

    @Test
    void validateOwnership_throws_unavailable_on_other_grpc_error() {
        StatusRuntimeException cause = new StatusRuntimeException(Status.INTERNAL);
        when(stubbedWithDeadline.validateOwnership(any(ValidateOwnershipRequest.class)))
                .thenThrow(cause);

        assertThatThrownBy(() -> client.validateOwnership("user-1", "ACC001"))
                .isInstanceOf(AccountUnavailableException.class)
                .hasMessageContaining("gRPC error");
    }
}
