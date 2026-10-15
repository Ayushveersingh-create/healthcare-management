package com.sehatsetu.controller;

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
@DisplayName("NotificationController Integration Tests")
@SuppressWarnings("null")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Notification Flow: Get -> Mark Single as Read -> Mark All Read")
    void testNotificationFlow() throws Exception {
        List<Map<String, Object>> users = jdbcTemplate.queryForList("SELECT id, email FROM users LIMIT 1");
        Long userId = ((Number) users.get(0).get("id")).longValue();
        String email = (String) users.get(0).get("email");
        String token = JwtUtil.generateToken(userId, email, "PATIENT");

        // Insert test notifications
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read) VALUES (?, 'Test Notification 1', 'Message 1', 'SYSTEM', 0)", userId);
        Long notifId = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read) VALUES (?, 'Test Notification 2', 'Message 2', 'SYSTEM', 0)", userId);

        // Fetch notifications
        mockMvc.perform(get("/api/notifications")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread_count", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.notifications", isA(List.class)));

        // Mark single as read
        mockMvc.perform(post("/api/notifications/{id}/read", notifId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("Notification marked as read")));

        // Mark all as read
        mockMvc.perform(post("/api/notifications/read-all")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.message", containsString("All notifications marked as read")));
    }

    @Test
    @DisplayName("GET /api/notifications - Unauthorized without token")
    void testGetNotificationsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
