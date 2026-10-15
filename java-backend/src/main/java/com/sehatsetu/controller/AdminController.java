package com.sehatsetu.controller;

import com.sehatsetu.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class AdminController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private boolean verifyAdmin(String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        return claims != null && "ADMIN".equalsIgnoreCase((String) claims.get("role"));
    }

    @GetMapping({"/stats", "/kpis"})
    public ResponseEntity<?> getKPIs(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!verifyAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        }

        String todayStr = LocalDate.now(IST).toString();

        Integer totalUsers = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class);
        Integer totalDoctors = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM doctor_profiles", Integer.class);
        Integer totalPatients = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE role = 'PATIENT'", Integer.class);
        Integer totalAppointments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments", Integer.class);
        Integer todayAppointments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE appointment_date = ?", Integer.class, todayStr);
        Integer completedAppointments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE status = 'COMPLETED'", Integer.class);
        Integer cancelledAppointments = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE status IN ('CANCELLED', 'Cancelled')", Integer.class);

        int totalAptsCount = totalAppointments != null ? totalAppointments : 0;
        int completedCount = completedAppointments != null ? completedAppointments : 0;
        int successRate = totalAptsCount > 0 ? (completedCount * 100 / totalAptsCount) : 100;

        Map<String, Object> stats = Map.of(
                "total_users", totalUsers != null ? totalUsers : 0,
                "total_doctors", totalDoctors != null ? totalDoctors : 0,
                "total_patients", totalPatients != null ? totalPatients : 0,
                "total_appointments", totalAptsCount,
                "today_appointments", todayAppointments != null ? todayAppointments : 0,
                "completed_appointments", completedCount,
                "cancelled_appointments", cancelledAppointments != null ? cancelledAppointments : 0,
                "success_rate", successRate
        );

        Map<String, Object> response = new HashMap<>(stats);
        response.put("stats", stats);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/analytics")
    public ResponseEntity<?> getAnalytics(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!verifyAdmin(authHeader)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        }

        List<Map<String, Object>> specialitiesData = jdbcTemplate.queryForList("""
            SELECT s.name as speciality, s.name as category, COUNT(a.id) as count
            FROM appointments a
            JOIN doctor_profiles dp ON a.doctor_id = dp.id
            JOIN specialities s ON dp.speciality_id = s.id
            GROUP BY s.name
            ORDER BY count DESC
        """);

        List<Map<String, Object>> citiesData = jdbcTemplate.queryForList("""
            SELECT c.city as city, c.city as category, COUNT(a.id) as count
            FROM appointments a
            JOIN clinics c ON a.clinic_id = c.id
            GROUP BY c.city
            ORDER BY count DESC
        """);

        List<Map<String, Object>> rawModes = jdbcTemplate.queryForList("""
            SELECT mode, COUNT(*) as count
            FROM appointments
            GROUP BY mode
        """);

        List<Map<String, Object>> modesData = new ArrayList<>();
        for (Map<String, Object> r : rawModes) {
            String mode = (String) r.get("mode");
            String displayType = "VIDEO".equalsIgnoreCase(mode) ? "Video Consult" : "In-Clinic";
            modesData.add(Map.of("consultation_type", displayType, "category", displayType, "count", r.get("count")));
        }

        List<Map<String, Object>> rawTrends = jdbcTemplate.queryForList("""
            SELECT appointment_date,
                   COUNT(*) as total,
                   SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END) as completed,
                   SUM(CASE WHEN status IN ('CANCELLED', 'Cancelled') THEN 1 ELSE 0 END) as cancelled
            FROM appointments
            GROUP BY appointment_date
            ORDER BY appointment_date ASC
            LIMIT 14
        """);

        List<Map<String, Object>> trendsData = new ArrayList<>();
        for (Map<String, Object> r : rawTrends) {
            Map<String, Object> m = new HashMap<>(r);
            m.put("category", r.get("appointment_date"));
            trendsData.add(m);
        }

        List<Map<String, Object>> topDoctors = jdbcTemplate.queryForList("""
            SELECT dp.id, u.full_name, s.name as speciality, c.city, dp.avg_rating as rating, COUNT(a.id) as appointment_count
            FROM doctor_profiles dp
            JOIN users u ON dp.user_id = u.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON dp.clinic_id = c.id
            LEFT JOIN appointments a ON dp.id = a.doctor_id
            GROUP BY dp.id
            ORDER BY appointment_count DESC, rating DESC
            LIMIT 5
        """);

        return ResponseEntity.ok(Map.of(
                "by_speciality", specialitiesData,
                "by_city", citiesData,
                "by_consultation_type", modesData,
                "trends", trendsData,
                "specialities", specialitiesData,
                "cities", citiesData,
                "modes", modesData,
                "daily_trends", trendsData,
                "top_doctors", topDoctors
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));

        List<Map<String, Object>> rawUsers = jdbcTemplate.queryForList("""
            SELECT u.id, u.email, u.role, u.full_name, u.mobile, u.city, u.status, u.created_at,
                   (SELECT COUNT(*) FROM appointments a WHERE a.patient_id = u.id) as appointment_count
            FROM users u
            ORDER BY u.created_at DESC
        """);

        return ResponseEntity.ok(Map.of("users", rawUsers, "count", rawUsers.size()));
    }

    @RequestMapping(value = "/users/{id}/status", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateUserStatus(
            @PathVariable("id") Long userId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        String newStatus = body.get("status");
        jdbcTemplate.update("UPDATE users SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", newStatus, userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "User status updated to " + newStatus));
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<?> deleteUser(
            @PathVariable("id") Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "User deleted successfully."));
    }

    @GetMapping("/doctors")
    public ResponseEntity<?> getDoctors(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));

        List<Map<String, Object>> doctors = jdbcTemplate.queryForList("""
            SELECT dp.id, dp.id as doctor_id, u.full_name, u.email, u.mobile, u.city, s.name as speciality, c.name as hospital_clinic, c.city as clinic_city,
                   dp.experience_years, dp.consultation_fee, dp.verification_status, dp.avg_rating as rating, dp.total_reviews as review_count
            FROM doctor_profiles dp
            JOIN users u ON dp.user_id = u.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON dp.clinic_id = c.id
            ORDER BY dp.id DESC
        """);

        return ResponseEntity.ok(Map.of("doctors", doctors, "count", doctors.size()));
    }

    @RequestMapping(value = "/doctors/{id}/verification", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateDoctorVerification(
            @PathVariable("id") Long doctorId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        String status = body.get("verification_status");
        jdbcTemplate.update("UPDATE doctor_profiles SET verification_status = ? WHERE id = ?", status, doctorId);
        return ResponseEntity.ok(Map.of("success", true, "message", "Doctor verification status updated to " + status));
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getAllAppointments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long doctor_id,
            @RequestParam(required = false) Long patient_id,
            @RequestParam(required = false) String date,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));

        StringBuilder sql = new StringBuilder("""
            SELECT a.*, u_pat.full_name as patient_name, u_doc.full_name as doctor_name, s.name as speciality, c.name as clinic_name
            FROM appointments a
            JOIN users u_pat ON a.patient_id = u_pat.id
            JOIN doctor_profiles dp ON a.doctor_id = dp.id
            JOIN users u_doc ON dp.user_id = u_doc.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON a.clinic_id = c.id
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (search != null && !search.trim().isEmpty()) {
            sql.append(" AND (LOWER(u_pat.full_name) LIKE ? OR LOWER(u_doc.full_name) LIKE ? OR LOWER(a.reference_code) LIKE ? OR LOWER(s.name) LIKE ?)");
            String term = "%" + search.trim().toLowerCase() + "%";
            params.add(term); params.add(term); params.add(term); params.add(term);
        }

        if (status != null && !status.trim().isEmpty() && !"ALL".equalsIgnoreCase(status)) {
            String norm = status.toUpperCase();
            if (norm.contains("CONFIRM") || norm.contains("SCHEDULE")) norm = "SCHEDULED";
            else if (norm.contains("COMPLETE")) norm = "COMPLETED";
            else if (norm.contains("CANCEL")) norm = "CANCELLED";
            sql.append(" AND UPPER(a.status) = ?");
            params.add(norm);
        }

        if (doctor_id != null && doctor_id > 0) {
            sql.append(" AND a.doctor_id = ?");
            params.add(doctor_id);
        }

        if (patient_id != null && patient_id > 0) {
            sql.append(" AND a.patient_id = ?");
            params.add(patient_id);
        }

        if (date != null && !date.trim().isEmpty()) {
            sql.append(" AND a.appointment_date = ?");
            params.add(date.trim());
        }

        sql.append(" ORDER BY a.appointment_date DESC, a.appointment_time DESC");

        List<Map<String, Object>> rawList = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<Map<String, Object>> formatted = new ArrayList<>();
        for (Map<String, Object> row : rawList) {
            Map<String, Object> map = new HashMap<>(row);
            String rawStatus = (String) map.get("status");
            String displayStatus = "SCHEDULED".equalsIgnoreCase(rawStatus) ? "Confirmed" :
                    ("COMPLETED".equalsIgnoreCase(rawStatus) ? "Completed" :
                    ("CANCELLED".equalsIgnoreCase(rawStatus) ? "Cancelled" : rawStatus));

            String mode = (String) map.get("mode");
            String consultType = "VIDEO".equalsIgnoreCase(mode) ? "Video Consult" : "In-Clinic";

            map.put("reference_id", map.get("reference_code"));
            map.put("consultation_type", consultType);
            map.put("status", displayStatus);
            map.put("appointment_date_formatted", formatDateIST((String) map.get("appointment_date")));

            formatted.add(map);
        }

        return ResponseEntity.ok(Map.of("appointments", formatted, "count", formatted.size()));
    }

    @GetMapping("/settings")
    public ResponseEntity<?> getSettings() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT key, value FROM system_settings");
        Map<String, String> settings = new HashMap<>();
        for (Map<String, Object> row : list) {
            settings.put((String) row.get("key"), (String) row.get("value"));
        }
        return ResponseEntity.ok(Map.of("settings", settings));
    }

    @RequestMapping(value = "/settings", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateSettings(
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));

        Map<?, ?> settingsMap = (Map<?, ?>) body.get("settings");
        if (settingsMap != null) {
            for (Map.Entry<?, ?> entry : settingsMap.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String value = String.valueOf(entry.getValue());
                jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value", key, value);
            }
        }
        return ResponseEntity.ok(Map.of("success", true, "message", "System settings updated successfully."));
    }

    @GetMapping({"/activity-logs", "/audit-trail"})
    public ResponseEntity<?> getAuditTrail(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        if (!verifyAdmin(authHeader)) return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Admin privileges required."));
        List<Map<String, Object>> logs = jdbcTemplate.queryForList("SELECT * FROM audit_logs ORDER BY created_at DESC LIMIT 50");
        return ResponseEntity.ok(Map.of("logs", logs, "activity_logs", logs));
    }

    private static String formatDateIST(String dateStr) {
        if (dateStr == null) return "-";
        try {
            String[] p = dateStr.split("-");
            if (p.length == 3) return p[2] + "/" + p[1] + "/" + p[0];
            return dateStr;
        } catch (Exception e) {
            return dateStr;
        }
    }
}

