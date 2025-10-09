package com.ignacioramirez.itemDetailService.controller;

import com.ignacioramirez.itemDetailService.dto.items.response.ItemRS;
import com.ignacioramirez.itemDetailService.service.ItemService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = ItemController.class)
class ItemControllerTest {

    @Autowired MockMvc mockMvc;

    @TestConfiguration
    static class TestBeans {
        @Bean @Primary
        ItemService itemService() {
            return Mockito.mock(ItemService.class);
        }
    }

    @Autowired ItemService service;

    @Test
    @DisplayName("POST /items crea: 201 y Location /items/{id}")
    void create_ok_setsLocation() throws Exception {
        ItemRS rs = Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS);
        when(rs.id()).thenReturn("ID123");
        when(service.create(any())).thenReturn(rs);

        String body = """
      {
        "title":"Lightsaber",
        "description":"Blue saber",
        "price": {
          "currency":"ARS",
          "amount": 1000
        },
        "stock": 5,
        "sellerId":"OBI-WAN",
        "condition":"NEW",
        "freeShipping": true,
        "categories": ["Jedi","Weapons"],
        "attributes": {"color":"blue"}
      }
    """;

        mockMvc.perform(post("/items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/items/ID123"))
                .andExpect(content().contentType("application/json"));

        verify(service).create(any());
        verifyNoMoreInteractions(service);
    }

    // ---------- GET /items/{id} ----------
    @Test
    @DisplayName("GET /items/{id} delega en service.getById: 200")
    void getById_ok_delegates() throws Exception {
        when(service.getById("ABC")).thenReturn(Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS));

        mockMvc.perform(get("/items/{id}", "ABC"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(service).getById("ABC");
        verifyNoMoreInteractions(service);
    }

    // ---------- GET /items (paginado) ----------
    @Test
    @DisplayName("GET /items con page/size válidos: 200 y delega con los params")
    void list_ok_withPaging_delegates() throws Exception {
        when(service.list(2, 50)).thenReturn(List.of());

        mockMvc.perform(get("/items").param("page", "2").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("[]"));

        verify(service).list(2, 50);
        verifyNoMoreInteractions(service);
    }

    @Test
    @DisplayName("GET /items con defaults (0,20)")
    void list_defaults() throws Exception {
        when(service.list(0, 20)).thenReturn(List.of());

        mockMvc.perform(get("/items"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"))
                .andExpect(content().json("[]"));

        verify(service).list(0, 20);
        verifyNoMoreInteractions(service);
    }


    // ---------- PUT /items/{id} ----------
    @Test
    @DisplayName("PUT /items/{id} delega en service.update y retorna 200 (body válido)")
    void update_ok_delegates() throws Exception {
        when(service.update(eq("ID1"), any())).thenReturn(Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS));

        String body = """
          {
            "title":"Lightsaber v2",
            "description":"Now greener",
            "amount": 1500,
            "stock": 7
          }
        """;

        mockMvc.perform(put("/items/{id}", "ID1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(service).update(eq("ID1"), any());
        verifyNoMoreInteractions(service);
    }

    // ---------- DELETE /items/{id} ----------
    @Test
    @DisplayName("DELETE /items/{id} delega en service.delete: 204")
    void delete_ok_delegates() throws Exception {
        doNothing().when(service).delete("ID1");

        mockMvc.perform(delete("/items/{id}", "ID1"))
                .andExpect(status().isNoContent());

        verify(service).delete("ID1");
        verifyNoMoreInteractions(service);
    }

    // ---------- POST /items/{id}/rating ----------
    @Test
    @DisplayName("POST /items/{id}/rating con stars válido: 200 y delega")
    void rate_ok_delegates() throws Exception {
        when(service.rate("ID1", 5)).thenReturn(Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS));

        mockMvc.perform(post("/items/{id}/rating", "ID1").param("stars", "5"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(service).rate("ID1", 5);
        verifyNoMoreInteractions(service);
    }

    // ---------- POST /items/{id}/discount ----------
    @Test
    @DisplayName("POST /items/{id}/discount con body válido: 200 y delega")
    void applyDiscount_ok_delegates() throws Exception {
        when(service.applyDiscount(eq("ID1"), any())).thenReturn(Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS));

        // Ajustá este JSON si tu ApplyDiscountRQ usa otros campos/nombres
        String body = """
          { "type":"PERCENT", "value": 10 }
        """;

        mockMvc.perform(post("/items/{id}/discount", "ID1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(service).applyDiscount(eq("ID1"), any());
        verifyNoMoreInteractions(service);
    }

    // ---------- DELETE /items/{id}/discount ----------
    @Test
    @DisplayName("DELETE /items/{id}/discount: 200 y delega")
    void clearDiscount_ok_delegates() throws Exception {
        when(service.clearDiscount("ID1")).thenReturn(Mockito.mock(ItemRS.class, Answers.RETURNS_DEEP_STUBS));

        mockMvc.perform(delete("/items/{id}/discount", "ID1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/json"));

        verify(service).clearDiscount("ID1");
        verifyNoMoreInteractions(service);
    }
}
