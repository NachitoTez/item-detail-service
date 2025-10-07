package com.ignacioramirez.itemDetailService.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ignacioramirez.itemDetailService.controller.ItemController;
import com.ignacioramirez.itemDetailService.dto.items.request.CreateItemRQ;
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

    @Autowired ItemService service;

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
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").value("/items/non-existent-id"));
    }

    @Test
    void whenSkuAlreadyExists_thenReturns409WithProblemDetail() throws Exception {
        var request = new CreateItemRQ(
                "LAPTOP-001",
                "Laptop Dell XPS",
                "High performance laptop",
                "ARS",
                new BigDecimal("1500.00"),
                5,
                "SELLER-1",
                "NEW",
                true,
                List.of("electronics","laptops"),
                Map.of("brand","Dell","model","XPS 13")
        );

        Mockito.when(itemService.create(any(CreateItemRQ.class)))
                .thenThrow(new ConflictException("SKU already exists"));

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("SKU already exists"))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.instance").value("/items"));
    }


    @Test
    void whenCreateItemWithInvalidBody_thenReturns400WithValidationErrors() throws Exception {
        String invalidRequest = """
        {
          "sku": "",
          "title": "Test",
          "description": "Test description",
          "currency": "ARS",
          "amount": -10.00,
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
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", hasItems("sku", "amount")));
    }


    @Test
    void whenUpdateItemWithNullTitle_thenReturns400() throws Exception {
        String invalidRequest = """
            { "title": null, "description": "Valid", "price": 100.00 }
        """;

        mockMvc.perform(put("/items/123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'title')]").exists());
    }

    @Test
    void whenListItemsWithInvalidPageParam_thenReturns400() throws Exception {
        mockMvc.perform(get("/items").param("page", "-1").param("size", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Constraint violation"))
                .andExpect(jsonPath("$.code").value("CONSTRAINT_VIOLATION"))
                .andExpect(jsonPath("$.errors[0].field", containsString("page")));
    }

    @Test
    void whenSendUnbindableJson_thenReturns400() throws Exception {
        String body = """
      {
        "sku": "X",
        "title": "X",
        "description": "X",
        "currency": "ARS",
        "amount": "oops",
        "stock": 1,
        "sellerId": "SELLER-1",
        "condition": "NEW",
        "freeShipping": true,
        "categories": "electronics",
        "attributes": { "brand": "Dell" }
      }
    """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.detail", anyOf(
                        containsString("Cannot deserialize value"),
                        containsString("JSON parse error"),
                        containsString("Mismatched input")
                )));
    }



    @Test
    void whenMissingRequiredParam_thenReturns400() throws Exception {
        mockMvc.perform(post("/items/123/rating"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.title").value("Bad request"));
    }

    @Test
    void whenUseWrongHttpMethod_thenReturns405() throws Exception {
        mockMvc.perform(patch("/items/123"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.title").value("Method Not Allowed"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.detail", containsString("PATCH")));
    }

    @Test
    void whenAccessNonExistentEndpoint_thenReturns404() throws Exception {
        mockMvc.perform(get("/items/123/non-existent-endpoint"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.code").value("NO_HANDLER"))
                .andExpect(jsonPath("$.detail", containsString("Endpoint not found")))
                .andExpect(jsonPath("$.instance").value("/items/123/non-existent-endpoint"));
    }

    @Test
    void whenUnexpectedExceptionOccurs_thenReturns500() throws Exception {
        Mockito.when(itemService.getById(anyString()))
                .thenThrow(new RuntimeException("Database connection failed"));

        mockMvc.perform(get("/items/123"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.title").value("Unexpected error"))
                .andExpect(jsonPath("$.code").value("UNEXPECTED_ERROR"))
                .andExpect(jsonPath("$.detail").value("Please try again later."));
    }



    //helpers
    private CreateItemRQ validCreateItemRQ() {
        return new CreateItemRQ(
                "SKU-1","Title","Desc","ARS",new BigDecimal("1.00"),
                1,"SELLER-1","NEW",false,List.of("cat"),Map.of("k","v")
        );
    }


}
