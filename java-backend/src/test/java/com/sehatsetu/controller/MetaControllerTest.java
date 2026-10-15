package com.sehatsetu.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MetaController Integration Tests")
@SuppressWarnings("null")
class MetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/specialities - Should return list of medical specialities")
    void testGetSpecialities() throws Exception {
        mockMvc.perform(get("/api/specialities")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialities", isA(java.util.List.class)))
                .andExpect(jsonPath("$.specialities.length()", greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$.specialities[0].name").exists())
                .andExpect(jsonPath("$.specialities[0].icon").exists());
    }

    @Test
    @DisplayName("GET /api/clinics - Should return list of clinic locations")
    void testGetClinics() throws Exception {
        mockMvc.perform(get("/api/clinics")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clinics", isA(java.util.List.class)))
                .andExpect(jsonPath("$.clinics.length()", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.clinics[0].name").exists())
                .andExpect(jsonPath("$.clinics[0].city").exists())
                .andExpect(jsonPath("$.clinics[0].address").exists());
    }
}
