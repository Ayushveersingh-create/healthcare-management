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
@RequestMapping("/api/notifications")
@CrossOrigin(origins = "*")
public class NotificationController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("")
    public ResponseEntity<?> getNotifications(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        List<Map<String, Object>> notifications = jdbcTemplate.queryForList(
                "SELECT * FROM notifications WHERE user_id = ? ORDER BY created_at DESC", userId);

        Integer unreadCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0", Integer.class, userId);

        return ResponseEntity.ok(Map.of(
                "unread_count", unreadCount != null ? unreadCount : 0,
                "notifications", notifications
        ));
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> markAsRead(
            @PathVariable("id") Long id,
            @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        jdbcTemplate.update("UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?", id, userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "Notification marked as read."));
    }

    @RequestMapping(value = "/read-all", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<?> markAllAsRead(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        Claims claims = JwtUtil.parseToken(authHeader);
        if (claims == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));

        Long userId = claims.get("user_id", Long.class);
        jdbcTemplate.update("UPDATE notifications SET is_read = 1 WHERE user_id = ?", userId);

        return ResponseEntity.ok(Map.of("success", true, "message", "All notifications marked as read."));
    }
}
