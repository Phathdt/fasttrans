package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.exception.AccountNotFoundException;
import com.fasttrans.commons.web.WebCommonsAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(WebCommonsAutoConfiguration.class)
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountQueryService accountQueryService;

    private Account account(String ref, UUID userId, long balance) {
        return new Account(UUID.randomUUID(), ref, userId, "alice", balance, "VND", Instant.now());
    }

    // ── GET /accounts ─────────────────────────────────────────────────────────

    @Test
    void listAccounts_validUserId_returnsAccountListWrapped() throws Exception {
        UUID userId = UUID.randomUUID();
        Account a = account("100000000001", userId, 1_000_000L);
        when(accountQueryService.listAccounts(userId)).thenReturn(List.of(a));

        mockMvc.perform(get("/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountRef").value("100000000001"))
                .andExpect(jsonPath("$.data[0].balance").value(1_000_000))
                .andExpect(jsonPath("$.data[0].currency").value("VND"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void listAccounts_noAccounts_returnsEmptyArrayWrapped() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.listAccounts(userId)).thenReturn(List.of());

        mockMvc.perform(get("/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void listAccounts_missingHeader_returns400WithErrorEnvelope() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    // ── GET /accounts/{ref} ───────────────────────────────────────────────────

    @Test
    void lookupAccount_found_returnsRefAndOwnerNameWrapped() throws Exception {
        UUID userId = UUID.randomUUID();
        Account a = account("100000000002", userId, 50_000L);
        when(accountQueryService.lookup("100000000002")).thenReturn(Optional.of(a));

        mockMvc.perform(get("/accounts/100000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountRef").value("100000000002"))
                .andExpect(jsonPath("$.data.ownerName").value("alice"))
                // balance must NOT be in lookup response (privacy)
                .andExpect(jsonPath("$.data.balance").doesNotExist())
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }

    @Test
    void lookupAccount_notFound_returns404WithErrorEnvelope() throws Exception {
        when(accountQueryService.lookup("999999999999"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/accounts/999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }
}
