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
@RequestMapping("/api/appointments")
@CrossOrigin(origins = "*")
public class AppointmentController {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/slots")
    public ResponseEntity<?> getAvailableSlots(
            @RequestParam Long doctor_id,
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
                    "available_slots", List.of(),
                    "message", "Appointments cannot be scheduled for past dates."
            ));
        }

        // Check if date is blocked by doctor
        Integer blockedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doctor_blocked_dates WHERE doctor_id = ? AND blocked_date = ?", Integer.class, doctor_id, date);
        if (blockedCount != null && blockedCount > 0) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "date", date,
                    "is_blocked", true,
                    "total_slots", 0,
                    "slots", List.of(),
                    "available_slots", List.of(),
                    "message", "Doctor is unavailable on this date (Blocked Date / Holiday)."
            ));
        }

        int dayOfWeek = localDate.getDayOfWeek().getValue() % 7; // 0=Sun, 1=Mon, ..., 6=Sat

        List<Map<String, Object>> schedule = jdbcTemplate.queryForList(
                "SELECT * FROM doctor_schedules WHERE doctor_id = ? AND day_of_week = ? AND is_available = 1", doctor_id, dayOfWeek);

        if (schedule.isEmpty()) {
            return ResponseEntity.ok(Map.of("success", true, "date", date, "slots", List.of(), "available_slots", List.of(), "message", "Doctor is not available on this day of the week."));
        }

        Map<String, Object> sched = schedule.get(0);
        LocalTime startTime = LocalTime.parse((String) sched.get("start_time"));
        LocalTime endTime = LocalTime.parse((String) sched.get("end_time"));
        int durationMins = ((Number) sched.get("slot_duration_mins")).intValue();

        LocalTime breakStart = sched.get("break_start") != null && !((String) sched.get("break_start")).isEmpty() ? LocalTime.parse((String) sched.get("break_start")) : null;
        LocalTime breakEnd = sched.get("break_end") != null && !((String) sched.get("break_end")).isEmpty() ? LocalTime.parse((String) sched.get("break_end")) : null;

        List<String> bookedTimes = jdbcTemplate.queryForList(
                "SELECT appointment_time FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND status NOT IN ('CANCELLED', 'REJECTED', 'Cancelled', 'Rejected')", String.class, doctor_id, date);

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
                Map<String, Object> s = new HashMap<>();
                s.put("time", slotStr24);
                s.put("time_24", slotStr24);
                s.put("time_12", slotStr12);
                s.put("is_available", isAvailable);
                s.put("available", isAvailable);
                s.put("status", isAlreadyBooked ? "BOOKED" : (isPastTime ? "PASSED" : "AVAILABLE"));
                s.put("reason", isAlreadyBooked ? "Already booked" : (isPastTime ? "Slot time passed" : "Available"));
                slots.add(s);
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

    @PostMapping("/book")
    public ResponseEntity<?> bookAppointment(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized access. Please login as a patient."));

        Long patientId = claims.get("user_id", Long.class);
        Long doctorId = ((Number) req.get("doctor_id")).longValue();
        String appointmentDate = (String) req.get("appointment_date");
        String appointmentTime = (String) req.get("appointment_time");

        LocalDate localDate;
        LocalTime localTime;
        try {
            localDate = LocalDate.parse(appointmentDate);
            localTime = LocalTime.parse(appointmentTime);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid date or time format. Expected YYYY-MM-DD and HH:mm."));
        }

        LocalDate todayIST = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);

        if (localDate.isBefore(todayIST) || (localDate.equals(todayIST) && localTime.isBefore(nowIST))) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Cannot book appointments for past dates or time slots."));
        }

        // Check if date is blocked
        Integer blockedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doctor_blocked_dates WHERE doctor_id = ? AND blocked_date = ?", Integer.class, doctorId, appointmentDate);
        if (blockedCount != null && blockedCount > 0) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Doctor is unavailable on this date (Blocked Date / Leave)."));
        }

        String rawMode = "IN_CLINIC";
        if (req.get("consultation_type") != null) {
            rawMode = String.valueOf(req.get("consultation_type"));
        } else if (req.get("mode") != null) {
            rawMode = String.valueOf(req.get("mode"));
        }

        String mode = (rawMode.equalsIgnoreCase("video") || rawMode.equalsIgnoreCase("online")) ? "VIDEO" : "IN_CLINIC";
        String reason = req.get("reason_for_visit") != null ? (String) req.get("reason_for_visit") : "General Consultation";

        List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT clinic_id FROM doctor_profiles WHERE id = ?", doctorId);
        if (docs.isEmpty()) return ResponseEntity.badRequest().body(Map.of("detail", "Selected doctor not found."));

        Long clinicId = ((Number) docs.get(0).get("clinic_id")).longValue();

        // Double booking collision check across all active non-cancelled appointments
        List<Map<String, Object>> existing = jdbcTemplate.queryForList(
                "SELECT id FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND appointment_time = ? AND status NOT IN ('CANCELLED', 'REJECTED', 'Cancelled', 'Rejected')",
                doctorId, appointmentDate, appointmentTime);

        if (!existing.isEmpty()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("detail", "This slot has already been booked. Please select another time slot."));
        }

        String refCode = "SS-APT-" + appointmentDate.replace("-", "") + "-" + (1000 + new Random().nextInt(9000));

        jdbcTemplate.update("""
            INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'SCHEDULED', ?)
        """, refCode, patientId, doctorId, clinicId, appointmentDate, appointmentTime, mode, reason);

        Long aptId = jdbcTemplate.queryForObject("SELECT id FROM appointments WHERE reference_code = ?", Long.class, refCode);

        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, target_url) VALUES (?, ?, ?, 'APPOINTMENT', '/patient/appointments')",
                patientId, "Appointment Booked Successfully!", "Your appointment (Ref: " + refCode + ") on " + appointmentDate + " at " + appointmentTime + " has been confirmed.");

        Map<String, Object> aptMap = Map.of(
                "id", aptId,
                "reference_code", refCode,
                "reference_id", refCode,
                "patient_id", patientId,
                "doctor_id", doctorId,
                "appointment_date", appointmentDate,
                "appointment_time", appointmentTime,
                "status", "SCHEDULED"
        );

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Appointment booked successfully!",
                "appointment", aptMap,
                "appointment_id", aptId,
                "reference_code", refCode,
                "reference_id", refCode,
                "appointment_date", appointmentDate,
                "appointment_time", appointmentTime
        ));
    }

    @RequestMapping(value = {"/reschedule", "/{id}/reschedule"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> rescheduleAppointment(
            @PathVariable(value = "id", required = false) Long pathAptId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long aptId = pathAptId;
        if (aptId == null && req.containsKey("appointment_id")) {
            aptId = ((Number) req.get("appointment_id")).longValue();
        }
        if (aptId == null) return ResponseEntity.badRequest().body(Map.of("detail", "Appointment ID is required."));

        String newDate = req.containsKey("appointment_date") ? (String) req.get("appointment_date") : (String) req.get("new_date");
        String newTime = req.containsKey("appointment_time") ? (String) req.get("appointment_time") : (String) req.get("new_time");

        LocalDate localDate;
        LocalTime localTime;
        try {
            localDate = LocalDate.parse(newDate);
            localTime = LocalTime.parse(newTime);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid new date or time format. Expected YYYY-MM-DD and HH:mm."));
        }

        LocalDate todayIST = LocalDate.now(IST);
        LocalTime nowIST = LocalTime.now(IST);

        if (localDate.isBefore(todayIST) || (localDate.equals(todayIST) && localTime.isBefore(nowIST))) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Cannot reschedule to a past date or time slot."));
        }

        List<Map<String, Object>> apts = jdbcTemplate.queryForList("SELECT doctor_id, patient_id, status FROM appointments WHERE id = ?", aptId);
        if (apts.isEmpty()) return ResponseEntity.badRequest().body(Map.of("detail", "Appointment not found."));

        Map<String, Object> targetApt = apts.get(0);
        String currentStatus = (String) targetApt.get("status");

        if ("CANCELLED".equalsIgnoreCase(currentStatus) || "COMPLETED".equalsIgnoreCase(currentStatus)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Cannot reschedule an appointment that is " + currentStatus.toLowerCase() + "."));
        }

        Long docId = ((Number) targetApt.get("doctor_id")).longValue();

        // Check if doctor blocked date
        Integer blockedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM doctor_blocked_dates WHERE doctor_id = ? AND blocked_date = ?", Integer.class, docId, newDate);
        if (blockedCount != null && blockedCount > 0) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Target date is blocked by the doctor for leave/holiday."));
        }

        List<Map<String, Object>> collision = jdbcTemplate.queryForList(
                "SELECT id FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND appointment_time = ? AND status NOT IN ('CANCELLED', 'REJECTED', 'Cancelled', 'Rejected') AND id != ?",
                docId, newDate, newTime, aptId);

        if (!collision.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "The target slot is already booked. Please choose another slot."));
        }

        jdbcTemplate.update("UPDATE appointments SET appointment_date = ?, appointment_time = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", newDate, newTime, aptId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Appointment rescheduled successfully to " + newDate + " " + newTime + "."));
    }

    @RequestMapping(value = {"/cancel", "/{id}/cancel"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> cancelAppointment(
            @PathVariable(value = "id", required = false) Long pathAptId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody(required = false) Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long aptId = pathAptId;
        if (aptId == null && req != null && req.containsKey("appointment_id")) {
            aptId = ((Number) req.get("appointment_id")).longValue();
        }
        if (aptId == null) return ResponseEntity.badRequest().body(Map.of("detail", "Appointment ID is required."));

        String reason = (req != null && req.get("cancellation_reason") != null) ? (String) req.get("cancellation_reason") :
                ((req != null && req.get("reason") != null) ? (String) req.get("reason") : "Cancelled by user");

        jdbcTemplate.update("UPDATE appointments SET status = 'CANCELLED', cancellation_reason = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", reason, aptId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Appointment cancelled successfully."));
    }

    @RequestMapping(value = {"/status", "/{id}/status"}, method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> updateAppointmentStatus(
            @PathVariable(value = "id", required = false) Long pathAptId,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestBody Map<String, Object> req
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long aptId = pathAptId;
        if (aptId == null && req.containsKey("appointment_id")) {
            aptId = ((Number) req.get("appointment_id")).longValue();
        }
        String status = (String) req.get("status");
        if (status == null) return ResponseEntity.badRequest().body(Map.of("detail", "Status is required."));

        // Fetch current status
        List<Map<String, Object>> existing = jdbcTemplate.queryForList("SELECT status FROM appointments WHERE id = ?", aptId);
        if (existing.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Appointment not found."));
        }

        String currentStatus = (String) existing.get(0).get("status");

        // Normalize status string
        String normalizedStatus = status.toUpperCase();
        if (normalizedStatus.contains("CONFIRM") || normalizedStatus.contains("SCHEDULE")) normalizedStatus = "SCHEDULED";
        else if (normalizedStatus.contains("COMPLETE")) normalizedStatus = "COMPLETED";
        else if (normalizedStatus.contains("CANCEL")) normalizedStatus = "CANCELLED";
        else if (normalizedStatus.contains("REJECT")) normalizedStatus = "CANCELLED";
        else if (normalizedStatus.contains("NO SHOW") || normalizedStatus.contains("NOSHOW")) normalizedStatus = "NO_SHOW";

        // Enforce state machine transitions
        if ("COMPLETED".equalsIgnoreCase(currentStatus) && !"COMPLETED".equalsIgnoreCase(normalizedStatus)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid status transition: Completed appointments cannot be reverted to " + normalizedStatus + "."));
        }
        if ("CANCELLED".equalsIgnoreCase(currentStatus) && !"CANCELLED".equalsIgnoreCase(normalizedStatus)) {
            return ResponseEntity.badRequest().body(Map.of("detail", "Invalid status transition: Cancelled appointments cannot be changed to " + normalizedStatus + "."));
        }

        jdbcTemplate.update("UPDATE appointments SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?", normalizedStatus, aptId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Appointment status updated to " + normalizedStatus));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyAppointments(
            @RequestParam(required = false, defaultValue = "all") String tab,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        String role = (String) claims.get("role");

        List<Map<String, Object>> rawList;
        if ("PATIENT".equalsIgnoreCase(role)) {
            rawList = jdbcTemplate.queryForList("""
                SELECT a.*, u_doc.full_name as doctor_name, s.name as speciality_name, c.name as clinic_name, c.city as clinic_city,
                       mr.id as medical_record_id, fb.id as feedback_id, fb.rating as feedback_rating
                FROM appointments a
                JOIN doctor_profiles dp ON a.doctor_id = dp.id
                JOIN users u_doc ON dp.user_id = u_doc.id
                JOIN specialities s ON dp.speciality_id = s.id
                JOIN clinics c ON a.clinic_id = c.id
                LEFT JOIN medical_records mr ON a.id = mr.appointment_id
                LEFT JOIN feedback fb ON a.id = fb.appointment_id
                WHERE a.patient_id = ?
                ORDER BY a.appointment_date DESC, a.appointment_time DESC
            """, userId);
        } else if ("DOCTOR".equalsIgnoreCase(role)) {
            List<Map<String, Object>> docs = jdbcTemplate.queryForList("SELECT id FROM doctor_profiles WHERE user_id = ?", userId);
            if (docs.isEmpty()) return ResponseEntity.ok(Map.of("appointments", List.of()));

            Long docId = ((Number) docs.get(0).get("id")).longValue();
            rawList = jdbcTemplate.queryForList("""
                SELECT a.*, u_pat.full_name as patient_name, u_pat.mobile as patient_mobile, u_pat.gender as patient_gender, u_pat.city as patient_city, c.name as clinic_name,
                       mr.id as medical_record_id, fb.id as feedback_id, fb.rating as feedback_rating
                FROM appointments a
                JOIN users u_pat ON a.patient_id = u_pat.id
                JOIN clinics c ON a.clinic_id = c.id
                LEFT JOIN medical_records mr ON a.id = mr.appointment_id
                LEFT JOIN feedback fb ON a.id = fb.appointment_id
                WHERE a.doctor_id = ?
                ORDER BY a.appointment_date DESC, a.appointment_time DESC
            """, docId);
        } else {
            rawList = List.of();
        }

        List<Map<String, Object>> formatted = new ArrayList<>();
        DateTimeFormatter time12Fmt = DateTimeFormatter.ofPattern("hh:mm a");

        for (Map<String, Object> row : rawList) {
            Map<String, Object> map = new HashMap<>(row);
            String rawStatus = (String) map.get("status");
            String displayStatus = "SCHEDULED".equalsIgnoreCase(rawStatus) ? "Confirmed" :
                    ("COMPLETED".equalsIgnoreCase(rawStatus) ? "Completed" :
                    ("CANCELLED".equalsIgnoreCase(rawStatus) ? "Cancelled" : rawStatus));

            String aptTime = (String) map.get("appointment_time");
            String time12 = aptTime;
            try {
                time12 = LocalTime.parse(aptTime).format(time12Fmt);
            } catch (Exception ignored) {}

            String mode = (String) map.get("mode");
            String consultType = "VIDEO".equalsIgnoreCase(mode) ? "Video Consult" : "In-Clinic";

            map.put("reference_id", map.get("reference_code"));
            map.put("speciality", map.get("speciality_name"));
            map.put("hospital_clinic", map.get("clinic_name"));
            map.put("consultation_type", consultType);
            map.put("status", displayStatus);
            map.put("appointment_date_formatted", formatDateIST((String) map.get("appointment_date")));
            map.put("appointment_time_12h", time12);

            formatted.add(map);
        }

        return ResponseEntity.ok(formatted);
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

