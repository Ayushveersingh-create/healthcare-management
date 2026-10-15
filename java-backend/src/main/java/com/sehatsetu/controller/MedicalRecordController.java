package com.sehatsetu.controller;

import com.sehatsetu.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/records", "/api/medical-records"})
@CrossOrigin(origins = "*")
public class MedicalRecordController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping({"", "/consultation"})
    public ResponseEntity<?> addConsultationRecord(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null || !"DOCTOR".equalsIgnoreCase((String) claims.get("role"))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Only doctors can submit consultation records."));
        }

        Long aptId = req.get("appointment_id") != null ? ((Number) req.get("appointment_id")).longValue() : null;
        String diagnosisTitle = (String) req.get("diagnosis_title");
        String clinicalNotes = (String) req.get("clinical_notes");
        String prescriptions = req.get("prescription_notes") != null ? (String) req.get("prescription_notes") : (String) req.get("prescriptions");
        String recordType = req.get("record_type") != null ? (String) req.get("record_type") : "Consultation";
        String followUpDate = (String) req.get("follow_up_date");

        if (aptId == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "appointment_id is required."));
        }

        List<Map<String, Object>> apts = jdbcTemplate.queryForList("SELECT patient_id, doctor_id FROM appointments WHERE id = ?", aptId);
        if (apts.isEmpty()) return ResponseEntity.badRequest().body(Map.of("detail", "Appointment not found."));

        Map<String, Object> apt = apts.get(0);
        Long patientId = req.get("patient_id") != null ? ((Number) req.get("patient_id")).longValue() : ((Number) apt.get("patient_id")).longValue();
        Long doctorId = ((Number) apt.get("doctor_id")).longValue();

        jdbcTemplate.update("""
            INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """, aptId, patientId, doctorId, diagnosisTitle, clinicalNotes, prescriptions, recordType, followUpDate);

        Long recordId = jdbcTemplate.queryForObject("SELECT id FROM medical_records WHERE appointment_id = ?", Long.class, aptId);

        // Auto mark appointment as COMPLETED
        jdbcTemplate.update("UPDATE appointments SET status = 'COMPLETED', updated_at = CURRENT_TIMESTAMP WHERE id = ?", aptId);

        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, target_url) VALUES (?, ?, ?, 'MEDICAL_RECORD', '/patient/records')",
                patientId, "New Consultation Record Added", "Dr. added clinical notes and prescription for your visit (" + diagnosisTitle + ").");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Consultation record saved successfully.",
                "record_id", recordId,
                "id", recordId
        ));
    }

    @GetMapping({"", "/patient/{patient_id}"})
    public ResponseEntity<?> getPatientRecords(
            @PathVariable(value = "patient_id", required = false) Long pathPatientId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long authUserId = claims.get("user_id", Long.class);
        String role = (String) claims.get("role");

        Long patientId = pathPatientId != null ? pathPatientId : authUserId;

        if ("PATIENT".equalsIgnoreCase(role) && !authUserId.equals(patientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Access denied. You can only view your own medical records."));
        }

        if ("DOCTOR".equalsIgnoreCase(role)) {
            List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", authUserId);
            if (!docs.isEmpty()) {
                Long docId = ((Number) docs.get(0).get("id")).longValue();
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND patient_id = ?", Integer.class, docId, patientId);
                if (count == null || count == 0) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Unauthorized medical record access blocked. Doctor has no appointments with this patient."));
                }
            }
        }

        List<Map<String, Object>> rawRecords = jdbcTemplate.queryForList("""
            SELECT mr.*, u_doc.full_name as doctor_name, s.name as speciality_name, c.name as clinic_name, a.appointment_date, a.reference_code
            FROM medical_records mr
            JOIN doctor_profiles dp ON mr.doctor_id = dp.id
            JOIN users u_doc ON dp.user_id = u_doc.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN appointments a ON mr.appointment_id = a.id
            JOIN clinics c ON a.clinic_id = c.id
            WHERE mr.patient_id = ?
            ORDER BY mr.created_at DESC
        """, patientId);

        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> r : rawRecords) {
            Map<String, Object> map = new HashMap<>(r);
            map.put("speciality", r.get("speciality_name"));
            map.put("hospital_clinic", r.get("clinic_name"));
            map.put("record_date", r.get("appointment_date") != null ? r.get("appointment_date") : r.get("created_at"));
            map.put("record_date_formatted", formatDateIST((String) map.get("record_date")));
            map.put("prescription_notes", r.get("prescriptions"));
            if (r.get("follow_up_date") != null) {
                map.put("follow_up_date_formatted", formatDateIST((String) r.get("follow_up_date")));
            }
            records.add(map);
        }

        return ResponseEntity.ok(Map.of("records", records, "count", records.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getSingleRecord(
            @PathVariable("id") Long recordId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long authUserId = claims.get("user_id", Long.class);
        String role = (String) claims.get("role");

        List<Map<String, Object>> rawRecords = jdbcTemplate.queryForList("""
            SELECT mr.*, u_doc.full_name as doctor_name, s.name as speciality_name, c.name as clinic_name, a.appointment_date, a.reference_code,
                   u_pat.full_name as patient_name, u_pat.gender as patient_gender, u_pat.blood_group as patient_blood_group
            FROM medical_records mr
            JOIN doctor_profiles dp ON mr.doctor_id = dp.id
            JOIN users u_doc ON dp.user_id = u_doc.id
            JOIN users u_pat ON mr.patient_id = u_pat.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN appointments a ON mr.appointment_id = a.id
            JOIN clinics c ON a.clinic_id = c.id
            WHERE mr.id = ?
        """, recordId);

        if (rawRecords.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Medical record not found."));

        Map<String, Object> record = new HashMap<>(rawRecords.get(0));
        Long recordPatientId = ((Number) record.get("patient_id")).longValue();

        if ("PATIENT".equalsIgnoreCase(role) && !authUserId.equals(recordPatientId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Unauthorized medical record access blocked."));
        }

        if ("DOCTOR".equalsIgnoreCase(role)) {
            List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", authUserId);
            if (!docs.isEmpty()) {
                Long docId = ((Number) docs.get(0).get("id")).longValue();
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND patient_id = ?", Integer.class, docId, recordPatientId);
                if (count == null || count == 0) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Unauthorized medical record access blocked. Doctor has no appointments with this patient."));
                }
            }
        }

        record.put("speciality", record.get("speciality_name"));
        record.put("hospital_clinic", record.get("clinic_name"));
        record.put("record_date", record.get("appointment_date") != null ? record.get("appointment_date") : record.get("created_at"));
        record.put("record_date_formatted", formatDateIST((String) record.get("record_date")));
        record.put("prescription_notes", record.get("prescriptions"));

        return ResponseEntity.ok(Map.of("record", record));
    }

    private static String formatDateIST(String dateStr) {
        if (dateStr == null) return "-";
        try {
            String datePart = dateStr.split(" ")[0];
            String[] p = datePart.split("-");
            if (p.length == 3) return p[2] + "/" + p[1] + "/" + p[0];
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }
}

