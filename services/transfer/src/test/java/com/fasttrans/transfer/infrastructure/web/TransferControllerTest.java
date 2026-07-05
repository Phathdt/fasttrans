package com.fasttrans.transfer.infrastructure.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasttrans.commons.web.WebCommonsAutoConfiguration;
import com.fasttrans.transfer.application.dto.CreateTransferRequest;
import com.fasttrans.transfer.application.dto.TransferResponse;
import com.fasttrans.transfer.application.services.TransferService;
import com.fasttrans.transfer.domain.exception.AccountUnavailableException;
import com.fasttrans.transfer.domain.exception.OwnershipDeniedException;
import com.fasttrans.transfer.domain.exception.TransferNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {TransferController.class, TransferExceptionHandler.class})
@Import(WebCommonsAutoConfiguration.class)
class TransferControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean  private TransferService transferService;

    private static final String USER_ID = UUID.randomUUID().toString();
    private static final String IDEM_KEY = "test-idem-key-1";

    @Test
    void create_returns_201_with_transfer_id_and_status() throws Exception {
        TransferResponse response = makeResponse(UUID.randomUUID(), "PENDING");
        when(transferService.create(any(), anyString(), any())).thenReturn(response);

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(response.id()))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void create_returns_400_when_idempotency_key_header_missing() throws Exception {
        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", USER_ID)
                        // No Idempotency-Key header
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns_400_when_x_user_id_header_missing() throws Exception {
        mockMvc.perform(post("/transfers")
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns_400_when_request_body_invalid() throws Exception {
        // amount is 0 (not @Positive)
        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountRef\":\"ACC001\",\"toAccountRef\":\"ACC002\",\"amount\":0,\"currency\":\"VND\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_returns_403_when_ownership_denied() throws Exception {
        when(transferService.create(any(), anyString(), any()))
                .thenThrow(new OwnershipDeniedException("Account does not belong to user"));

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns_503_when_account_service_unavailable() throws Exception {
        when(transferService.create(any(), anyString(), any()))
                .thenThrow(new AccountUnavailableException("timeout", new RuntimeException()));

        mockMvc.perform(post("/transfers")
                        .header("X-User-Id", USER_ID)
                        .header("Idempotency-Key", IDEM_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateTransferRequest("ACC001", "ACC002", 100_000L, "VND"))))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void list_returns_200_with_transfers() throws Exception {
        List<TransferResponse> transfers = List.of(
                makeResponse(UUID.randomUUID(), "PENDING"),
                makeResponse(UUID.randomUUID(), "COMPLETED"));
        when(transferService.list(any())).thenReturn(transfers);

        mockMvc.perform(get("/transfers")
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void detail_returns_200_when_found() throws Exception {
        UUID id = UUID.randomUUID();
        TransferResponse response = makeResponse(id, "COMPLETED");
        when(transferService.detail(eq(id), any())).thenReturn(response);

        mockMvc.perform(get("/transfers/{id}", id)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void detail_returns_404_when_not_found() throws Exception {
        UUID id = UUID.randomUUID();
        when(transferService.detail(eq(id), any()))
                .thenThrow(new TransferNotFoundException("Transfer " + id + " not found"));

        mockMvc.perform(get("/transfers/{id}", id)
                        .header("X-User-Id", USER_ID))
                .andExpect(status().isNotFound());
    }

    private TransferResponse makeResponse(UUID id, String status) {
        return new TransferResponse(id.toString(), "ACC001", "ACC002",
                100_000L, "VND", status, null, OffsetDateTime.now());
    }
}
