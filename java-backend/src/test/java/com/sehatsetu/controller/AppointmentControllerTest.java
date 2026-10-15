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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AppointmentController Integration Tests")
@SuppressWarnings("null")
class AppointmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET /api/appointments/slots - Return slots for doctor on valid date")
    void testGetAvailableSlots() throws Exception {
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles LIMIT 1");
        if (docs.isEmpty()) return;

        Long docId = ((Number) docs.get(0).get("id")).longValue();
        String testDate = LocalDate.now().plusDays(3).toString();

        mockMvc.perform(get("/api/appointments/slots")
                        .param("doctor_id", docId.toString())
                        .param("date", testDate)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date", is(testDate)))
                .andExpect(jsonPath("$.available_slots").exists());
    }

    @Test
    @DisplayName("GET /api/appointments/slots - Invalid date format returns 400")
    void testGetAvailableSlotsInvalidDate() throws Exception {
        mockMvc.perform(get("/api/appointments/slots")
                        .param("doctor_id", "1")
                        .param("date", "invalid-date-format")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Invalid date format")));
    }

    @Test
    @DisplayName("Complete Appointment Lifecycle: Book -> Reschedule -> My Appointments -> Cancel")
    void testAppointmentLifecycle() throws Exception {
        jdbcTemplate.update("DELETE FROM medical_records WHERE appointment_id IN (SELECT id FROM appointments WHERE reason_for_visit = 'Routine health screening')");
        jdbcTemplate.update("DELETE FROM feedback WHERE appointment_id IN (SELECT id FROM appointments WHERE reason_for_visit = 'Routine health screening')");
        jdbcTemplate.update("DELETE FROM appointments WHERE reason_for_visit = 'Routine health screening'");
        // 1. Get patient user
        List<Map<String, Object>> patUsers = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE role = 'PATIENT' LIMIT 1");
        Long patientId = ((Number) patUsers.get(0).get("id")).longValue();
        String patEmail = (String) patUsers.get(0).get("email");
        String patToken = JwtUtil.generateToken(patientId, patEmail, "PATIENT");

        // 2. Get doctor profile
        List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles LIMIT 1");
        Long doctorId = ((Number) docs.get(0).get("id")).longValue();

        String bookDate = LocalDate.now().plusDays(10).toString();
        String bookTime = "10:30";

        // Book appointment
        Map<String, Object> bookReq = Map.of(
                "doctor_id", doctorId,
                "appointment_date", bookDate,
                "appointment_time", bookTime,
                "mode", "IN_CLINIC",
                "reason_for_visit", "Routine health screening"
        );

        MvcResult bookResult = mockMvc.perform(post("/api/appointments/book")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.appointment_id").isNotEmpty())
                .andExpect(jsonPath("$.reference_code", startsWith("SS-APT-")))
                .andReturn();

        String bookRespJson = bookResult.getResponse().getContentAsString();
        Map<?, ?> bookResp = objectMapper.readValue(bookRespJson, Map.class);
        Number aptIdNum = (Number) bookResp.get("appointment_id");
        Long aptId = aptIdNum.longValue();

        // Test Double booking prevention
        mockMvc.perform(post("/api/appointments/book")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("already been booked")));

        // Reschedule appointment
        String newDate = LocalDate.now().plusDays(11).toString();
        String newTime = "11:00";
        Map<String, Object> reschedReq = Map.of(
                "appointment_id", aptId,
                "new_date", newDate,
                "new_time", newTime
        );

        mockMvc.perform(post("/api/appointments/reschedule")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reschedReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("rescheduled successfully")));

        // Verify in /api/appointments/my
        mockMvc.perform(get("/api/appointments/my")
                        .header("Authorization", "Bearer " + patToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)))
                .andExpect(jsonPath("$[?(@.id == " + aptId + ")].appointment_date", contains(newDate)))
                .andExpect(jsonPath("$[?(@.id == " + aptId + ")].appointment_time", contains(newTime)));

        // Cancel appointment
        Map<String, Object> cancelReq = Map.of(
                "appointment_id", aptId,
                "reason", "Change of plans"
        );

        mockMvc.perform(post("/api/appointments/cancel")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("cancelled successfully")));
    }

    @Test
    @DisplayName("GET /api/appointments/my - Doctor view returns doctor appointments")
    void testGetDoctorAppointments() throws Exception {
        List<Map<String, Object>> docUsers = jdbcTemplate.queryForList(
                "SELECT u.id as user_id, u.email FROM users u JOIN doctor_profiles dp ON u.id = dp.user_id LIMIT 1");
        if (docUsers.isEmpty()) return;

        Long userId = ((Number) docUsers.get(0).get("user_id")).longValue();
        String email = (String) docUsers.get(0).get("email");
        String docToken = JwtUtil.generateToken(userId, email, "DOCTOR");

        mockMvc.perform(get("/api/appointments/my")
                        .header("Authorization", "Bearer " + docToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(List.class)));
    }

    @Test
    @DisplayName("POST /api/appointments/book - Unauthenticated request returns 401")
    void testBookUnauthenticated() throws Exception {
        Map<String, Object> req = Map.of(
                "doctor_id", 1,
                "appointment_date", "2026-10-01",
                "appointment_time", "10:00"
        );

        mockMvc.perform(post("/api/appointments/book")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized());
    }
}
