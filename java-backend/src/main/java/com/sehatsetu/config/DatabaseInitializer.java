package com.sehatsetu.config;

import com.sehatsetu.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.File;

@Component
@SuppressWarnings("null")
public class DatabaseInitializer implements CommandLineRunner {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) throws Exception {
        File dataDir = new File("data");
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        // Configure WAL mode and foreign keys
        jdbcTemplate.execute("PRAGMA journal_mode=WAL;");
        jdbcTemplate.execute("PRAGMA foreign_keys=ON;");

        // Create Tables
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                role TEXT CHECK(role IN ('PATIENT', 'DOCTOR', 'ADMIN')) NOT NULL,
                full_name TEXT NOT NULL,
                mobile TEXT,
                city TEXT,
                dob TEXT,
                gender TEXT,
                blood_group TEXT,
                address TEXT,
                emergency_contact TEXT,
                status TEXT CHECK(status IN ('ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION')) DEFAULT 'ACTIVE',
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS specialities (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT UNIQUE NOT NULL,
                icon TEXT,
                description TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS clinics (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                city TEXT NOT NULL,
                address TEXT NOT NULL,
                phone TEXT,
                email TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS doctor_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER UNIQUE NOT NULL,
                speciality_id INTEGER NOT NULL,
                clinic_id INTEGER NOT NULL,
                qualifications TEXT NOT NULL,
                experience_years INTEGER NOT NULL,
                consultation_fee REAL NOT NULL,
                about TEXT,
                registration_number TEXT UNIQUE NOT NULL,
                verification_status TEXT CHECK(verification_status IN ('VERIFIED', 'PENDING', 'REJECTED')) DEFAULT 'VERIFIED',
                avg_rating REAL DEFAULT 5.0,
                total_reviews INTEGER DEFAULT 0,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (speciality_id) REFERENCES specialities(id),
                FOREIGN KEY (clinic_id) REFERENCES clinics(id)
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS doctor_schedules (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doctor_id INTEGER NOT NULL,
                day_of_week INTEGER CHECK(day_of_week BETWEEN 0 AND 6) NOT NULL,
                start_time TEXT NOT NULL,
                end_time TEXT NOT NULL,
                slot_duration_mins INTEGER DEFAULT 30,
                break_start TEXT,
                break_end TEXT,
                is_available INTEGER DEFAULT 1,
                FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id) ON DELETE CASCADE,
                UNIQUE(doctor_id, day_of_week)
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS doctor_blocked_dates (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                doctor_id INTEGER NOT NULL,
                blocked_date TEXT NOT NULL,
                reason TEXT,
                FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id) ON DELETE CASCADE,
                UNIQUE(doctor_id, blocked_date)
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS patient_profiles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER UNIQUE NOT NULL,
                blood_group TEXT,
                allergies TEXT,
                chronic_conditions TEXT,
                current_medications TEXT,
                height_cm REAL,
                weight_kg REAL,
                bp_systolic INTEGER,
                bp_diastolic INTEGER,
                pulse_rate INTEGER,
                temperature_f REAL,
                spo2_percent INTEGER,
                emergency_name TEXT,
                emergency_phone TEXT,
                emergency_relation TEXT,
                vitals_updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        String[] alterPatientCols = {
            "ALTER TABLE patient_profiles ADD COLUMN current_medications TEXT;",
            "ALTER TABLE patient_profiles ADD COLUMN height_cm REAL;",
            "ALTER TABLE patient_profiles ADD COLUMN weight_kg REAL;",
            "ALTER TABLE patient_profiles ADD COLUMN bp_systolic INTEGER;",
            "ALTER TABLE patient_profiles ADD COLUMN bp_diastolic INTEGER;",
            "ALTER TABLE patient_profiles ADD COLUMN pulse_rate INTEGER;",
            "ALTER TABLE patient_profiles ADD COLUMN temperature_f REAL;",
            "ALTER TABLE patient_profiles ADD COLUMN spo2_percent INTEGER;",
            "ALTER TABLE patient_profiles ADD COLUMN emergency_name TEXT;",
            "ALTER TABLE patient_profiles ADD COLUMN emergency_relation TEXT;",
            "ALTER TABLE patient_profiles ADD COLUMN vitals_updated_at TIMESTAMP;"
        };
        for (String sql : alterPatientCols) {
            try { jdbcTemplate.execute(sql); } catch (Exception ignored) {}
        }

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS appointments (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                reference_code TEXT UNIQUE NOT NULL,
                patient_id INTEGER NOT NULL,
                doctor_id INTEGER NOT NULL,
                clinic_id INTEGER NOT NULL,
                appointment_date TEXT NOT NULL,
                appointment_time TEXT NOT NULL,
                mode TEXT CHECK(mode IN ('IN_CLINIC', 'VIDEO')) NOT NULL,
                status TEXT CHECK(status IN ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')) DEFAULT 'SCHEDULED',
                reason_for_visit TEXT,
                cancellation_reason TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (patient_id) REFERENCES users(id),
                FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id),
                FOREIGN KEY (clinic_id) REFERENCES clinics(id)
            );
        """);

        jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_unique_active_doctor_slot ON appointments(doctor_id, appointment_date, appointment_time) WHERE status = 'SCHEDULED';");

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS medical_records (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                appointment_id INTEGER UNIQUE NOT NULL,
                patient_id INTEGER NOT NULL,
                doctor_id INTEGER NOT NULL,
                diagnosis_title TEXT NOT NULL,
                clinical_notes TEXT NOT NULL,
                prescriptions TEXT,
                record_type TEXT DEFAULT 'Consultation',
                follow_up_date TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (appointment_id) REFERENCES appointments(id),
                FOREIGN KEY (patient_id) REFERENCES users(id),
                FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id)
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                appointment_id INTEGER UNIQUE NOT NULL,
                patient_id INTEGER NOT NULL,
                doctor_id INTEGER NOT NULL,
                rating INTEGER CHECK(rating BETWEEN 1 AND 5) NOT NULL,
                comment TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (appointment_id) REFERENCES appointments(id),
                FOREIGN KEY (patient_id) REFERENCES users(id),
                FOREIGN KEY (doctor_id) REFERENCES doctor_profiles(id)
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS notifications (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                message TEXT NOT NULL,
                notification_type TEXT DEFAULT 'SYSTEM',
                target_url TEXT,
                is_read INTEGER DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS audit_logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER,
                action TEXT NOT NULL,
                details TEXT,
                ip_address TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS system_settings (
                key TEXT PRIMARY KEY,
                value TEXT NOT NULL,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
        """);

        // Seed Demo Users if empty or missing
        Integer patientCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'aarav.singh@gmail.com'", Integer.class);
        if (patientCount == null || patientCount == 0) {
            seedInitialData();
        } else {
            seedSpecialitiesAndClinics();
        }
    }

    private void seedSpecialitiesAndClinics() {
        // ── Specialities (id 1-11) ──
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "General Physician", "🩺", "Primary care and routine wellness consultations");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Cardiology", "❤️", "Heart health, ECG, hypertension & cardiovascular care");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Dermatology", "✨", "Skin, hair, nails, acne and allergy treatments");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Pediatrics", "👶", "Child health, growth monitoring & vaccinations");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Orthopedics", "🦴", "Bone, joint, spine and sports injury treatments");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Neurology", "🧠", "Brain, nerve disorders, migraine, epilepsy & stroke management");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Gynecology & Obstetrics", "🤰", "Women's health, maternity care, PCOS & pregnancy guidance");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "ENT Specialist", "👂", "Ear, nose, throat, sinusitis & hearing health care");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Ophthalmology", "👁️", "Eye care, vision testing, cataract & retina consultations");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Psychiatry & Wellness", "🧘", "Mental health, stress, anxiety, depression & therapy");
        jdbcTemplate.update("INSERT OR IGNORE INTO specialities (name, icon, description) VALUES (?, ?, ?)", "Gastroenterology", "🩺", "Digestive health, liver care, acidity & stomach disorders");

        // ── Clinics (id 1-5) ──
        jdbcTemplate.update("INSERT OR IGNORE INTO clinics (name, city, address, phone, email) VALUES (?, ?, ?, ?, ?)",
                "SehatSetu Care Clinic - Sector 62", "Noida", "B-12, Block B, Industrial Area, Sector 62, Noida, UP 201309", "+91 120 456 7890", "noida@sehatsetu.in");
        jdbcTemplate.update("INSERT OR IGNORE INTO clinics (name, city, address, phone, email) VALUES (?, ?, ?, ?, ?)",
                "SehatSetu Specialty Hospital - Saket", "New Delhi", "Press Enclave Marg, Saket District Centre, Sector 6, Pushp Vihar, New Delhi 110017", "+91 11 2651 8888", "saket@sehatsetu.in");
        jdbcTemplate.update("INSERT OR IGNORE INTO clinics (name, city, address, phone, email) VALUES (?, ?, ?, ?, ?)",
                "SehatSetu Multi-Speciality Centre - Indiranagar", "Bengaluru", "100 Feet Road, Indiranagar, Bengaluru, Karnataka 560038", "+91 80 4567 8901", "blr@sehatsetu.in");
        jdbcTemplate.update("INSERT OR IGNORE INTO clinics (name, city, address, phone, email) VALUES (?, ?, ?, ?, ?)",
                "SehatSetu Super Speciality Hospital - Bandra West", "Mumbai", "Hill Road, Bandra West, Mumbai, Maharashtra 400050", "+91 22 6789 0123", "mumbai@sehatsetu.in");
        jdbcTemplate.update("INSERT OR IGNORE INTO clinics (name, city, address, phone, email) VALUES (?, ?, ?, ?, ?)",
                "SehatSetu Health Hub - Hitech City", "Hyderabad", "Cyber Towers Rd, Hitech City, Hyderabad, Telangana 500081", "+91 40 5678 9012", "hyd@sehatsetu.in");
    }

    private Long createUser(String email, String passwordHash, String role, String fullName, String mobile, String city, String dob, String gender, String bloodGroup, String address, String emergencyContact) {
        jdbcTemplate.update("INSERT INTO users (email, password_hash, role, full_name, mobile, city, dob, gender, blood_group, address, emergency_contact, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                email, passwordHash, role, fullName, mobile, city, dob, gender, bloodGroup, address, emergencyContact);
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    private void createPatientProfile(Long userId, String bloodGroup, String allergies, String chronicConditions, String currentMedications,
                                      Double heightCm, Double weightKg, Integer bpSys, Integer bpDia, Integer pulseRate, Double tempF, Integer spo2Percent,
                                      String emergencyName, String emergencyPhone, String emergencyRelation) {
        jdbcTemplate.update("""
            INSERT INTO patient_profiles (user_id, blood_group, allergies, chronic_conditions, current_medications,
                                          height_cm, weight_kg, bp_systolic, bp_diastolic, pulse_rate, temperature_f, spo2_percent,
                                          emergency_name, emergency_phone, emergency_relation, vitals_updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
            ON CONFLICT(user_id) DO UPDATE SET
                blood_group = excluded.blood_group,
                allergies = excluded.allergies,
                chronic_conditions = excluded.chronic_conditions,
                current_medications = excluded.current_medications,
                height_cm = excluded.height_cm,
                weight_kg = excluded.weight_kg,
                bp_systolic = excluded.bp_systolic,
                bp_diastolic = excluded.bp_diastolic,
                pulse_rate = excluded.pulse_rate,
                temperature_f = excluded.temperature_f,
                spo2_percent = excluded.spo2_percent,
                emergency_name = excluded.emergency_name,
                emergency_phone = excluded.emergency_phone,
                emergency_relation = excluded.emergency_relation,
                vitals_updated_at = CURRENT_TIMESTAMP
        """, userId, bloodGroup, allergies, chronicConditions, currentMedications,
             heightCm, weightKg, bpSys, bpDia, pulseRate, tempF, spo2Percent,
             emergencyName, emergencyPhone, emergencyRelation);
    }

    private void seedInitialData() {
        seedSpecialitiesAndClinics();
        String patientPw = PasswordUtil.hashPassword("Patient@123");
        String doctorPw = PasswordUtil.hashPassword("Doctor@123");
        String adminPw = PasswordUtil.hashPassword("Admin@123");

        // ── Admin ──
        Long adminUserId = createUser("admin@sehatsetu.in", adminPw, "ADMIN", "Rajesh K. Sharma", "+91 98100 11223", "New Delhi", null, null, null, null, null);

        Long[] docUserIds = new Long[13];
        Long[] docProfileIds = new Long[13];

        // ── Doctor 1 — Dr. Ananya Sharma (General Physician) ──
        docUserIds[1] = createUser("ananya.sharma@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Ananya Sharma", "+91 98765 43210", "Noida", "1985-04-12", "Female", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 1, 1, ?, 12, 600.0, ?, ?, 'VERIFIED', 4.9, 128)",
                docUserIds[1], "MBBS, MD (General Medicine) - AIIMS New Delhi",
                "Senior General Physician with expertise in preventive health and chronic disease management. Specialist in diabetes, hypertension and lifestyle disorders.",
                "DMC/R/14298");
        docProfileIds[1] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 2 — Dr. Vikram Patel (Cardiology) ──
        docUserIds[2] = createUser("vikram.patel@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Vikram Patel", "+91 98765 43211", "New Delhi", "1978-09-22", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 2, 2, ?, 18, 1200.0, ?, ?, 'VERIFIED', 4.8, 215)",
                docUserIds[2], "MBBS, DM (Cardiology) - AIIMS, Fellowship in Interventional Cardiology - Mount Sinai, USA",
                "Leading interventional cardiologist with 18+ years of experience. Performed over 3000 angioplasties. Expert in complex coronary interventions and structural heart disease.",
                "DMC/R/08741");
        docProfileIds[2] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 3 — Dr. Priya Menon (Dermatology) ──
        docUserIds[3] = createUser("priya.menon@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Priya Menon", "+91 98765 43212", "Bengaluru", "1982-01-15", "Female", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 3, 3, ?, 14, 800.0, ?, ?, 'VERIFIED', 4.7, 97)",
                docUserIds[3], "MBBS, MD (Dermatology) - KMC Manipal, Fellowship in Cosmetic Dermatology",
                "Renowned dermatologist specializing in acne, eczema, psoriasis and cosmetic procedures including laser treatments and chemical peels.",
                "KMC/R/21567");
        docProfileIds[3] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 4 — Dr. Suresh Iyer (Pediatrics) ──
        docUserIds[4] = createUser("suresh.iyer@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Suresh Iyer", "+91 98765 43213", "Noida", "1980-06-30", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 4, 1, ?, 16, 700.0, ?, ?, 'VERIFIED', 4.85, 186)",
                docUserIds[4], "MBBS, MD (Pediatrics) - Maulana Azad Medical College, DCH",
                "Experienced pediatrician with special interest in neonatology and childhood nutrition. Trusted by over 5000 families in Delhi-NCR region.",
                "DMC/R/11034");
        docProfileIds[4] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 5 — Dr. Kavitha Reddy (Orthopedics) ──
        docUserIds[5] = createUser("kavitha.reddy@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Kavitha Reddy", "+91 98765 43214", "New Delhi", "1983-11-08", "Female", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 5, 2, ?, 13, 900.0, ?, ?, 'VERIFIED', 4.6, 74)",
                docUserIds[5], "MBBS, MS (Orthopedics) - Safdarjung Hospital, Fellowship in Joint Replacement",
                "Orthopedic surgeon specializing in knee and hip replacements, sports injuries, and arthroscopic surgeries. Over 1500 successful joint replacements.",
                "DMC/R/16890");
        docProfileIds[5] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 6 — Dr. Rohan Verma (General Physician) ──
        docUserIds[6] = createUser("rohan.verma@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Rohan Verma", "+91 98765 43215", "Noida", "1987-05-18", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 1, 1, ?, 10, 500.0, ?, ?, 'VERIFIED', 4.8, 92)",
                docUserIds[6], "MBBS, MD (Internal Medicine) - AIIMS New Delhi",
                "Dedicated General Physician specializing in diabetes care, hypertension, and general wellness consultations.",
                "DMC/R/19542");
        docProfileIds[6] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 7 — Dr. Siddharth Malhotra (Neurology) ──
        docUserIds[7] = createUser("siddharth.malhotra@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Siddharth Malhotra", "+91 98765 43216", "Mumbai", "1976-11-20", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 6, 4, ?, 20, 1500.0, ?, ?, 'VERIFIED', 4.95, 310)",
                docUserIds[7], "MBBS, DM (Neurology) - NIMHANS Bengaluru, Fellowship in Stroke & Epilepsy",
                "Chief Neurologist with 20+ years of clinical excellence. Expert in stroke rehabilitation, migraine protocols, and Parkinson's management.",
                "MMC/R/04112");
        docProfileIds[7] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 8 — Dr. Sunita Deshmukh (Gynecology & Obstetrics) ──
        docUserIds[8] = createUser("sunita.deshmukh@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Sunita Deshmukh", "+91 98765 43217", "Bengaluru", "1981-08-14", "Female", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 7, 3, ?, 15, 900.0, ?, ?, 'VERIFIED', 4.88, 175)",
                docUserIds[8], "MBBS, MS (Obstetrics & Gynecology) - KEM Hospital Mumbai, DNB",
                "Senior Gynecologist and High-Risk Pregnancy Specialist. Passionate about women's hormonal wellness, PCOS recovery, and prenatal care.",
                "KMC/R/31209");
        docProfileIds[8] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 9 — Dr. Rajesh Natarajan (ENT Specialist) ──
        docUserIds[9] = createUser("rajesh.natarajan@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Rajesh Natarajan", "+91 98765 43218", "New Delhi", "1984-03-09", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 8, 2, ?, 13, 750.0, ?, ?, 'VERIFIED', 4.75, 114)",
                docUserIds[9], "MBBS, MS (ENT) - Madras Medical College, Fellowship in Endoscopic Sinus Surgery",
                "Leading ENT Surgeon specializing in chronic sinusitis, allergic rhinitis, snoring/sleep apnea treatments, and hearing loss disorders.",
                "DMC/R/22891");
        docProfileIds[9] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 10 — Dr. Meenakshi Sundaram (Ophthalmology) ──
        docUserIds[10] = createUser("meenakshi.sundaram@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Meenakshi Sundaram", "+91 98765 43219", "Hyderabad", "1986-12-05", "Female", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 9, 5, ?, 11, 650.0, ?, ?, 'VERIFIED', 4.9, 142)",
                docUserIds[10], "MBBS, MS (Ophthalmology) - LV Prasad Eye Institute, FRCS",
                "Cornea and Refractive Eye Surgeon. Performed over 2500 LASIK surgeries and micro-incision cataract procedures.",
                "TSMC/R/18430");
        docProfileIds[10] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 11 — Dr. Alok Chatterjee (Psychiatry & Wellness) ──
        docUserIds[11] = createUser("alok.chatterjee@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Alok Chatterjee", "+91 98765 43220", "New Delhi", "1979-02-17", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 10, 2, ?, 17, 1000.0, ?, ?, 'VERIFIED', 4.92, 168)",
                docUserIds[11], "MBBS, MD (Psychiatry) - CIP Ranchi, MRCPsych (UK)",
                "Senior Neuro-Psychiatrist specializing in stress management, anxiety, corporate burnout, depression, and cognitive behavioral therapy.",
                "DMC/R/09321");
        docProfileIds[11] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor 12 — Dr. Tariq Khan (Gastroenterology) ──
        docUserIds[12] = createUser("tariq.khan@sehatsetu.in", doctorPw, "DOCTOR", "Dr. Tariq Khan", "+91 98765 43221", "Hyderabad", "1982-07-25", "Male", null, null, null);
        jdbcTemplate.update("INSERT INTO doctor_profiles (user_id, speciality_id, clinic_id, qualifications, experience_years, consultation_fee, about, registration_number, verification_status, avg_rating, total_reviews) VALUES (?, 11, 5, ?, 14, 1100.0, ?, ?, 'VERIFIED', 4.82, 126)",
                docUserIds[12], "MBBS, DM (Gastroenterology) - Osmania Medical College, Fellowship in Advanced Endoscopy",
                "Senior Gastroenterologist and Hepatologist specializing in IBS, fatty liver, acid reflux, and endoscopic procedures.",
                "TSMC/R/15704");
        docProfileIds[12] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Doctor Schedules (Sun-Sat 09:00 - 17:00 for all 12 doctors) ──
        for (int docIndex = 1; docIndex <= 12; docIndex++) {
            Long docProfileId = docProfileIds[docIndex];
            for (int day = 0; day <= 6; day++) {
                String startTime = (docIndex % 2 == 0) ? "10:00" : "09:00";
                String endTime = (docIndex % 2 == 0) ? "18:00" : "17:00";
                jdbcTemplate.update("INSERT INTO doctor_schedules (doctor_id, day_of_week, start_time, end_time, slot_duration_mins, break_start, break_end, is_available) VALUES (?, ?, ?, ?, 30, '13:00', '14:00', 1)",
                        docProfileId, day, startTime, endTime);
            }
        }

        Long[] patientUserIds = new Long[21];

        // ── Patient 1 — Aarav Singh ──
        patientUserIds[1] = createUser("aarav.singh@gmail.com", patientPw, "PATIENT", "Aarav Singh", "+91 98234 56789", "Noida", "1992-08-15", "Male", "B+", "Flat 402, Lotus Boulevard, Sector 100, Noida", "+91 98234 56700");
        createPatientProfile(patientUserIds[1], "B+", "Penicillin", "None", "Tab Paracetamol 650mg SOS", 178.0, 75.0, 120, 80, 72, 98.6, 99, "Sunita Singh", "+91 98234 56700", "Spouse");

        // ── Patient 2 — Meera Joshi ──
        patientUserIds[2] = createUser("meera.joshi@gmail.com", patientPw, "PATIENT", "Meera Joshi", "+91 99887 76655", "New Delhi", "1988-03-22", "Female", "A+", "C-14, Vasant Kunj, New Delhi 110070", "+91 99887 76600");
        createPatientProfile(patientUserIds[2], "A+", "Sulfa Drugs", "Hypothyroidism", "Tab Thyronorm 50mcg", 162.0, 60.0, 118, 76, 76, 98.4, 98, "Ramesh Joshi", "+91 99887 76600", "Father");

        // ── Patient 3 — Rohit Kapoor ──
        patientUserIds[3] = createUser("rohit.kapoor@gmail.com", patientPw, "PATIENT", "Rohit Kapoor", "+91 97123 45678", "Bengaluru", "1995-12-01", "Male", "O+", "1st Cross, 5th Block, Koramangala, Bengaluru 560095", "+91 97123 45600");
        createPatientProfile(patientUserIds[3], "O+", "Dust Allergy", "Mild Asthma", "Inhaler Asthalin SOS, Tab Montelukast 10mg", 175.0, 72.0, 124, 82, 78, 98.6, 97, "Priya Kapoor", "+91 97123 45600", "Spouse");

        // ── Patient 4 — Priyanka Verma ──
        patientUserIds[4] = createUser("priyanka.verma@example.in", patientPw, "PATIENT", "Priyanka Verma", "+91 98765 43210", "Gurugram", "1996-03-22", "Female", "B+", "Sector 43, Gurugram, Haryana", "+91 98765 00000");
        createPatientProfile(patientUserIds[4], "B+", "Dust Allergy", "PCOS", "Tab Metformin 500mg, Myo-Inositol", 165.0, 58.0, 116, 74, 70, 98.2, 99, "Suresh Verma", "+91 98765 00000", "Brother");

        // ── Patient 5 — Kabir Mehta ──
        patientUserIds[5] = createUser("kabir.mehta@gmail.com", patientPw, "PATIENT", "Kabir Mehta", "+91 98112 23344", "Mumbai", "1990-10-10", "Male", "O-", "Worli Sea Face, Bandra, Mumbai", "+91 98112 00000");
        createPatientProfile(patientUserIds[5], "O-", "Peanuts", "Hypertension", "Tab Telmisartan 40mg", 180.0, 85.0, 138, 88, 82, 98.6, 98, "Neha Mehta", "+91 98112 00000", "Spouse");

        // ── Patient 6 — Ananya Roy ──
        patientUserIds[6] = createUser("ananya.roy@gmail.com", patientPw, "PATIENT", "Ananya Roy", "+91 98300 11223", "New Delhi", "1994-06-14", "Female", "AB+", "Greater Kailash 1, New Delhi", "+91 98300 00000");
        createPatientProfile(patientUserIds[6], "AB+", "Latex", "Migraine", "Tab Naproxen 250mg SOS", 160.0, 55.0, 112, 72, 74, 98.5, 99, "Debashish Roy", "+91 98300 00000", "Father");

        // ── Patient 7 — Vikramaditya Rao ──
        patientUserIds[7] = createUser("vikram.rao@gmail.com", patientPw, "PATIENT", "Vikramaditya Rao", "+91 98490 12345", "Hyderabad", "1981-11-05", "Male", "A+", "Jubilee Hills, Hyderabad", "+91 98490 00000");
        createPatientProfile(patientUserIds[7], "A+", "Aspirin", "Type 2 Diabetes", "Tab Teneligliptin 20mg", 172.0, 81.0, 130, 84, 80, 98.6, 98, "Lakshmi Rao", "+91 98490 00000", "Spouse");

        // ── Patient 8 — Sneha Kulkarni ──
        patientUserIds[8] = createUser("sneha.kulkarni@gmail.com", patientPw, "PATIENT", "Sneha Kulkarni", "+91 98200 98765", "Mumbai", "1997-04-18", "Female", "O+", "Dadar West, Mumbai", "+91 98200 00000");
        createPatientProfile(patientUserIds[8], "O+", "Pollen", "GERD / Acidity", "Cap Pantoprazole 40mg", 158.0, 52.0, 110, 70, 72, 98.4, 99, "Anil Kulkarni", "+91 98200 00000", "Father");

        // ── Patient 9 — Tarun Bhatia ──
        patientUserIds[9] = createUser("tarun.bhatia@gmail.com", patientPw, "PATIENT", "Tarun Bhatia", "+91 98101 23456", "Noida", "1974-09-30", "Male", "B-", "Sector 50, Noida", "+91 98101 00000");
        createPatientProfile(patientUserIds[9], "B-", "NSAIDs", "Osteoarthritis", "Tab Glucosamine 500mg", 170.0, 78.0, 128, 82, 75, 98.6, 97, "Seema Bhatia", "+91 98101 00000", "Spouse");

        // ── Patient 10 — Deepa Nair ──
        patientUserIds[10] = createUser("deepa.nair@gmail.com", patientPw, "PATIENT", "Deepa Nair", "+91 98470 11223", "Bengaluru", "1985-02-14", "Female", "AB-", "Whitefield, Bengaluru", "+91 98470 00000");
        createPatientProfile(patientUserIds[10], "AB-", "None", "Cervical Spondylosis", "Cap Calcium + D3", 164.0, 63.0, 122, 78, 76, 98.5, 98, "Unni Nair", "+91 98470 00000", "Spouse");

        // ── Patient 11 — Aditya Sengupta ──
        patientUserIds[11] = createUser("aditya.sengupta@gmail.com", patientPw, "PATIENT", "Aditya Sengupta", "+91 98310 55667", "New Delhi", "1991-07-20", "Male", "A-", "CR Park, New Delhi", "+91 98310 00000");
        createPatientProfile(patientUserIds[11], "A-", "Shellfish", "High Cholesterol", "Tab Rosuvastatin 10mg", 176.0, 76.0, 126, 80, 74, 98.6, 99, "Mousumi Sengupta", "+91 98310 00000", "Mother");

        // ── Patient 12 — Pooja Agarwal ──
        patientUserIds[12] = createUser("pooja.agarwal@gmail.com", patientPw, "PATIENT", "Pooja Agarwal", "+91 98290 33445", "Noida", "1998-12-10", "Female", "B+", "Sector 137, Noida", "+91 98290 00000");
        createPatientProfile(patientUserIds[12], "B+", "Nickel", "Atopic Eczema", "Cream Hydrocortisone 1%", 161.0, 54.0, 114, 72, 71, 98.3, 99, "Rajesh Agarwal", "+91 98290 00000", "Father");

        // ── Patient 13 — Manjunath Swamy ──
        patientUserIds[13] = createUser("manjunath.swamy@gmail.com", patientPw, "PATIENT", "Manjunath Swamy", "+91 98800 44556", "Bengaluru", "1978-05-04", "Male", "O+", "HSR Layout, Bengaluru", "+91 98800 00000");
        createPatientProfile(patientUserIds[13], "O+", "None", "Grade 1 Fatty Liver", "Cap Vitamin E 400mg", 169.0, 82.0, 132, 86, 79, 98.6, 98, "Savitri Swamy", "+91 98800 00000", "Spouse");

        // ── Patient 14 — Ritu Saxena ──
        patientUserIds[14] = createUser("ritu.saxena@gmail.com", patientPw, "PATIENT", "Ritu Saxena", "+91 98390 66778", "New Delhi", "1992-01-28", "Female", "A+", "Civil Lines, New Delhi", "+91 98390 00000");
        createPatientProfile(patientUserIds[14], "A+", "Dust Mites", "Chronic Sinusitis", "Nasal Spray Fluticasone", 163.0, 57.0, 116, 75, 73, 98.5, 99, "Alok Saxena", "+91 98390 00000", "Spouse");

        // ── Patient 15 — Harshvardhan Jain ──
        patientUserIds[15] = createUser("harsh.jain@gmail.com", patientPw, "PATIENT", "Harshvardhan Jain", "+91 98250 88990", "Mumbai", "1971-08-12", "Male", "B+", "Malabar Hill, Mumbai", "+91 98250 00000");
        createPatientProfile(patientUserIds[15], "B+", "Iodine Contrast", "Coronary Artery Disease", "Tab Clopidogrel 75mg, Tab Atorvastatin 20mg", 174.0, 80.0, 135, 85, 68, 98.4, 97, "Sangeeta Jain", "+91 98250 00000", "Spouse");

        // ── Patient 16 — Divya Pillai ──
        patientUserIds[16] = createUser("divya.pillai@gmail.com", patientPw, "PATIENT", "Divya Pillai", "+91 98400 11223", "Bengaluru", "2000-06-25", "Female", "O+", "Indiranagar, Bengaluru", "+91 98400 00000");
        createPatientProfile(patientUserIds[16], "O+", "Iron Supplements", "Anemia & PCOS", "Syrup Dexorange", 159.0, 50.0, 108, 68, 78, 98.6, 99, "Ramesh Pillai", "+91 98400 00000", "Father");

        // ── Patient 17 — Samarpreet Singh ──
        patientUserIds[17] = createUser("samar.singh@gmail.com", patientPw, "PATIENT", "Samarpreet Singh", "+91 98140 22334", "Noida", "1987-03-15", "Male", "B+", "Sector 93B, Noida", "+91 98140 00000");
        createPatientProfile(patientUserIds[17], "B+", "None", "Lumbar Disc Herniation", "Cap Pregabalin 75mg", 182.0, 88.0, 125, 80, 75, 98.6, 98, "Gurpreet Kaur", "+91 98140 00000", "Spouse");

        // ── Patient 18 — Kavya Nambiar ──
        patientUserIds[18] = createUser("kavya.nambiar@gmail.com", patientPw, "PATIENT", "Kavya Nambiar", "+91 98460 33445", "Hyderabad", "1993-10-08", "Female", "AB+", "Banjara Hills, Hyderabad", "+91 98460 00000");
        createPatientProfile(patientUserIds[18], "AB+", "Synthetic Fragrances", "Allergic Dermatitis", "Cream Tacrolimus 0.1%", 166.0, 59.0, 115, 72, 72, 98.5, 99, "Suresh Nambiar", "+91 98460 00000", "Brother");

        // ── Patient 19 — Nikhil Chaudhari ──
        patientUserIds[19] = createUser("nikhil.c@gmail.com", patientPw, "PATIENT", "Nikhil Chaudhari", "+91 98220 55667", "Mumbai", "1984-11-30", "Male", "A+", "Andheri West, Mumbai", "+91 98220 00000");
        createPatientProfile(patientUserIds[19], "A+", "Coffee", "Stress Insomnia", "Tab Melatonin 3mg", 173.0, 74.0, 124, 82, 76, 98.6, 98, "Pooja Chaudhari", "+91 98220 00000", "Spouse");

        // ── Patient 20 — Sunayana Das ──
        patientUserIds[20] = createUser("sunayana.das@gmail.com", patientPw, "PATIENT", "Sunayana Das", "+91 98610 77889", "New Delhi", "1996-09-14", "Female", "O-", "Mayur Vihar 1, New Delhi", "+91 98610 00000");
        createPatientProfile(patientUserIds[20], "O-", "None", "Myopia & Eye Strain", "Lubricant Eye Drops", 162.0, 53.0, 112, 70, 70, 98.4, 99, "Prabir Das", "+91 98610 00000", "Father");

        // ── Past & Upcoming Appointments ──
        Long[] apptIds = new Long[16];

        // Appt 1: Aarav -> Dr. Ananya
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 1, date('now', '-10 days'), '10:00', 'IN_CLINIC', 'COMPLETED', 'Persistent fever and body ache for 3 days', datetime('now', '-10 days'))",
                "SS-2026-0001", patientUserIds[1], docProfileIds[1]);
        apptIds[1] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 2: Meera -> Dr. Vikram
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 2, date('now', '-8 days'), '11:00', 'IN_CLINIC', 'COMPLETED', 'Chest pain and shortness of breath during exercise', datetime('now', '-8 days'))",
                "SS-2026-0002", patientUserIds[2], docProfileIds[2]);
        apptIds[2] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 3: Rohit -> Dr. Priya
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 3, date('now', '-7 days'), '14:30', 'IN_CLINIC', 'COMPLETED', 'Recurring acne and skin rash on forearms', datetime('now', '-7 days'))",
                "SS-2026-0003", patientUserIds[3], docProfileIds[3]);
        apptIds[3] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 4: Aarav -> Dr. Suresh
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 1, date('now', '-5 days'), '10:30', 'VIDEO', 'COMPLETED', 'Child vaccination and growth checkup', datetime('now', '-5 days'))",
                "SS-2026-0004", patientUserIds[1], docProfileIds[4]);
        apptIds[4] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 5: Meera -> Dr. Kavitha
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 2, date('now', '-4 days'), '15:00', 'IN_CLINIC', 'COMPLETED', 'Knee pain after morning walks', datetime('now', '-4 days'))",
                "SS-2026-0005", patientUserIds[2], docProfileIds[5]);
        apptIds[5] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 6: Rohit -> Dr. Ananya
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 1, date('now', '-3 days'), '09:30', 'VIDEO', 'COMPLETED', 'Follow-up for seasonal allergies and asthma', datetime('now', '-3 days'))",
                "SS-2026-0006", patientUserIds[3], docProfileIds[1]);
        apptIds[6] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 7: Aarav -> Dr. Vikram
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 2, date('now', '-2 days'), '11:30', 'IN_CLINIC', 'COMPLETED', 'Routine cardiac checkup - family history of heart disease', datetime('now', '-2 days'))",
                "SS-2026-0007", patientUserIds[1], docProfileIds[2]);
        apptIds[7] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 8: Kabir -> Dr. Siddharth (Neurology)
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 4, date('now', '-1 days'), '16:00', 'IN_CLINIC', 'COMPLETED', 'Severe chronic migraine and sleep disturbances', datetime('now', '-1 days'))",
                "SS-2026-0008", patientUserIds[5], docProfileIds[7]);
        apptIds[8] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 9: Priyanka -> Dr. Sunita (Gynecology)
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 3, date('now', '-1 days'), '11:00', 'VIDEO', 'COMPLETED', 'PCOS consultation & dietary wellness guidance', datetime('now', '-1 days'))",
                "SS-2026-0009", patientUserIds[4], docProfileIds[8]);
        apptIds[9] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Appt 10: Meera -> Dr. Ananya (Cancelled)
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, cancellation_reason, created_at) VALUES (?, ?, ?, 1, date('now', '-1 days'), '09:00', 'IN_CLINIC', 'CANCELLED', 'General checkup', 'Had to travel urgently', datetime('now', '-2 days'))",
                "SS-2026-0010", patientUserIds[2], docProfileIds[1]);
        apptIds[10] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // Upcoming Scheduled Appointments
        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 1, date('now', '+1 days'), '10:00', 'IN_CLINIC', 'SCHEDULED', 'Follow-up for fever treatment', datetime('now'))",
                "SS-2026-0011", patientUserIds[1], docProfileIds[1]);
        apptIds[11] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 3, date('now', '+2 days'), '14:00', 'VIDEO', 'SCHEDULED', 'Skin allergy follow-up consultation', datetime('now'))",
                "SS-2026-0012", patientUserIds[2], docProfileIds[3]);
        apptIds[12] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 1, date('now', '+3 days'), '11:00', 'IN_CLINIC', 'SCHEDULED', 'Child routine vaccination - 18 months', datetime('now'))",
                "SS-2026-0013", patientUserIds[3], docProfileIds[4]);
        apptIds[13] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 2, date('now', '+4 days'), '15:30', 'VIDEO', 'SCHEDULED', 'Stress and corporate burnout consultation', datetime('now'))",
                "SS-2026-0014", patientUserIds[5], docProfileIds[11]);
        apptIds[14] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        jdbcTemplate.update("INSERT INTO appointments (reference_code, patient_id, doctor_id, clinic_id, appointment_date, appointment_time, mode, status, reason_for_visit, created_at) VALUES (?, ?, ?, 5, date('now', '+5 days'), '12:00', 'IN_CLINIC', 'SCHEDULED', 'Comprehensive eye vision testing and screen fatigue checkup', datetime('now'))",
                "SS-2026-0015", patientUserIds[6], docProfileIds[10]);
        apptIds[15] = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);

        // ── Medical Records ──
        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Viral Fever with Myalgia', 'Patient presented with high-grade fever (102F), body ache, and mild sore throat for 3 days. Throat examination shows mild congestion. No signs of bacterial infection. Vitals stable.', 'Tab Paracetamol 650mg - 1 tab every 6 hours for 5 days\nTab Cetirizine 10mg - 1 tab at bedtime for 5 days\nORS sachets - 2 per day\nAdequate rest and hydration advised', 'Consultation', date('now', '+4 days'), datetime('now', '-10 days'))",
                apptIds[1], patientUserIds[1], docProfileIds[1]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Exercise-Induced Angina - Evaluation', 'Patient reports chest tightness during moderate exercise. ECG shows mild ST-segment changes. Lipid profile and TMT advised. No acute distress. BP 138/88. BMI 26.2.', 'Tab Aspirin 75mg - Once daily after breakfast\nTab Atorvastatin 10mg - Once at bedtime\nLifestyle modifications: 30 min walk daily, low-salt diet\nTMT test scheduled next week', 'Consultation', date('now', '+7 days'), datetime('now', '-8 days'))",
                apptIds[2], patientUserIds[2], docProfileIds[2]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Acne Vulgaris Grade II with Contact Dermatitis', 'Multiple open and closed comedones on face. Erythematous papular rash on bilateral forearms, likely contact dermatitis. No pustular lesions. Advised patch testing.', 'Gel Adapalene 0.1% - Apply on face at bedtime\nCream Mometasone 0.1% - Apply on forearms twice daily for 7 days\nFacewash - Salicylic Acid 2% - Twice daily\nAvoid harsh soaps and known irritants', 'Consultation', date('now', '+14 days'), datetime('now', '-7 days'))",
                apptIds[3], patientUserIds[3], docProfileIds[3]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Well-Child Visit - Vaccination Update', 'Child (3 years) growth parameters within normal range. Height: 96cm (50th percentile), Weight: 14.5kg (50th percentile). Development milestones age-appropriate. DPT booster administered.', 'DPT Booster vaccine administered\nSyrup Multivitamin - 5ml once daily for 30 days\nDiet: Include more iron-rich foods - spinach, dates, jaggery\nNext vaccination: OPV booster due at 5 years', 'Consultation', date('now', '+180 days'), datetime('now', '-5 days'))",
                apptIds[4], patientUserIds[1], docProfileIds[4]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Bilateral Knee Osteoarthritis - Early Stage', 'Patient complains of bilateral knee pain, worse in morning and during stair climbing. X-ray shows early degenerative changes. No joint effusion. ROM mildly restricted.', 'Tab Glucosamine + Chondroitin - Twice daily for 3 months\nCap Calcium + Vitamin D3 - Once daily\nPhysiotherapy: Quadriceps strengthening exercises - 15 min twice daily\nAvoid squatting and prolonged standing\nKnee cap support during walks', 'Consultation', date('now', '+30 days'), datetime('now', '-4 days'))",
                apptIds[5], patientUserIds[2], docProfileIds[5]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Allergic Rhinitis with Mild Asthma Exacerbation', 'Known case of mild intermittent asthma. Currently experiencing nasal congestion, sneezing, and mild wheeze. Peak flow at 85% predicted. Triggered by seasonal pollen.', 'Inhaler Budesonide 200mcg - 2 puffs twice daily for 14 days\nTab Montelukast 10mg - Once at bedtime for 30 days\nNasal spray Fluticasone - 2 sprays each nostril morning\nAvoid early morning outdoor activity during pollen season', 'Consultation', date('now', '+14 days'), datetime('now', '-3 days'))",
                apptIds[6], patientUserIds[3], docProfileIds[1]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Cardiac Risk Assessment - Low Risk', 'Family history of MI (father at age 55). Patient is 34M, non-smoker, BMI 24.1. Lipid profile normal. ECG normal sinus rhythm. 2D Echo: Normal LV function, EF 62%. TMT: Negative for inducible ischemia.', 'No medication required at this time\nLifestyle: Continue regular exercise 30 min/day\nDiet: Mediterranean-style, limit red meat\nAnnual cardiac screening recommended\nRepeat lipid profile in 6 months', 'Consultation', date('now', '+180 days'), datetime('now', '-2 days'))",
                apptIds[7], patientUserIds[1], docProfileIds[2]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Migraine without Aura & Tension Headache', 'Frequent unilateral throbbing headache (6-8 episodes/month). Photophobia present. MRI brain normal. Triggered by screen fatigue and irregular sleep.', 'Tab Propranolol 40mg - Once daily in morning\nTab Rizatriptan 10mg - SOS during severe attack\nMagnesium 400mg + Riboflavin 400mg daily\nScreen time limitation and blue-light filter advised', 'Consultation', date('now', '+30 days'), datetime('now', '-1 days'))",
                apptIds[8], patientUserIds[5], docProfileIds[7]);

        jdbcTemplate.update("INSERT INTO medical_records (appointment_id, patient_id, doctor_id, diagnosis_title, clinical_notes, prescriptions, record_type, follow_up_date, created_at) VALUES (?, ?, ?, 'Polycystic Ovary Syndrome (PCOS) - Management', 'Irregular menstrual cycles and mild insulin resistance. Pelvic ultrasound confirms bilateral polycystic ovaries. Thyroid profile normal. HbA1c 5.4%.', 'Tab Metformin 500mg - Once daily with dinner\nCap Myo-Inositol 2g + D-Chiro Inositol - Twice daily\nLow glycemic index diet & 45 min cardio 5 days/week\nWeight management consultation scheduled', 'Consultation', date('now', '+60 days'), datetime('now', '-1 days'))",
                apptIds[9], patientUserIds[4], docProfileIds[8]);

        // ── Feedback / Reviews ──
        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Ananya was very thorough and patient. She explained everything clearly and the fever resolved within 3 days of following her prescription. Highly recommended!', datetime('now', '-9 days'))",
                apptIds[1], patientUserIds[1], docProfileIds[1]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Vikram is an excellent cardiologist. He was very reassuring and ordered all the right tests. The clinic facilities are world-class. Feeling much more confident about my heart health.', datetime('now', '-7 days'))",
                apptIds[2], patientUserIds[2], docProfileIds[2]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 4, 'Dr. Priya diagnosed my skin condition accurately. The prescribed treatment showed improvement within a week. Only minor issue was the wait time at the clinic.', datetime('now', '-6 days'))",
                apptIds[3], patientUserIds[3], docProfileIds[3]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Suresh is wonderful with kids! My child was very comfortable during the entire visit. He explained the vaccination schedule clearly and gave great dietary advice.', datetime('now', '-4 days'))",
                apptIds[4], patientUserIds[1], docProfileIds[4]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 4, 'Dr. Kavitha was very knowledgeable about my knee condition. The physiotherapy exercises she recommended have been very helpful. Will continue follow-up treatment.', datetime('now', '-3 days'))",
                apptIds[5], patientUserIds[2], docProfileIds[5]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Second visit to Dr. Ananya for my asthma. She remembered my history and adjusted the treatment perfectly. Video consultation was very convenient.', datetime('now', '-2 days'))",
                apptIds[6], patientUserIds[3], docProfileIds[1]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Vikram conducted a very thorough cardiac screening. All reports came normal which was very reassuring. He took time to explain everything in detail.', datetime('now', '-1 days'))",
                apptIds[7], patientUserIds[1], docProfileIds[2]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Siddharth is an absolute master of neurology. My migraines are significantly reduced after starting his preventative protocol.', datetime('now', '-1 days'))",
                apptIds[8], patientUserIds[5], docProfileIds[7]);

        jdbcTemplate.update("INSERT INTO feedback (appointment_id, patient_id, doctor_id, rating, comment, created_at) VALUES (?, ?, ?, 5, 'Dr. Sunita gave me complete clarity on PCOS management. Her holistic approach combining diet, exercise, and light medication is fantastic.', datetime('now', '-1 days'))",
                apptIds[9], patientUserIds[4], docProfileIds[8]);

        // ── Notifications ──
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Appointment Confirmed', 'Your appointment with Dr. Ananya Sharma on tomorrow at 10:00 AM has been confirmed.', 'APPOINTMENT', 0, datetime('now'))", patientUserIds[1]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Medical Record Available', 'Your consultation record from Dr. Vikram Patel is now available. View your prescriptions and follow-up instructions.', 'MEDICAL', 0, datetime('now', '-2 days'))", patientUserIds[1]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Vaccination Reminder', 'Next vaccination (OPV Booster) for your child is due. Please schedule an appointment with Dr. Suresh Iyer.', 'REMINDER', 0, datetime('now', '-1 days'))", patientUserIds[1]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Welcome to SehatSetu!', 'Your account has been created successfully. Explore our network of verified doctors and book your first appointment.', 'SYSTEM', 1, datetime('now', '-15 days'))", patientUserIds[1]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Appointment Confirmed', 'Your video consultation with Dr. Priya Menon in 2 days at 2:00 PM has been confirmed.', 'APPOINTMENT', 0, datetime('now'))", patientUserIds[2]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Follow-up Reminder', 'Dr. Kavitha Reddy has recommended a follow-up visit for your knee treatment. Please book an appointment at your convenience.', 'REMINDER', 0, datetime('now', '-1 days'))", patientUserIds[2]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Prescription Ready', 'Your prescription from Dr. Vikram Patel is ready for download. Remember to take Aspirin 75mg daily after breakfast.', 'MEDICAL', 1, datetime('now', '-7 days'))", patientUserIds[2]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Appointment Confirmed', 'Your appointment with Dr. Suresh Iyer in 3 days at 11:00 AM has been confirmed.', 'APPOINTMENT', 0, datetime('now'))", patientUserIds[3]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Rate Your Visit', 'How was your experience with Dr. Ananya Sharma? Please leave a review to help other patients.', 'SYSTEM', 0, datetime('now', '-2 days'))", patientUserIds[3]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'New Appointment', 'You have a new appointment with Aarav Singh scheduled for tomorrow at 10:00 AM.', 'APPOINTMENT', 0, datetime('now'))", docUserIds[1]);
        jdbcTemplate.update("INSERT INTO notifications (user_id, title, message, notification_type, is_read, created_at) VALUES (?, 'Weekly Report', 'This week: 15 appointments completed, 8 new patient registrations, 1 cancellation. System running smoothly.', 'SYSTEM', 0, datetime('now', '-1 days'))", adminUserId);

        // ── Audit Logs ──
        jdbcTemplate.update("INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, 'ADMIN_LOGIN', 'Admin Rajesh K. Sharma logged in', '127.0.0.1', datetime('now', '-1 days'))", adminUserId);
        jdbcTemplate.update("INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, 'DOCTOR_VERIFIED', 'Verified Dr. Siddharth Malhotra (Neurology)', '127.0.0.1', datetime('now', '-5 days'))", adminUserId);
        jdbcTemplate.update("INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, 'APPOINTMENT_BOOKED', 'Aarav Singh booked appointment SS-2026-0011 with Dr. Ananya Sharma', '192.168.1.101', datetime('now'))", patientUserIds[1]);
        jdbcTemplate.update("INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, 'APPOINTMENT_CANCELLED', 'Meera Joshi cancelled appointment SS-2026-0010 - Reason: Had to travel urgently', '192.168.1.102', datetime('now', '-2 days'))", patientUserIds[2]);
        jdbcTemplate.update("INSERT INTO audit_logs (user_id, action, details, ip_address, created_at) VALUES (?, 'CONSULTATION_COMPLETED', 'Dr. Ananya Sharma released medical prescription for Aarav Singh', '192.168.1.105', datetime('now', '-10 days'))", docUserIds[1]);

        // ── System Settings ──
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('platform_name', 'SehatSetu')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('support_email', 'support@sehatsetu.in')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('appointment_buffer_mins', '15')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('max_advance_booking_days', '30')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('cancellation_policy_hours', '4')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('video_consultation_enabled', 'true')");
        jdbcTemplate.update("INSERT INTO system_settings (key, value) VALUES ('maintenance_mode', 'false')");
    }
}
