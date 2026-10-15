package com.sehatsetu.controller;

import com.sehatsetu.util.JwtUtil;
import com.sehatsetu.util.PasswordUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostMapping("/demo-login")
    public ResponseEntity<?> demoLogin(@RequestBody Map<String, String> request) {
        String roleParam = request.get("role");
        if (roleParam == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Role is required."));
        }

        String roleUpper = roleParam.toUpperCase();
        Map<String, String> roleEmailMap = Map.of(
                "PATIENT", "aarav.singh@gmail.com",
                "DOCTOR", "ananya.sharma@sehatsetu.in",
                "ADMIN", "admin@sehatsetu.in"
        );

        String reqEmail = request.get("email");
        String targetEmail = (reqEmail != null && !reqEmail.trim().isEmpty()) ? reqEmail.trim().toLowerCase() : roleEmailMap.get(roleUpper);
        if (targetEmail == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid demo role or email address."));
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, email, password_hash, role, full_name, mobile, city, status FROM users WHERE email = ?", targetEmail);

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Demo account not found in database."));
        }

        Map<String, Object> user = new HashMap<>(users.get(0));
        String pwHash = (String) user.remove("password_hash");

        // Password protection for DOCTOR and ADMIN
        if (Set.of("DOCTOR", "ADMIN").contains(roleUpper)) {
            String password = request.get("password");
            if (password == null || password.isEmpty()) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "detail", "Password required for " + roleUpper + " login access. Default password: " + capitalize(roleUpper) + "@123"
                ));
            }
            if (!PasswordUtil.verifyPassword(password, pwHash)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                        "detail", "Incorrect password for " + roleUpper + " login. Please enter the valid password."
                ));
            }
        }

        Long userId = ((Number) user.get("id")).longValue();
        String token = JwtUtil.generateToken(userId, targetEmail, (String) user.get("role"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Demo login successful.",
                "access_token", token,
                "token_type", "bearer",
                "user", user
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String password = request.get("password");

        if (email == null || password == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Email and password are required."));
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList(
                "SELECT id, email, password_hash, role, full_name, mobile, city, status FROM users WHERE LOWER(email) = LOWER(?)", email.trim());

        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Invalid email or password. Please verify your credentials."));
        }

        Map<String, Object> user = new HashMap<>(users.get(0));
        String pwHash = (String) user.remove("password_hash");

        if (!PasswordUtil.verifyPassword(password, pwHash)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Invalid email or password. Please verify your credentials."));
        }

        if ("SUSPENDED".equalsIgnoreCase((String) user.get("status"))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Your account has been suspended. Please contact administrator."));
        }

        Long userId = ((Number) user.get("id")).longValue();
        String token = JwtUtil.generateToken(userId, (String) user.get("email"), (String) user.get("role"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Login successful.",
                "access_token", token,
                "token_type", "bearer",
                "user", user
        ));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, Object> request) {
        String email = (String) request.get("email");
        String password = (String) request.get("password");
        String fullName = (String) request.get("full_name");
        String mobile = (String) request.get("mobile");
        String city = (String) request.get("city");
        String dob = (String) request.get("dob");
        String gender = (String) request.get("gender");
        String bloodGroup = (String) request.get("blood_group");
        String address = (String) request.get("address");
        String emergencyContact = (String) request.get("emergency_contact");

        if (email == null || password == null || fullName == null) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Email, password, and full_name are required."));
        }

        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT id FROM users WHERE LOWER(email) = LOWER(?)", email.trim());
        if (!existing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Email address is already registered."));
        }

        String pwHash = PasswordUtil.hashPassword(password);
        jdbcTemplate.update("""
            INSERT INTO users (email, password_hash, role, full_name, mobile, city, dob, gender, blood_group, address, emergency_contact, status)
            VALUES (?, ?, 'PATIENT', ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE');
        """, email.toLowerCase().trim(), pwHash, fullName.trim(), mobile, city, dob, gender, bloodGroup, address, emergencyContact);

        Long newUserId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO patient_profiles (user_id, blood_group, emergency_phone) VALUES (?, ?, ?)", newUserId, bloodGroup, emergencyContact);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, target_url) VALUES (?, ?, ?, 'SYSTEM', '/patient/dashboard')",
                newUserId, "Welcome to SehatSetu!", "Your patient account has been created. You can now search top Indian doctors and book consultations.");

        String token = JwtUtil.generateToken(newUserId, email, "PATIENT");

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Registration successful. Welcome to SehatSetu!",
                "access_token", token,
                "token_type", "bearer",
                "user", Map.of("id", newUserId, "email", email, "role", "PATIENT", "full_name", fullName, "city", city)
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Invalid or expired authorization token."));
        }

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, email, role, full_name, mobile, city, dob, gender, blood_group, address, emergency_contact, status FROM users WHERE id = ?", userId);
        if (users.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "User account not found."));
        }

        Map<String, Object> user = new HashMap<>(users.get(0));
        String role = (String) user.get("role");

        if ("PATIENT".equalsIgnoreCase(role)) {
            List<Map<String, Object>> profiles = jdbcTemplate.queryForList("SELECT * FROM patient_profiles WHERE user_id = ?", userId);
            user.put("patient_profile", profiles.isEmpty() ? Map.of() : profiles.get(0));
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            List<Map<String, Object>> profiles = jdbcTemplate.queryForList("""
                SELECT dp.*, s.name as speciality_name, c.name as clinic_name, c.city as clinic_city
                FROM doctor_profiles dp
                JOIN specialities s ON dp.speciality_id = s.id
                JOIN clinics c ON dp.clinic_id = c.id
                WHERE dp.user_id = ?
            """, userId);
            user.put("doctor_profile", profiles.isEmpty() ? Map.of() : profiles.get(0));
        }

        return ResponseEntity.ok(user);
    }

    @RequestMapping(value = "/profile", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateProfile(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));
        }

        Long userId = claims.get("user_id", Long.class);

        String fullName = (String) req.get("full_name");
        String mobile = (String) req.get("mobile");
        String city = (String) req.get("city");
        String dob = (String) req.get("dob");
        String gender = (String) req.get("gender");
        String bloodGroup = (String) req.get("blood_group");
        String address = (String) req.get("address");
        String emergencyContact = (String) req.get("emergency_contact");

        jdbcTemplate.update("""
            UPDATE users SET 
                full_name = COALESCE(?, full_name),
                mobile = COALESCE(?, mobile),
                city = COALESCE(?, city),
                dob = COALESCE(?, dob),
                gender = COALESCE(?, gender),
                blood_group = COALESCE(?, blood_group),
                address = COALESCE(?, address),
                emergency_contact = COALESCE(?, emergency_contact),
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
        """, fullName, mobile, city, dob, gender, bloodGroup, address, emergencyContact, userId);

        String role = (String) claims.get("role");
        if ("PATIENT".equalsIgnoreCase(role)) {
            String allergies = (String) req.get("allergies");
            String chronicConditions = req.get("chronic_conditions") != null ? (String) req.get("chronic_conditions") : (String) req.get("medical_history");
            String currentMedications = (String) req.get("current_medications");
            String emergencyName = (String) req.get("emergency_name");
            String emergencyPhone = req.get("emergency_phone") != null ? (String) req.get("emergency_phone") : emergencyContact;
            String emergencyRelation = (String) req.get("emergency_relation");

            Double heightCm = req.get("height_cm") != null ? Double.valueOf(req.get("height_cm").toString()) : (req.get("height") != null ? Double.valueOf(req.get("height").toString()) : null);
            Double weightKg = req.get("weight_kg") != null ? Double.valueOf(req.get("weight_kg").toString()) : (req.get("weight") != null ? Double.valueOf(req.get("weight").toString()) : null);
            Integer bpSys = req.get("bp_systolic") != null ? Integer.valueOf(req.get("bp_systolic").toString()) : null;
            Integer bpDia = req.get("bp_diastolic") != null ? Integer.valueOf(req.get("bp_diastolic").toString()) : null;
            Integer pulseRate = req.get("pulse_rate") != null ? Integer.valueOf(req.get("pulse_rate").toString()) : (req.get("pulse") != null ? Integer.valueOf(req.get("pulse").toString()) : null);
            Double tempF = req.get("temperature_f") != null ? Double.valueOf(req.get("temperature_f").toString()) : (req.get("temperature") != null ? Double.valueOf(req.get("temperature").toString()) : null);
            Integer spo2Percent = req.get("spo2_percent") != null ? Integer.valueOf(req.get("spo2_percent").toString()) : (req.get("spo2") != null ? Integer.valueOf(req.get("spo2").toString()) : null);

            jdbcTemplate.update("""
                INSERT INTO patient_profiles (user_id, blood_group, allergies, chronic_conditions, current_medications,
                                              height_cm, weight_kg, bp_systolic, bp_diastolic, pulse_rate, temperature_f, spo2_percent,
                                              emergency_name, emergency_phone, emergency_relation, vitals_updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                ON CONFLICT(user_id) DO UPDATE SET
                    blood_group = COALESCE(excluded.blood_group, blood_group),
                    allergies = COALESCE(excluded.allergies, allergies),
                    chronic_conditions = COALESCE(excluded.chronic_conditions, chronic_conditions),
                    current_medications = COALESCE(excluded.current_medications, current_medications),
                    height_cm = COALESCE(excluded.height_cm, height_cm),
                    weight_kg = COALESCE(excluded.weight_kg, weight_kg),
                    bp_systolic = COALESCE(excluded.bp_systolic, bp_systolic),
                    bp_diastolic = COALESCE(excluded.bp_diastolic, bp_diastolic),
                    pulse_rate = COALESCE(excluded.pulse_rate, pulse_rate),
                    temperature_f = COALESCE(excluded.temperature_f, temperature_f),
                    spo2_percent = COALESCE(excluded.spo2_percent, spo2_percent),
                    emergency_name = COALESCE(excluded.emergency_name, emergency_name),
                    emergency_phone = COALESCE(excluded.emergency_phone, emergency_phone),
                    emergency_relation = COALESCE(excluded.emergency_relation, emergency_relation),
                    vitals_updated_at = CURRENT_TIMESTAMP
            """, userId, bloodGroup, allergies, chronicConditions, currentMedications,
                 heightCm, weightKg, bpSys, bpDia, pulseRate, tempF, spo2Percent,
                 emergencyName, emergencyPhone, emergencyRelation);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully."));
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }
}
