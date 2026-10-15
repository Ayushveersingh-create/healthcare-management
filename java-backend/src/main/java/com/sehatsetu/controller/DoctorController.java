package com.sehatsetu.controller;

import com.sehatsetu.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/doctors")
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class DoctorController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping({"", "/search"})
    public ResponseEntity<?> searchDoctors(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long speciality_id,
            @RequestParam(required = false) String speciality,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false, defaultValue = "rating_desc") String sort_by
    ) {
        String searchTerm = search != null ? search : q;

        StringBuilder sql = new StringBuilder("""
            SELECT dp.id as doctor_id, dp.id as id, u.full_name, u.email, u.mobile, u.city as user_city,
                   s.id as speciality_id, s.name as speciality_name, s.name as speciality, s.icon as speciality_icon,
                   c.id as clinic_id, c.name as clinic_name, c.name as hospital_clinic, c.address as clinic_address, c.city as clinic_city, c.city as city,
                   dp.qualifications, dp.experience_years, dp.consultation_fee, dp.about, dp.about as bio,
                   dp.registration_number, dp.verification_status, dp.avg_rating as rating, dp.avg_rating, dp.total_reviews as review_count, dp.total_reviews
            FROM doctor_profiles dp
            JOIN users u ON dp.user_id = u.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON dp.clinic_id = c.id
            WHERE u.status = 'ACTIVE' AND dp.verification_status = 'VERIFIED'
        """);

        List<Object> params = new ArrayList<>();

        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            sql.append(" AND (LOWER(u.full_name) LIKE ? OR LOWER(s.name) LIKE ? OR LOWER(c.name) LIKE ?)");
            String term = "%" + searchTerm.trim().toLowerCase() + "%";
            params.add(term); params.add(term); params.add(term);
        }

        if (speciality_id != null && speciality_id > 0) {
            sql.append(" AND dp.speciality_id = ?");
            params.add(speciality_id);
        } else if (speciality != null && !speciality.trim().isEmpty() && !"ALL".equalsIgnoreCase(speciality)) {
            sql.append(" AND LOWER(s.name) = LOWER(?)");
            params.add(speciality.trim());
        }

        if (city != null && !city.trim().isEmpty() && !"ALL".equalsIgnoreCase(city)) {
            sql.append(" AND (LOWER(c.city) = LOWER(?) OR LOWER(u.city) = LOWER(?))");
            params.add(city.trim()); params.add(city.trim());
        }

        if ("fee_asc".equalsIgnoreCase(sort_by)) {
            sql.append(" ORDER BY dp.consultation_fee ASC");
        } else if ("exp_desc".equalsIgnoreCase(sort_by)) {
            sql.append(" ORDER BY dp.experience_years DESC");
        } else {
            sql.append(" ORDER BY dp.avg_rating DESC, dp.total_reviews DESC");
        }

        List<Map<String, Object>> doctors = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<Map<String, Object>> formattedDoctors = new ArrayList<>();
        for (Map<String, Object> doc : doctors) {
            Map<String, Object> map = new HashMap<>(doc);
            map.put("languages", "English, Hindi");
            map.put("consultation_modes", List.of("In-Clinic", "Video Consult"));
            formattedDoctors.add(map);
        }

        return ResponseEntity.ok(Map.of("doctors", formattedDoctors, "count", formattedDoctors.size()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getDoctorDetail(@PathVariable("id") Long id) {
        List<Map<String, Object>> doctors = jdbcTemplate.queryForList("""
            SELECT dp.id as doctor_id, dp.id as id, u.full_name, u.email, u.mobile, u.city as user_city,
                   s.id as speciality_id, s.name as speciality_name, s.name as speciality, s.icon as speciality_icon,
                   c.id as clinic_id, c.name as clinic_name, c.name as hospital_clinic, c.address as clinic_address, c.city as clinic_city, c.city as city,
                   dp.qualifications, dp.experience_years, dp.consultation_fee, dp.about, dp.about as bio,
                   dp.registration_number, dp.verification_status, dp.avg_rating as rating, dp.avg_rating, dp.total_reviews as review_count, dp.total_reviews
            FROM doctor_profiles dp
            JOIN users u ON dp.user_id = u.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON dp.clinic_id = c.id
            WHERE dp.id = ?
        """, id);

        if (doctors.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));
        }

        Map<String, Object> doctor = new HashMap<>(doctors.get(0));
        doctor.put("languages", "English, Hindi");
        doctor.put("consultation_modes", List.of("In-Clinic", "Video Consult"));

        List<Map<String, Object>> schedules = jdbcTemplate.queryForList("SELECT day_of_week, start_time, end_time, slot_duration_mins, break_start, break_end, is_available FROM doctor_schedules WHERE doctor_id = ?", id);
        doctor.put("schedules", schedules);

        List<Map<String, Object>> reviews = jdbcTemplate.queryForList("""
            SELECT f.*, u.full_name as patient_name
            FROM feedback f
            JOIN users u ON f.patient_id = u.id
            WHERE f.doctor_id = ?
            ORDER BY f.created_at DESC
        """, id);
        doctor.put("reviews", reviews);

        return ResponseEntity.ok(Map.of("doctor", doctor, "schedules", schedules, "doctor_id", id));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<?> getDoctorSlots(
            @PathVariable("id") Long doctorId,
            @RequestParam String date
    ) {
        LocalDate localDate;
        try {
            localDate = LocalDate.parse(date);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid date format. Expected YYYY-MM-DD."));
        }

        LocalDate todayIST = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);

        if (localDate.isBefore(todayIST)) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "date", date,
                    "is_past", true,
                    "total_slots", 0,
                    "slots", List.of(),
                    "message", "Appointments cannot be scheduled for past dates."
            ));
        }

        Integer blockedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doctor_blocked_dates WHERE doctor_id = ? AND blocked_date = ?", Integer.class, doctorId, date);
        if (blockedCount != null && blockedCount > 0) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "date", date,
                    "is_blocked", true,
                    "total_slots", 0,
                    "slots", List.of(),
                    "message", "Doctor is unavailable on this date (Blocked Date / Leave)."
            ));
        }

        int dayOfWeek = localDate.getDayOfWeek().getValue() % 7; // 0=Sun, 1=Mon, ..., 6=Sat

        List<Map<String, Object>> schedule = jdbcTemplate.queryForList(
                "SELECT * FROM doctor_schedules WHERE doctor_id = ? AND day_of_week = ? AND is_available = 1", doctorId, dayOfWeek);

        if (schedule.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "date", date, "slots", List.of(), "message", "Doctor is not available on this day of the week."));
        }

        Map<String, Object> sched = schedule.get(0);
        LocalTime startTime = LocalTime.parse((String) sched.get("start_time"));
        LocalTime endTime = LocalTime.parse((String) sched.get("end_time"));
        int durationMins = ((Number) sched.get("slot_duration_mins")).intValue();

        LocalTime breakStart = sched.get("break_start") != null && !((String) sched.get("break_start")).isEmpty() ? LocalTime.parse((String) sched.get("break_start")) : null;
        LocalTime breakEnd = sched.get("break_end") != null && !((String) sched.get("break_end")).isEmpty() ? LocalTime.parse((String) sched.get("break_end")) : null;

        List<String> bookedTimes = jdbcTemplate.queryForList(
                "SELECT appointment_time FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND status NOT IN ('CANCELLED', 'REJECTED', 'Cancelled', 'Rejected')", String.class, doctorId, date);

        Set<String> bookedSet = new HashSet<>(bookedTimes);

        List<Map<String, Object>> slots = new ArrayList<>();
        LocalTime current = startTime;
        DateTimeFormatter fmt24 = DateTimeFormatter.ofPattern("HH:mm");
        DateTimeFormatter fmt12 = DateTimeFormatter.ofPattern("hh:mm a");

        while (current.plusMinutes(durationMins).isBefore(endTime) || current.plusMinutes(durationMins).equals(endTime)) {
            LocalTime next = current.plusMinutes(durationMins);
            boolean isBreak = (breakStart != null && breakEnd != null) &&
                    (current.isBefore(breakEnd) && next.isAfter(breakStart));

            String slotStr24 = current.format(fmt24);
            String slotStr12 = current.format(fmt12);

            boolean isAlreadyBooked = bookedSet.contains(slotStr24);
            boolean isPastTime = localDate.equals(todayIST) && current.isBefore(nowIST);
            boolean isAvailable = !isBreak && !isAlreadyBooked && !isPastTime;

            if (!isBreak) {
                Map<String, Object> slotObj = new HashMap<>();
                slotObj.put("time", slotStr24);
                slotObj.put("time_24", slotStr24);
                slotObj.put("time_12", slotStr12);
                slotObj.put("is_available", isAvailable);
                slotObj.put("available", isAvailable);
                slotObj.put("status", isAlreadyBooked ? "BOOKED" : (isPastTime ? "PASSED" : "AVAILABLE"));
                slotObj.put("reason", isAlreadyBooked ? "Already booked" : (isPastTime ? "Slot time passed" : "Available"));
                slots.add(slotObj);
            }
            current = next;
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "date", date,
                "total_slots", slots.size(),
                "slots", slots,
                "available_slots", slots
        ));
    }

    @GetMapping({"/me/dashboard-stats", "/dashboard/stats"})
    public ResponseEntity<?> getDoctorDashboardStats(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        String todayStr = LocalDate.now(IST).toString();

        Integer todayTotal = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND status NOT IN ('CANCELLED', 'REJECTED')", Integer.class, docId, todayStr);
        Integer totalUpcoming = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND appointment_date >= ? AND status NOT IN ('CANCELLED', 'REJECTED', 'COMPLETED')", Integer.class, docId, todayStr);
        Integer totalCompleted = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND status = 'COMPLETED'", Integer.class, docId);
        Integer totalPatients = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT patient_id) FROM appointments WHERE doctor_id = ?", Integer.class, docId);

        List<Map<String, Object>> docMeta = jdbcTemplate.queryForList("SELECT avg_rating, total_reviews FROM doctor_profiles WHERE id = ?", docId);

        // Fetch single next upcoming appointment
        List<Map<String, Object>> nextApts = jdbcTemplate.queryForList("""
            SELECT a.*, u.full_name as patient_name, u.gender as patient_gender, u.city as patient_city
            FROM appointments a
            JOIN users u ON a.patient_id = u.id
            WHERE a.doctor_id = ? AND a.appointment_date >= ? AND a.status NOT IN ('CANCELLED', 'REJECTED', 'COMPLETED')
            ORDER BY a.appointment_date ASC, a.appointment_time ASC
            LIMIT 1
        """, docId, todayStr);

        Map<String, Object> nextMap = null;
        if (!nextApts.isEmpty()) {
            Map<String, Object> rawNext = new HashMap<>(nextApts.get(0));
            String mode = (String) rawNext.get("mode");
            rawNext.put("consultation_type", "VIDEO".equalsIgnoreCase(mode) ? "Video Consult" : "In-Clinic");
            rawNext.put("status", "Confirmed");
            nextMap = rawNext;
        }

        Map<String, Object> res = new HashMap<>();
        res.put("today_total", todayTotal != null ? todayTotal : 0);
        res.put("upcoming_total", totalUpcoming != null ? totalUpcoming : 0);
        res.put("upcoming_appointments", totalUpcoming != null ? totalUpcoming : 0);
        res.put("completed_consultations", totalCompleted != null ? totalCompleted : 0);
        res.put("completed_appointments", totalCompleted != null ? totalCompleted : 0);
        res.put("patients_seen", totalPatients != null ? totalPatients : 0);
        res.put("total_patients_seen", totalPatients != null ? totalPatients : 0);
        res.put("avg_rating", !docMeta.isEmpty() ? docMeta.get(0).get("avg_rating") : 5.0);
        res.put("total_reviews", !docMeta.isEmpty() ? docMeta.get(0).get("total_reviews") : 0);
        res.put("next_appointment", nextMap);

        return ResponseEntity.ok(res);
    }

    @GetMapping("/me/schedule")
    public ResponseEntity<?> getMySchedule(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        List<Map<String, Object>> rawSchedules = jdbcTemplate.queryForList("SELECT * FROM doctor_schedules WHERE doctor_id = ? ORDER BY day_of_week ASC", docId);
        List<Map<String, Object>> blockedDates = jdbcTemplate.queryForList("SELECT * FROM doctor_blocked_dates WHERE doctor_id = ? ORDER BY blocked_date ASC", docId);

        List<Map<String, Object>> schedules = new ArrayList<>();
        for (Map<String, Object> s : rawSchedules) {
            Map<String, Object> m = new HashMap<>(s);
            m.put("is_active", s.get("is_available"));
            schedules.add(m);
        }

        return ResponseEntity.ok(Map.of("schedules", schedules, "blocked_dates", blockedDates));
    }

    @RequestMapping(value = "/me/schedule", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateMySchedule(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        List<?> list = (List<?>) req.get("schedules");

        if (list != null) {
            for (Object obj : list) {
                if (obj instanceof Map<?, ?> item) {
                    int dayOfWeek = ((Number) item.get("day_of_week")).intValue();
                    String startTime = (String) item.get("start_time");
                    String endTime = (String) item.get("end_time");
                    String breakStart = (String) item.get("break_start");
                    String breakEnd = (String) item.get("break_end");
                    int duration = item.get("slot_duration_mins") != null ? ((Number) item.get("slot_duration_mins")).intValue() : 30;
                    int isAvailable = 1;
                    if (item.containsKey("is_active")) {
                        Object act = item.get("is_active");
                        isAvailable = (Boolean.TRUE.equals(act) || Integer.valueOf(1).equals(act)) ? 1 : 0;
                    } else if (item.containsKey("is_available")) {
                        Object avail = item.get("is_available");
                        isAvailable = (Boolean.TRUE.equals(avail) || Integer.valueOf(1).equals(avail)) ? 1 : 0;
                    }

                    jdbcTemplate.update("""
                        INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_duration_mins, break_start, break_end, is_available)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(doctor_id, day_of_week) DO UPDATE SET
                            start_time = excluded.start_time,
                            end_time = excluded.end_time,
                            slot_duration_mins = excluded.slot_duration_mins,
                            break_start = excluded.break_start,
                            break_end = excluded.break_end,
                            is_available = excluded.is_available
                    """, docId, dayOfWeek, startTime, endTime, duration, breakStart, breakEnd, isAvailable);
                }
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "Schedule updated successfully."));
    }

    @GetMapping("/me/blocked-dates")
    public ResponseEntity<?> getMyBlockedDates(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        List<Map<String, Object>> blocked = jdbcTemplate.queryForList("SELECT * FROM doctor_blocked_dates WHERE doctor_id = ? ORDER BY blocked_date ASC", docId);

        return ResponseEntity.ok(Map.of("blocked_dates", blocked));
    }

    @PostMapping("/me/blocked-dates")
    public ResponseEntity<?> addBlockedDate(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        String blockedDate = (String) req.get("blocked_date");
        String reason = req.get("reason") != null ? (String) req.get("reason") : "Doctor Leave";

        if (blockedDate == null) return ResponseEntity.badRequest().body(Map.of("detail", "blocked_date is required."));

        jdbcTemplate.update("INSERT OR IGNORE INTO doctor_blocked_dates (doctor_id, blocked_date, reason) VALUES (?, ?, ?)", docId, blockedDate, reason);

        return ResponseEntity.ok(Map.of("success", true, "message", "Blocked date added successfully."));
    }

    @DeleteMapping("/me/blocked-dates/{id}")
    public ResponseEntity<?> deleteBlockedDate(
            @PathVariable("id") Long blockedId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();
        jdbcTemplate.update("DELETE FROM doctor_blocked_dates WHERE id = ? AND doctor_id = ?", blockedId, docId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Blocked date removed successfully."));
    }

    @GetMapping("/me/patients")
    public ResponseEntity<?> getMyPatients(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> docList = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
        if (docList.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Doctor profile not found."));

        Long docId = ((Number) docList.get(0).get("id")).longValue();

        List<Map<String, Object>> patients = jdbcTemplate.queryForList("""
            SELECT DISTINCT u.id as patient_id, u.id as id, u.full_name, u.email, u.mobile, u.city, u.gender, u.blood_group,
                   COUNT(a.id) as total_visits, COUNT(a.id) as total_appointments, MAX(a.appointment_date) as last_visit_date, MAX(a.appointment_date) as last_consultation_date
            FROM appointments a
            JOIN users u ON a.patient_id = u.id
            WHERE a.doctor_id = ?
            GROUP BY u.id
            ORDER BY last_visit_date DESC
        """, docId);

        return ResponseEntity.ok(Map.of("patients", patients));
    }

    @GetMapping("/patient-detail/{patient_id}")
    public ResponseEntity<?> getDoctorPatientDetail(
            @PathVariable("patient_id") Long patientId,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long authUserId = claims.get("user_id", Long.class);
        String role = (String) claims.get("role");

        if ("DOCTOR".equalsIgnoreCase(role)) {
            List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", authUserId);
            if (!docs.isEmpty()) {
                Long docId = ((Number) docs.get(0).get("id")).longValue();
                Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM appointments WHERE doctor_id = ? AND patient_id = ?", Integer.class, docId, patientId);
                if (count == null || count == 0) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Access denied. Doctor has no consultation records with this patient."));
                }
            }
        } else if (!"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("detail", "Access denied. Only doctors and admins can view patient details."));
        }

        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, email, full_name, mobile, city, dob, gender, blood_group, address, emergency_contact FROM users WHERE id = ?", patientId);
        if (users.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("detail", "Patient account not found."));

        Map<String, Object> user = new HashMap<>(users.get(0));

        List<Map<String, Object>> profiles = jdbcTemplate.queryForList("SELECT * FROM patient_profiles WHERE user_id = ?", patientId);
        Map<String, Object> profile = profiles.isEmpty() ? Map.of() : profiles.get(0);

        List<Map<String, Object>> apts = jdbcTemplate.queryForList("""
            SELECT a.*, u_doc.full_name as doctor_name, s.name as speciality_name, c.name as clinic_name
            FROM appointments a
            JOIN doctor_profiles dp ON a.doctor_id = dp.id
            JOIN users u_doc ON dp.user_id = u_doc.id
            JOIN specialities s ON dp.speciality_id = s.id
            JOIN clinics c ON a.clinic_id = c.id
            WHERE a.patient_id = ?
            ORDER BY a.appointment_date DESC, a.appointment_time DESC
        """, patientId);

        List<Map<String, Object>> records = jdbcTemplate.queryForList("""
            SELECT mr.*, u_doc.full_name as doctor_name, s.name as speciality_name
            FROM medical_records mr
            JOIN doctor_profiles dp ON mr.doctor_id = dp.id
            JOIN users u_doc ON dp.user_id = u_doc.id
            JOIN specialities s ON dp.speciality_id = s.id
            WHERE mr.patient_id = ?
            ORDER BY mr.created_at DESC
        """, patientId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "patient", user,
                "profile", profile,
                "appointments", apts,
                "records", records
        ));
    }
}

