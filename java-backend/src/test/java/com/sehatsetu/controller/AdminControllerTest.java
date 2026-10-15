package com.sehatsetu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AdminController Integration Tests")
@SuppressWarnings("null")
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String getAdminToken() {
        List<Map<String, Object>> admins = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE role = 'ADMIN' LIMIT 1");
        if (admins.isEmpty()) {
            return JwtUtil.generateToken(1L, "admin@sehatsetu.in", "ADMIN");
        }
        Long id = ((Number) admins.get(0).get("id")).longValue();
        String email = (String) admins.get(0).get("email");
        return JwtUtil.generateToken(id, email, "ADMIN");
    }

    private String getPatientToken() {
        List<Map<String, Object>> pats = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE role = 'PATIENT' LIMIT 1");
        Long id = ((Number) pats.get(0).get("id")).longValue();
        String email = (String) pats.get(0).get("email");
        return JwtUtil.generateToken(id, email, "PATIENT");
    }

    @Test
    @DisplayName("GET /api/admin/kpis - Admin access succeeds; Non-admin access returns 403")
    void testGetKPIs() throws Exception {
        String adminToken = getAdminToken();
        String patientToken = getPatientToken();

        // Admin access
        mockMvc.perform(get("/api/admin/kpis")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total_users", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.total_doctors", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.total_patients", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.total_appointments").exists());

        // Patient access rejected
        mockMvc.perform(get("/api/admin/kpis")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Admin privileges required")));
    }

    @Test
    @DisplayName("GET /api/admin/analytics - Returns analytics chart metrics for Admin")
    void testGetAnalytics() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.specialities", isA(List.class)))
                .andExpect(jsonPath("$.cities", isA(List.class)))
                .andExpect(jsonPath("$.modes", isA(List.class)))
                .andExpect(jsonPath("$.daily_trends", isA(List.class)));
    }

    @Test
    @DisplayName("GET /api/admin/users & PUT /api/admin/users/{id}/status - User management")
    void testUserManagement() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.users", isA(List.class)))
                .andExpect(jsonPath("$.users[0].email").exists());

        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id FROM users WHERE role = 'PATIENT' LIMIT 1");
        Long targetUserId = ((Number) users.get(0).get("id")).longValue();

        Map<String, String> statusBody = Map.of("status", "ACTIVE");
        mockMvc.perform(put("/api/admin/users/{id}/status", targetUserId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(statusBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("User status updated")));
    }

    @Test
    @DisplayName("GET /api/admin/doctors & PUT /api/admin/doctors/{id}/verification - Doctor verification")
    void testDoctorVerificationManagement() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/admin/doctors")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.doctors", isA(List.class)));

        List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles LIMIT 1");
        Long docId = ((Number) docs.get(0).get("id")).longValue();

        Map<String, String> verifBody = Map.of("verification_status", "VERIFIED");
        mockMvc.perform(put("/api/admin/doctors/{id}/verification", docId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Doctor verification status updated")));
    }

    @Test
    @DisplayName("GET /api/admin/appointments - Admin views all platform appointments")
    void testGetAllAppointments() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/admin/appointments")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments", isA(List.class)));
    }

    @Test
    @DisplayName("GET /api/admin/settings - Public system configuration parameters")
    void testGetSettings() throws Exception {
        mockMvc.perform(get("/api/admin/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settings", isA(Map.class)));
    }

    @Test
    @DisplayName("GET /api/admin/audit-trail - Admin audit logs")
    void testGetAuditTrail() throws Exception {
        String adminToken = getAdminToken();

        mockMvc.perform(get("/api/admin/audit-trail")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logs", isA(List.class)));
    }
}
