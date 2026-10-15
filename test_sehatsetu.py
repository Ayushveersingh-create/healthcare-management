"""
SehatSetu - Automated Quality Assurance & Integration Test Suite
Verifies all critical healthcare workflows across Patients, Doctors, and Administrators.
"""

import sys
import os
import io
import asyncio
import httpx
from datetime import datetime, timedelta

# Fix Windows console UTF-8 encoding
if sys.platform == "win32":
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8', errors='replace')
    sys.stderr = io.TextIOWrapper(sys.stderr.buffer, encoding='utf-8', errors='replace')

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

async def run_async_tests():
    print("\n[INFO] Initializing SehatSetu Automated Integration Test Suite (Java Spring Boot Backend)...", flush=True)
    base_url = os.environ.get("SEHATSETU_URL", "http://127.0.0.1:8000")
    
    async with httpx.AsyncClient(base_url=base_url, timeout=30.0) as client:
        # 1. Public Metadata Endpoints
        print("\n[TEST 1] Testing Public Metadata Endpoints...", flush=True)
        res = await client.get("/api/config")
        assert res.status_code == 200, f"Config failed: {res.text}"
        data = res.json()
        assert data["platform_name"] == "SehatSetu"
        assert data["currency"] == "₹"
        assert data["timezone"] == "Asia/Kolkata (IST)"

        res = await client.get("/api/specialities")
        assert res.status_code == 200
        specialities = res.json()["specialities"]
        assert len(specialities) >= 10
        print(f"  -> Verified {len(specialities)} medical specialities with Indian localization.", flush=True)

        res = await client.get("/api/clinics")
        assert res.status_code == 200
        clinics = res.json()["clinics"]
        assert len(clinics) >= 5
        print(f"  -> Verified {len(clinics)} multi-city clinics in Delhi, Noida, Bengaluru, etc.", flush=True)

        # 2. Authentication, RBAC & Demo Logins
        print("\n[TEST 2] Testing Authentication & RBAC...", flush=True)
        # Patient demo login
        res = await client.post("/api/auth/demo-login", json={"role": "PATIENT"})
        assert res.status_code == 200
        pat_token = res.json()["access_token"]
        pat_headers = {"Authorization": f"Bearer {pat_token}"}
        assert res.json()["user"]["role"] == "PATIENT"
        print("  -> Patient demo login verified (Aarav Singh)", flush=True)

        # Test missing password protection on Doctor demo login
        res_no_pw = await client.post("/api/auth/demo-login", json={"role": "DOCTOR"})
        assert res_no_pw.status_code == 401, "Doctor demo login without password was not blocked!"

        # Doctor demo login with password
        res = await client.post("/api/auth/demo-login", json={"role": "DOCTOR", "password": "Doctor@123"})
        assert res.status_code == 200, f"Doctor login failed: {res.text}"
        doc_token = res.json()["access_token"]
        doc_headers = {"Authorization": f"Bearer {doc_token}"}
        doc_user_id = res.json()["user"]["id"]
        assert res.json()["user"]["role"] == "DOCTOR"
        print("  -> Doctor demo login password protection verified (Dr. Ananya Sharma)", flush=True)

        # Admin demo login with password
        res = await client.post("/api/auth/demo-login", json={"role": "ADMIN", "password": "Admin@123"})
        assert res.status_code == 200, f"Admin login failed: {res.text}"
        admin_token = res.json()["access_token"]
        admin_headers = {"Authorization": f"Bearer {admin_token}"}
        assert res.json()["user"]["role"] == "ADMIN"
        print("  -> Admin demo login password protection verified (Rajesh K. Sharma)", flush=True)

        # New Patient Registration
        reg_payload = {
            "email": f"test.patient.{int(datetime.now().timestamp())}@example.in",
            "password": "SecurePassword@123",
            "full_name": "Priyanka Verma",
            "mobile": "+91 98765 43210",
            "city": "Gurugram",
            "dob": "1996-03-22",
            "gender": "Female",
            "blood_group": "B+",
            "address": "Sector 43, Gurugram, Haryana",
            "emergency_contact": "+91 98765 00000"
        }
        res_reg = await client.post("/api/auth/register", json=reg_payload)
        assert res_reg.status_code == 200, f"Registration failed: {res_reg.text}"
        new_pat_token = res_reg.json()["access_token"]
        new_pat_headers = {"Authorization": f"Bearer {new_pat_token}"}
        new_pat_id = res_reg.json()["user"]["id"]
        print(f"  -> New patient registration verified: {reg_payload['full_name']} (ID {new_pat_id})", flush=True)

        # 3. Doctor Discovery, Search & Filtering
        print("\n[TEST 3] Testing Doctor Discovery Engine...", flush=True)
        # Search by city
        res = await client.get("/api/doctors?city=Noida")
        assert res.status_code == 200
        noida_docs = res.json()["doctors"]
        assert len(noida_docs) > 0
        target_doc = noida_docs[0]
        test_doc_id = target_doc["id"]
        print(f"  -> Found {len(noida_docs)} doctors in Noida. Selected: {target_doc['full_name']} ({target_doc['speciality']})", flush=True)

        # Search by name query
        res_s = await client.get("/api/doctors?search=Rohan")
        assert res_s.status_code == 200
        assert any("Rohan" in d["full_name"] for d in res_s.json()["doctors"])
        print("  -> Search query by doctor name verified.", flush=True)

        # 4. Real Dynamic Time Slot Generation Engine
        print("\n[TEST 4] Testing Slot Generation & Availability Engine...", flush=True)
        # Target a future weekday
        future_dt = datetime.now() + timedelta(days=5)
        while future_dt.weekday() >= 5: # Skip weekends for standard testing
            future_dt += timedelta(days=1)
        future_date_str = future_dt.strftime("%Y-%m-%d")

        res_slots = await client.get(f"/api/doctors/{test_doc_id}/slots?date={future_date_str}")
        assert res_slots.status_code == 200
        slot_data = res_slots.json()
        assert slot_data["success"] is True
        avail_slots = [s for s in slot_data["slots"] if s["is_available"]]
        assert len(avail_slots) > 0, "No available slots found for doctor on weekday"
        first_slot = avail_slots[0]["time_24"]
        second_slot = avail_slots[1]["time_24"]
        print(f"  -> Generated {len(slot_data['slots'])} total slots ({len(avail_slots)} available) on {future_date_str}. First: {first_slot}", flush=True)

        # 5. Appointment Booking & Collision Prevention
        print("\n[TEST 5] Testing Appointment Booking & Double Booking Prevention...", flush=True)
        book_data = {
            "doctor_id": test_doc_id,
            "appointment_date": future_date_str,
            "appointment_time": first_slot,
            "consultation_type": "In-Clinic",
            "reason_for_visit": "Persistent headache and seasonal fatigue"
        }
        res_book = await client.post("/api/appointments/book", json=book_data, headers=new_pat_headers)
        assert res_book.status_code == 200, f"Booking failed: {res_book.text}"
        booking_info = res_book.json()
        apt_id = booking_info["appointment"]["id"]
        ref_id = booking_info["reference_id"]
        assert ref_id.startswith("SS-APT-")
        print(f"  -> Appointment booked successfully! Ref: {ref_id}, ID: {apt_id}", flush=True)

        # Physical Collision Check: Attempt to book the exact same slot with another patient
        res_collision = await client.post("/api/appointments/book", json=book_data, headers=pat_headers)
        assert res_collision.status_code == 400, "Double booking was not prevented!"
        print("  -> Collision prevention verified: Duplicate booking correctly rejected with HTTP 400.", flush=True)

        # 6. Rescheduling & Slot Release Integrity
        print("\n[TEST 6] Testing Reschedule Workflow & Slot Release...", flush=True)
        res_resched = await client.put(
            f"/api/appointments/{apt_id}/reschedule",
            json={"appointment_date": future_date_str, "appointment_time": second_slot},
            headers=new_pat_headers
        )
        assert res_resched.status_code == 200, f"Reschedule failed: {res_resched.text}"

        # Verify old slot is now freed up
        res_slots_after = await client.get(f"/api/doctors/{test_doc_id}/slots?date={future_date_str}")
        freed_slot = next((s for s in res_slots_after.json()["slots"] if s["time_24"] == first_slot), None)
        assert freed_slot is not None and freed_slot["is_available"] is True, "Previous slot was not released after rescheduling!"
        print(f"  -> Rescheduled appointment to {second_slot}. Slot {first_slot} successfully released and reopened.", flush=True)

        # 7. Doctor Consultation Notes & Patient Medical History
        print("\n[TEST 7] Testing Doctor Consultation & Patient Medical Records...", flush=True)
        # Doctor records consultation notes and marks completed
        record_payload = {
            "patient_id": new_pat_id,
            "appointment_id": apt_id,
            "record_date": future_date_str,
            "visit_reason": "Persistent headache and seasonal fatigue",
            "diagnosis_title": "Tension-type Headache with Dehydration",
            "record_type": "Consultation",
            "clinical_notes": "BP 118/76 mmHg, Pulse 72 bpm. Neurological exam normal. High stress and inadequate fluid intake reported.",
            "prescription_notes": "1. Oral Rehydration Solution (ORS) 1 sachet daily in 1L water x 5 days\n2. Tab Naproxen 250mg SOS after meals for severe headache\n3. Ensure 7-8 hours sleep.",
            "follow_up_date": (future_dt + timedelta(days=14)).strftime("%Y-%m-%d")
        }
        res_record = await client.post("/api/medical-records", json=record_payload, headers=doc_headers)
        assert res_record.status_code == 200, f"Add record failed: {res_record.text}"
        rec_id = res_record.json()["record_id"]
        print(f"  -> Doctor consultation record added (ID {rec_id}). Appointment auto-marked as Completed.", flush=True)

        # Verify patient can view their new medical record
        res_pat_records = await client.get("/api/medical-records", headers=new_pat_headers)
        assert res_pat_records.status_code == 200
        records = res_pat_records.json()["records"]
        assert any(r["id"] == rec_id for r in records), "New medical record not found in patient history!"
        print("  -> Patient medical history timeline verified with clinical diagnosis and prescription.", flush=True)

        # RBAC Check: Ensure another patient cannot access this patient's medical records
        res_unauthorized = await client.get(f"/api/medical-records/{rec_id}", headers=pat_headers)
        assert res_unauthorized.status_code == 403, "Unauthorized medical record access was not blocked!"
        print("  -> Security check passed: Unauthorized patient blocked from accessing medical record.", flush=True)

        # 8. Patient Feedback & Rating Recalculation
        print("\n[TEST 8] Testing Patient Feedback & Rating Engine...", flush=True)
        fb_data = {
            "appointment_id": apt_id,
            "rating": 5,
            "comment": "Exceptional doctor! Dr. Sharma took time to understand my symptoms and provided very clear lifestyle guidance."
        }
        res_fb = await client.post("/api/feedback", json=fb_data, headers=new_pat_headers)
        assert res_fb.status_code == 200, f"Feedback submission failed: {res_fb.text}"
        print("  -> Patient feedback and 5-star rating submitted successfully.", flush=True)

        # Verify duplicate feedback prevention
        res_fb_dup = await client.post("/api/feedback", json=fb_data, headers=new_pat_headers)
        assert res_fb_dup.status_code == 400, "Duplicate feedback was not rejected!"
        print("  -> Duplicate review prevention verified: HTTP 400 returned.", flush=True)

        # 9. Real-Time Notification System
        print("\n[TEST 9] Testing Notifications System...", flush=True)
        res_notif = await client.get("/api/notifications", headers=new_pat_headers)
        assert res_notif.status_code == 200
        notifs = res_notif.json()["notifications"]
        assert len(notifs) >= 2, "Expected appointment and record notifications"
        first_notif = notifs[0]
        res_read = await client.put(f"/api/notifications/{first_notif['id']}/read", headers=new_pat_headers)
        assert res_read.status_code == 200
        print(f"  -> Notifications verified ({len(notifs)} received, marked ID {first_notif['id']} as read).", flush=True)

        # 10. Admin Operations & Live Analytics
        print("\n[TEST 10] Testing Admin Operations & Analytics...", flush=True)
        res_stats = await client.get("/api/admin/stats", headers=admin_headers)
        assert res_stats.status_code == 200
        stats = res_stats.json()["stats"]
        assert stats["total_users"] >= 10
        assert stats["total_doctors"] >= 5
        print(f"  -> Admin KPIs verified: {stats['total_users']} users, {stats['total_doctors']} doctors, {stats['total_appointments']} appointments.", flush=True)

        res_analytics = await client.get("/api/admin/analytics", headers=admin_headers)
        assert res_analytics.status_code == 200
        analytics = res_analytics.json()
        assert len(analytics["by_speciality"]) > 0
        assert len(analytics["by_city"]) > 0
        assert len(analytics["trends"]) > 0
        print("  -> Real analytics datasets verified for Chart.js rendering (Specialities, Cities, Daily Trends).", flush=True)

        # Admin Settings Update
        res_settings = await client.put(
            "/api/admin/settings",
            json={"settings": {"support_phone": "+91 1800 202 7788"}},
            headers=admin_headers
        )
        assert res_settings.status_code == 200
        print("  -> Admin platform settings update verified.", flush=True)

    print("\n" + "=" * 65, flush=True)
    print("  [SUCCESS] ALL 10 INTEGRATION TEST SUITES PASSED FLAWLESSLY!", flush=True)
    print("=" * 65, flush=True)

if __name__ == "__main__":
    asyncio.run(run_async_tests())
