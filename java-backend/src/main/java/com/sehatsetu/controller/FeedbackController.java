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
@RequestMapping("/api/feedback")
@CrossOrigin(origins = "*")
public class FeedbackController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping({"", "/submit"})
    public ResponseEntity<?> submitFeedback(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long patientId = claims.get("user_id", Long.class);
        Long aptId = ((Number) req.get("appointment_id")).longValue();
        int rating = ((Number) req.get("rating")).intValue();
        String comment = (String) req.get("comment");

        List<Map<String, Object>> apts = jdbcTemplate.queryForList("SELECT doctor_id FROM appointments WHERE id = ? AND patient_id = ?", aptId, patientId);
        if (apts.isEmpty()) return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "Valid appointment not found."));

        Long doctorId = ((Number) apts.get(0).get("doctor_id")).longValue();

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id FROM feedback WHERE appointment_id = ?", aptId);
        if (!existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "Feedback has already been submitted for this appointment."));
        }

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment) VALUES (?, ?, ?, ?, ?)",
                aptId, patientId, doctorId, rating, comment);

        // Recalculate doctor rating
        List<Map<String, Object>> stats = jdbcTemplate.queryForList("SELECT AVG(rating) as avg_r, COUNT(*) as cnt FROM feedback WHERE doctor_id = ?", doctorId);
        if (!stats.isEmpty() && stats.get(0).get("avg_r") != null) {
            Number avgNum = (Number) stats.get(0).get("avg_r");
            double avgR = avgNum.doubleValue();
            Integer cnt = ((Number) stats.get(0).get("cnt")).intValue();
            jdbcTemplate.update("UPDATE doctor_profiles SET avg_rating = ?, total_reviews = ? WHERE id = ?", avgR, cnt, doctorId);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Thank you! Your feedback has been recorded."));
    }

    @GetMapping({"", "/doctor/{doctor_id}"})
    public ResponseEntity<?> getDoctorReviews(@PathVariable(value = "doctor_id", required = false) Long doctorId) {
        List<Map<String, Object>> reviews;
        if (doctorId != null) {
            reviews = jdbcTemplate.queryForList("""
                SELECT f.*, u.full_name as patient_name
                FROM feedback f
                JOIN users u ON f.patient_id = u.id
                WHERE f.doctor_id = ?
                ORDER BY f.created_at DESC
            """, doctorId);
        } else {
            reviews = jdbcTemplate.queryForList("""
                SELECT f.*, u.full_name as patient_name
                FROM feedback f
                JOIN users u ON f.patient_id = u.id
                ORDER BY f.created_at DESC
            """);
        }

        return ResponseEntity.ok(Map.of("reviews", reviews, "count", reviews.size()));
    }
}
