package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.entities.Account;
import com.fasttrans.account.domain.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
class AccountControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountQueryService accountQueryService;

    private Account account(String ref, UUID userId, long balance) {
        return new Account(UUID.randomUUID(), ref, userId, "alice", balance, "VND", Instant.now());
    }

    // ── GET /accounts ─────────────────────────────────────────────────────────

    @Test
    void listAccounts_validUserId_returnsAccountList() throws Exception {
        UUID userId = UUID.randomUUID();
        Account a = account("100000000001", userId, 1_000_000L);
        when(accountQueryService.listAccounts(userId)).thenReturn(List.of(a));

        mockMvc.perform(get("/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].accountRef").value("100000000001"))
                .andExpect(jsonPath("$[0].balance").value(1_000_000))
                .andExpect(jsonPath("$[0].currency").value("VND"));
    }

    @Test
    void listAccounts_noAccounts_returnsEmptyArray() throws Exception {
        UUID userId = UUID.randomUUID();
        when(accountQueryService.listAccounts(userId)).thenReturn(List.of());

        mockMvc.perform(get("/accounts").header("X-User-Id", userId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listAccounts_missingHeader_returns400() throws Exception {
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /accounts/{ref} ───────────────────────────────────────────────────

    @Test
    void lookupAccount_found_returnsRefAndOwnerName() throws Exception {
        UUID userId = UUID.randomUUID();
        Account a = account("100000000002", userId, 50_000L);
        when(accountQueryService.lookup("100000000002")).thenReturn(Optional.of(a));

        mockMvc.perform(get("/accounts/100000000002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountRef").value("100000000002"))
                .andExpect(jsonPath("$.ownerName").value("alice"))
                // balance must NOT be in lookup response (privacy)
                .andExpect(jsonPath("$.balance").doesNotExist());
    }

    @Test
    void lookupAccount_notFound_returns404() throws Exception {
        when(accountQueryService.lookup("999999999999"))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/accounts/999999999999"))
                .andExpect(status().isNotFound());
    }
}
