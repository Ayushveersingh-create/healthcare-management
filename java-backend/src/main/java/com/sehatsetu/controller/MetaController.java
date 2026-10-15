package com.sehatsetu.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class MetaController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/config")
    public ResponseEntity<?> getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("platform_name", "SehatSetu");
        config.put("tagline", "Better Care. Simpler Healthcare.");
        config.put("currency", "₹");
        config.put("currency_code", "INR");
        config.put("timezone", "Asia/Kolkata (IST)");
        config.put("support_phone", "+91 1800 202 7788");
        config.put("version", "2.0");

        return ResponseEntity.ok(config);
    }

    @GetMapping({"/specialities", "/meta/specialities"})
    public ResponseEntity<?> getSpecialities() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT id, name, icon, description FROM specialities ORDER BY name ASC");
        Map<String, Object> response = new HashMap<>();
        response.put("specialities", list);
        response.put("count", list.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping({"/clinics", "/meta/clinics"})
    public ResponseEntity<?> getClinics() {
        List<Map<String, Object>> list = jdbcTemplate.queryForList("SELECT id, name, city, address, phone, email FROM clinics ORDER BY name ASC");
        Map<String, Object> response = new HashMap<>();
        response.put("clinics", list);
        response.put("count", list.size());
        return ResponseEntity.ok(response);
    }
}
