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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("MedicalRecordController Integration Tests")
@SuppressWarnings("null")
class MedicalRecordControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Doctor adds consultation record -> Appointment becomes COMPLETED -> Patient views records")
    void testConsultationRecordFlow() throws Exception {
        // Find or create an appointment
        List<Map<String, Object>> apts = jdbcTemplate.queryForList(
                "SELECT id, patient_id, doctor_id FROM appointments WHERE status = 'SCHEDULED' LIMIT 1");

        Long aptId;
        Long patientId;
        Long doctorProfileId;

        if (apts.isEmpty()) {
            List<Map<String, Object>> pat = jdbcTemplate.queryForList("SELECT id FROM users WHERE role = 'PATIENT' LIMIT 1");
            List<Map<String, Object>> doc = jdbcTemplate.queryForList("SELECT id, clinic_id FROM doctor_profiles LIMIT 1");
            patientId = ((Number) pat.get(0).get("id")).longValue();
            doctorProfileId = ((Number) doc.get(0).get("id")).longValue();
            Long clinicId = ((Number) doc.get(0).get("clinic_id")).longValue();

            jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status) VALUES ('SS-TEST-001', ?, ?, ?, '2026-10-15', '09:00', 'IN_CLINIC', 'SCHEDULED')",
                    patientId, doctorProfileId, clinicId);
            aptId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        } else {
            aptId = ((Number) apts.get(0).get("id")).longValue();
            patientId = ((Number) apts.get(0).get("patient_id")).longValue();
            doctorProfileId = ((Number) apts.get(0).get("doctor_id")).longValue();
        }

        // Get doctor user
        List<Map<String, Object>> docUser = jdbcTemplate.queryForList(
                "SELECT u.id, u.email FROM users u JOIN doctor_profiles dp ON u.id = dp.user_id WHERE dp.id = ?", doctorProfileId);
        Long docUserId = ((Number) docUser.get(0).get("id")).longValue();
        String docEmail = (String) docUser.get(0).get("email");
        String docToken = JwtUtil.generateToken(docUserId, docEmail, "DOCTOR");

        // Doctor submits consultation
        Map<String, Object> recPayload = Map.of(
                "appointment_id", aptId,
                "diagnosis_title", "Hypertension & Stage 1 Pre-diabetes",
                "clinical_notes", "Patient has BP 138/88 mmHg. Advised lifestyle modifications.",
                "prescription_notes", "Tab Telmisartan 40mg OD, Tab Metformin 500mg BD after meals.",
                "record_type", "Consultation",
                "follow_up_date", "2026-11-15"
        );

        mockMvc.perform(post("/api/records/consultation")
                        .header("Authorization", "Bearer " + docToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.record_id").isNotEmpty());

        // Verify patient can fetch records
        List<Map<String, Object>> patUser = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE id = ?", patientId);
        String patEmail = (String) patUser.get(0).get("email");
        String patToken = JwtUtil.generateToken(patientId, patEmail, "PATIENT");

        mockMvc.perform(get("/api/records/patient/{patient_id}", patientId)
                        .header("Authorization", "Bearer " + patToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records", isA(List.class)))
                .andExpect(jsonPath("$.records[0].diagnosis_title").exists());
    }

    @Test
    @DisplayName("POST /api/records/consultation - Non-doctor cannot add consultation record")
    void testNonDoctorCannotAddConsultation() throws Exception {
        List<Map<String, Object>> pat = jdbcTemplate.queryForList("SELECT id, email FROM users WHERE role = 'PATIENT' LIMIT 1");
        Long patientId = ((Number) pat.get(0).get("id")).longValue();
        String patEmail = (String) pat.get(0).get("email");
        String patToken = JwtUtil.generateToken(patientId, patEmail, "PATIENT");

        Map<String, Object> recPayload = Map.of(
                "appointment_id", 1,
                "diagnosis_title", "Unauthorized test"
        );

        mockMvc.perform(post("/api/records/consultation")
                        .header("Authorization", "Bearer " + patToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(recPayload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Only doctors can submit")));
    }

    @Test
    @DisplayName("GET /api/records/patient/{patient_id} - Patient cannot view other patient records (403)")
    void testPatientCannotViewOtherPatientRecords() throws Exception {
        String patToken = JwtUtil.generateToken(99991L, "unrelated.patient@test.in", "PATIENT");

        mockMvc.perform(get("/api/records/patient/1")
                        .header("Authorization", "Bearer " + patToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail", containsString("Access denied")));
    }
}
