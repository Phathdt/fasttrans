package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.exception.AccountNotFoundException;
import com.fasttrans.commons.web.WebCommonsAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@Import(WebCommonsAutoConfiguration.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountQueryService accountQueryService;

    @Test
    void handleAccountNotFound_returns404WithErrorEnvelope() throws Exception {
        when(accountQueryService.lookup("bad-ref"))
                .thenThrow(new AccountNotFoundException("Account bad-ref not found"));

        mockMvc.perform(get("/accounts/bad-ref"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Account bad-ref not found"))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty())
                .andExpect(jsonPath("$.meta.timestamp").isNotEmpty());
    }

    @Test
    void handleMissingHeader_returns400WithErrorEnvelope() throws Exception {
        // Omitting X-User-Id triggers MissingRequestHeaderException
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty())
                .andExpect(jsonPath("$.error.code").value("MISSING_HEADER"))
                .andExpect(jsonPath("$.error.message").value(containsString("X-User-Id")))
                .andExpect(jsonPath("$.meta.requestId").isNotEmpty());
    }
}

