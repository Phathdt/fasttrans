package com.fasttrans.account.infrastructure.web;

import com.fasttrans.account.application.services.AccountQueryService;
import com.fasttrans.account.domain.exception.AccountNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
class GlobalExceptionHandlerTest {

    @Autowired MockMvc mockMvc;
    @MockBean AccountQueryService accountQueryService;

    @Test
    void handleAccountNotFound_returns404WithProblemDetail() throws Exception {
        when(accountQueryService.lookup("bad-ref"))
                .thenThrow(new AccountNotFoundException("Account bad-ref not found"));

        mockMvc.perform(get("/accounts/bad-ref"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account bad-ref not found"));
    }

    @Test
    void handleMissingHeader_returns400WithProblemDetail() throws Exception {
        // Omitting X-User-Id triggers MissingRequestHeaderException
        mockMvc.perform(get("/accounts"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("X-User-Id")));
    }
}
