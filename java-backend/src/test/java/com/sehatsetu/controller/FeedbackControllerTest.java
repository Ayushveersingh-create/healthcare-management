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
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("FeedbackController Integration Tests")
@SuppressWarnings("null")
class FeedbackControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Patient submits feedback -> Doctor reviews updated -> Duplicate blocked")
    void testFeedbackSubmissionAndRetrieval() throws Exception {
        // Setup appointment for feedback
        List<Map<String, Object>> pat = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE role = 'PATIENT' LIMIT 1");
        List<Map<String, Object>> doc = jdbcTemplate.queryForList("SELECT id, clinic_id FROM doctor_profiles LIMIT 1");

        Long patientId = ((Number) pat.get(0).get("id")).longValue();
        String patEmail = (String) pat.get(0).get("email");
        String patToken = JwtUtil.generateToken(patientId, patEmail, "PATIENT");

        Long docProfileId = ((Number) doc.get(0).get("id")).longValue();
        Long clinicId = ((Number) doc.get(0).get("clinic_id")).longValue();

        String refCode = "SS-FB-" + UUID.randomUUID().toString().substring(0, 8);
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status) VALUES (?, ?, ?, ?, '2026-09-01', '14:00', 'IN_CLINIC', 'COMPLETED')",
                refCode, patientId, docProfileId, clinicId);
        Long aptId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        Map<String, Object> feedbackReq = Map.of(
                "appointment_id", aptId,
                "rating", 5,
                "comment", "Dr. was very polite, listened patiently, and explained the prescription thoroughly!"
        );

        // Submit feedback
        mockMvc.perform(post("/api/feedback/submit")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("feedback has been recorded")));

        // Duplicate submission should be blocked
        mockMvc.perform(post("/api/feedback/submit")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("already been submitted")));

        // Fetch doctor reviews
        mockMvc.perform(get("/api/feedback/doctor/{doctor_id}", docProfileId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviews", isA(List.class)))
                .andExpect(jsonPath("$.reviews[0].rating").exists())
                .andExpect(jsonPath("$.reviews[0].patient_name").exists());
    }

    @Test
    @DisplayName("POST /api/feedback/submit - Unauthorized without token")
    void testSubmitFeedbackUnauthorized() throws Exception {
        Map<String, Object> feedbackReq = Map.of("appointment_id", 1, "rating", 5);

        mockMvc.perform(post("/api/feedback/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(feedbackReq)))
                .andExpect(status().isUnauthorized());
    }
}
