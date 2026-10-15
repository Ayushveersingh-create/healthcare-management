package com.sehatsetu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("AuthController Integration Tests")
@SuppressWarnings("null")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /api/auth/demo-login - Patient demo login without password succeeds")
    void testPatientDemoLogin() throws Exception {
        Map<String, String> body = Map.of("role", "PATIENT");

        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.user.role", is("PATIENT")))
                .andExpect(jsonPath("$.user.email", is("aarav.singh@gmail.com")));
    }

    @Test
    @DisplayName("POST /api/auth/demo-login - Doctor login without password is required and returns 401")
    void testDoctorDemoLoginRequiresPassword() throws Exception {
        Map<String, String> body = Map.of("role", "DOCTOR");

        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Password required")));
    }

    @Test
    @DisplayName("POST /api/auth/demo-login - Doctor login with correct password succeeds")
    void testDoctorDemoLoginSuccess() throws Exception {
        Map<String, String> body = Map.of("role", "DOCTOR", "password", "Doctor@123");

        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.user.role", is("DOCTOR")))
                .andExpect(jsonPath("$.user.email", is("ananya.sharma@sehatsetu.in")));
    }

    @Test
    @DisplayName("POST /api/auth/demo-login - Admin login with correct password succeeds")
    void testAdminDemoLoginSuccess() throws Exception {
        Map<String, String> body = Map.of("role", "ADMIN", "password", "Admin@123");

        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andExpect(jsonPath("$.user.role", is("ADMIN")))
                .andExpect(jsonPath("$.user.email", is("admin@sehatsetu.in")));
    }

    @Test
    @DisplayName("POST /api/auth/demo-login - Invalid role returns 400")
    void testDemoLoginInvalidRole() throws Exception {
        Map<String, String> body = Map.of("role", "SUPERUSER");

        mockMvc.perform(post("/api/auth/demo-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Invalid demo role")));
    }

    @Test
    @DisplayName("POST /api/auth/login - Valid credentials return access token")
    void testStandardLoginSuccess() throws Exception {
        Map<String, String> body = Map.of("email", "aarav.singh@gmail.com", "password", "Patient@123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /api/auth/login - Wrong password returns 401")
    void testStandardLoginWrongPassword() throws Exception {
        Map<String, String> body = Map.of("email", "aarav.singh@gmail.com", "password", "WrongPass@999");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Invalid email or password")));
    }

    @Test
    @DisplayName("POST /api/auth/register - Register new patient successfully and login with me endpoint")
    void testRegisterNewPatientAndGetMe() throws Exception {
        String uniqueEmail = "patient." + UUID.randomUUID().toString().substring(0, 8) + "@test.in";
        Map<String, Object> regBody = Map.of(
                "email", uniqueEmail,
                "password", "SecurePatient@123",
                "full_name", "Devika Nambiar",
                "mobile", "+91 91234 56789",
                "city", "Bengaluru",
                "dob", "1994-07-15",
                "gender", "Female",
                "blood_group", "O+",
                "address", "Indiranagar, Bengaluru, Karnataka",
                "emergency_contact", "+91 98765 00000"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.access_token").isNotEmpty())
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        Map<?, ?> map = objectMapper.readValue(responseJson, Map.class);
        String token = (String) map.get("access_token");

        // Verify /api/auth/me with the generated token
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(uniqueEmail)))
                .andExpect(jsonPath("$.full_name", is("Devika Nambiar")))
                .andExpect(jsonPath("$.role", is("PATIENT")))
                .andExpect(jsonPath("$.patient_profile").exists());
    }

    @Test
    @DisplayName("POST /api/auth/register - Duplicate email returns 400")
    void testRegisterDuplicateEmail() throws Exception {
        Map<String, Object> regBody = Map.of(
                "email", "aarav.singh@gmail.com",
                "password", "SomePass@123",
                "full_name", "Duplicate User"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("already registered")));
    }

    @Test
    @DisplayName("GET /api/auth/me - Without token returns 401")
    void testGetMeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail", containsString("Invalid or expired")));
    }
}
