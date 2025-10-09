package com.ignacioramirez.itemDetailService.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignacioramirez.itemDetailService.controller.ItemController;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
import com.ignacioramirez.itemDetailService.dto.items.request.PriceRQ;
import com.ignacioramirez.itemDetailService.exceptions.ConflictException;
import com.ignacioramirez.itemDetailService.exceptions.GlobalExceptionHandler;
import com.ignacioramirez.itemDetailService.exceptions.NotFoundException;
import com.ignacioramirez.itemDetailService.service.ItemService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ItemController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "spring.mvc.problemdetails.enabled=false",
        "spring.web.resources.add-mappings=false"
})
class GlobalExceptionHandlerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Qualifier("itemService")
    @Autowired
    private ItemService itemService;

    @TestConfiguration
    static class TestBeans {
        @Bean @Primary
        ItemService itemService() {
            return Mockito.mock(ItemService.class);
        }
    }

    // ===================== API Exceptions =====================

    @Test
    void whenItemNotFound_thenReturns404WithProblemDetail() throws Exception {
        Mockito.when(itemService.getById("non-existent-id"))
                .thenThrow(new NotFoundException("Item non-existent-id not found"));

        mockMvc.perform(get("/items/non-existent-id"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.detail").value("Item non-existent-id not found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    @Test
    void whenTitleAlreadyExists_thenReturns409WithProblemDetail() throws Exception {
        var request = new CreateItemRQ(
                "Laptop Dell XPS",
                "High performance laptop",
                new PriceRQ("ARS", new BigDecimal("1500.00")),
                5,
                "SELLER-1",
                "NEW",
                true,
                List.of("electronics", "laptops"),
                Map.of("brand", "Dell", "model", "XPS 13")
        );

        Mockito.when(itemService.create(any(CreateItemRQ.class)))
                .thenThrow(new ConflictException("Title already exists"));

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Title already exists"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.type").doesNotExist());
    }

    // ===================== Validation Errors =====================

    @Test
    void whenCreateItemWithInvalidBody_thenReturns400WithValidationErrors() throws Exception {
        String invalidRequest = """
        {
          "title": "Test",
          "description": "Test description",
          "price": {
            "currency": "ARS",
            "amount": -10.00
          },
          "stock": 0,
          "sellerId": "SELLER-1",
          "condition": "NEW",
          "freeShipping": true
        }
        """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Validation Failed"))
                .andExpect(jsonPath("$.detail").value("One or more fields have invalid values"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[*].field", hasItems("price.amount")))
                .andExpect(jsonPath("$.errors[*].message").exists())
                .andExpect(jsonPath("$.errors[*].rejectedValue").exists());
    }

    @Test
    void whenListItemsWithInvalidPageParam_thenReturns400() throws Exception {
        mockMvc.perform(get("/items")
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Request Parameters"))
                .andExpect(jsonPath("$.detail").value("One or more request parameters are invalid"))
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors[0].parameter", containsString("page")))
                .andExpect(jsonPath("$.errors[0].message").exists())
                .andExpect(jsonPath("$.errors[0].rejectedValue").exists());
    }

    // ===================== JSON Parsing Errors =====================

    @Test
    void whenSendInvalidJsonFormat_thenReturns400WithDetailedError() throws Exception {
        String body = """
        {
          "title": "X",
          "description": "X",
          "price": {
            "currency": "ARS",
            "amount": "not-a-number"
          },
          "stock": 1,
          "sellerId": "SELLER-1",
          "condition": "NEW",
          "freeShipping": true,
          "categories": ["electronics"],
          "attributes": { "brand": "Dell" }
        }
        """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Field Format"))
                .andExpect(jsonPath("$.code").value("INVALID_FORMAT"))
                .andExpect(jsonPath("$.field").value("price.amount"))
                .andExpect(jsonPath("$.expectedType").exists())
                .andExpect(jsonPath("$.providedValue").value("not-a-number"))
                .andExpect(jsonPath("$.detail", containsString("price.amount")));
    }


    @Test
    void whenSendEmptyBody_thenReturns400() throws Exception {
        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Malformed JSON"))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"))
                .andExpect(jsonPath("$.detail", containsString("not valid JSON or is empty")));
    }

    @Test
    void whenSendMalformedJson_thenReturns400() throws Exception {
        String malformedJson = """
        {
          "title": "Test"
          "description": "Missing comma"
        }
        """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Malformed JSON"))
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    // ===================== Parameter Errors =====================

    @Test
    void whenMissingRequiredParam_thenReturns400WithParamInfo() throws Exception {
        mockMvc.perform(post("/items/123/rating"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Missing Required Parameter"))
                .andExpect(jsonPath("$.code").value("MISSING_PARAMETER"))
                .andExpect(jsonPath("$.parameter").value("stars"))
                .andExpect(jsonPath("$.expectedType").exists())
                .andExpect(jsonPath("$.detail", containsString("stars")));
    }

    @Test
    void whenSendWrongParamType_thenReturns400WithTypeInfo() throws Exception {
        mockMvc.perform(post("/items/123/rating")
                        .param("stars", "not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid Parameter Type"))
                .andExpect(jsonPath("$.code").value("TYPE_MISMATCH"))
                .andExpect(jsonPath("$.parameter").value("stars"))
                .andExpect(jsonPath("$.expectedType").exists())
                .andExpect(jsonPath("$.providedValue").value("not-a-number"))
                .andExpect(jsonPath("$.detail", containsString("stars")));
    }

    // ===================== HTTP Method & Endpoint Errors =====================

    @Test
    void whenUseWrongHttpMethod_thenReturns405() throws Exception {
        mockMvc.perform(patch("/items/123"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail", containsString("PATCH")));
    }

    @Test
    void whenAccessNonExistentEndpoint_thenReturns404() throws Exception {
        mockMvc.perform(get("/items/123/non-existent-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Endpoint Not Found"))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail", containsString("does not exist")))
                .andExpect(jsonPath("$.detail", containsString("GET")))
                .andExpect(jsonPath("$.detail", containsString("/items/123/non-existent-endpoint")));
    }

    // ===================== Unexpected Errors =====================

    @Test
    void whenUnexpectedExceptionOccurs_thenReturns500() throws Exception {
        Mockito.when(itemService.getById(anyString()))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/items/123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred. Please try again later."))
                .andExpect(jsonPath("$.detail", not(containsString("Database"))));
    }

    // ===================== Edge Cases =====================

    @Test
    void whenMissingFieldInJson_thenReturns400WithMissingFieldError() throws Exception {
        String bodyMissingTitle = """
        {
          "description": "Test description",
          "price": {
            "currency": "ARS",
            "amount": 100.00
          },
          "stock": 5,
          "sellerId": "SELLER-1",
          "condition": "NEW",
          "freeShipping": true,
          "categories": ["electronics"],
          "attributes": {}
        }
        """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyMissingTitle))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code", anyOf(is("MISSING_FIELD"), is("VALIDATION_ERROR"))))
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void whenMultipleValidationErrors_thenReturnsAllErrors() throws Exception {
        String invalidRequest = """
        {
          "title": "",
          "description": "",
          "price": {
            "currency": "INVALID",
            "amount": -100
          },
          "stock": -5,
          "sellerId": "",
          "condition": "INVALID_CONDITION",
          "freeShipping": true
        }
        """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(greaterThan(1)))
                .andExpect(jsonPath("$.errors[*].field").exists())
                .andExpect(jsonPath("$.errors[*].message").exists());
    }

    // ===================== Helper Methods =====================

    private CreateItemRQ validCreateItemRQ() {
        return new CreateItemRQ(
                "Title",
                "Desc",
                new PriceRQ("ARS", new BigDecimal("1.00")),
                1,
                "SELLER-1",
                "NEW",
                false,
                List.of("cat"),
                Map.of("k", "v")
        );
    }
}