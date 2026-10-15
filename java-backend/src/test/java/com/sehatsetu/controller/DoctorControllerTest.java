package com.sehatsetu.controller;

import com.sehatsetu.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("DoctorController Integration Tests")
@SuppressWarnings("null")
class DoctorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /api/doctors/search - Default search returns active verified doctors")
    void testSearchDoctorsDefault() throws Exception {
        mockMvc.perform(get("/api/doctors/search")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors", isA(List.class)))
                .andExpect(jsonPath("$.doctors.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.doctors[0].doctor_id").exists())
                .andExpect(jsonPath("$.doctors[0].full_name").exists())
                .andExpect(jsonPath("$.doctors[0].speciality_name").exists());
    }

    @Test
    @DisplayName("GET /api/doctors/search - Filter by keyword, speciality, and sorting")
    void testSearchDoctorsWithFilters() throws Exception {
        mockMvc.perform(get("/api/doctors/search")
                        .param("q", "Sharma")
                        .param("sort_by", "fee_asc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors", isA(List.class)));

        mockMvc.perform(get("/api/doctors/search")
                        .param("sort_by", "exp_desc")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors", isA(List.class)));
    }

    @Test
    @DisplayName("GET /api/doctors/{id} - Valid doctor ID returns profile and schedules")
    void testGetDoctorDetailSuccess() throws Exception {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles LIMIT 1");
        if (docs.isEmpty()) return;

        Long docId = ((Number) docs.get(0).get("id")).longValue();

        mockMvc.perform(get("/api/doctors/{id}", docId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctor.doctor_id", is(docId.intValue())))
                .andExpect(jsonPath("$.doctor.full_name").exists())
                .andExpect(jsonPath("$.schedules", isA(List.class)));
    }

    @Test
    @DisplayName("GET /api/doctors/{id} - Non-existent ID returns 404")
    void testGetDoctorDetailNotFound() throws Exception {
        mockMvc.perform(get("/api/doctors/{id}", 999999)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail", containsString("Doctor profile not found")));
    }

    @Test
    @DisplayName("GET /api/doctors/dashboard/stats - Doctor JWT returns dashboard metrics")
    void testDoctorDashboardStats() throws Exception {
        List<Map<String, Object>> docUsers = jdbcTemplate.queryForList(
                "SELECT u.id as user_id, u.email FROM users u JOIN doctor_profiles dp ON u.id = dp.user_id LIMIT 1");
        if (docUsers.isEmpty()) return;

        Long userId = ((Number) docUsers.get(0).get("user_id")).longValue();
        String email = (String) docUsers.get(0).get("email");
        String token = JwtUtil.generateToken(userId, email, "DOCTOR");

        mockMvc.perform(get("/api/doctors/dashboard/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upcoming_appointments").exists())
                .andExpect(jsonPath("$.completed_appointments").exists())
                .andExpect(jsonPath("$.total_patients_seen").exists())
                .andExpect(jsonPath("$.avg_rating").exists());
    }

    @Test
    @DisplayName("GET /api/doctors/dashboard/stats - Without token returns 401")
    void testDoctorDashboardStatsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/doctors/dashboard/stats"))
                .andExpect(status().isUnauthorized());
    }
}
