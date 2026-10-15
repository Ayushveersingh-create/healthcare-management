/**
 * SehatSetu - Doctor Workspace Controller
 * Handles schedule configuration, appointments workflow, clinical notes entry, and patient records.
 */

// ==================== 1. DOCTOR DASHBOARD ====================

async function loadDoctorDashboard() {
  const user = AppState.currentUser;
  if (!user || user.role !== "DOCTOR") return;

  const greetingEl = document.getElementById("doctor-greeting-name");
  if (greetingEl) greetingEl.textContent = user.full_name;

  try {
    const [statsRes, aptsRes] = await Promise.all([
      apiCall("/api/doctors/me/dashboard-stats"),
      apiCall("/api/appointments/my?tab=all")
    ]);

    const s = statsRes;
    document.getElementById("d-stat-today").textContent = s.today_total || 0;
    document.getElementById("d-stat-upcoming").textContent = s.upcoming_total || 0;
    document.getElementById("d-stat-completed").textContent = s.completed_consultations || 0;
    document.getElementById("d-stat-patients").textContent = s.patients_seen || 0;

    // Next Appointment Card
    const nextCard = document.getElementById("doctor-next-appointment-card");
    if (nextCard) {
      if (s.next_appointment) {
        const next = s.next_appointment;
        nextCard.innerHTML = `
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div class="flex items-start gap-4">
              <div class="w-12 h-12 rounded-xl bg-teal-800 text-white font-bold text-base flex items-center justify-center shrink-0 shadow-sm">
                ${next.patient_name.split(" ").map(n => n[0]).join("")}
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <h4 class="font-bold text-slate-900 text-base">${next.patient_name}</h4>
                  <span class="badge-confirmed px-2 py-0.5 rounded text-[11px] font-semibold">${next.status}</span>
                </div>
                <p class="text-xs text-slate-500 mt-0.5">${next.patient_gender || 'Patient'} • ${next.patient_city || 'India'}</p>
                <div class="mt-2 flex items-center gap-3 text-xs text-slate-700">
                  <span>📅 <strong>${formatDateIST(next.appointment_date)}</strong></span>
                  <span>⏰ <strong>${next.appointment_time}</strong></span>
                  <span>(${next.consultation_type})</span>
                </div>
                <p class="text-xs text-slate-600 mt-1 italic">"${next.reason_for_visit}"</p>
              </div>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <button onclick="openConsultationNotesModal(${next.id}, ${next.patient_id}, '${next.patient_name}', '${next.reason_for_visit}')" 
                class="px-3.5 py-2 text-xs font-semibold text-white bg-teal-800 hover:bg-teal-700 rounded-lg shadow-sm transition">
                Start Consultation / Add Notes
              </button>
            </div>
          </div>
        `;
      } else {
        nextCard.innerHTML = `
          <div class="text-center py-6">
            <p class="text-xs text-slate-500">No appointments scheduled for right now.</p>
          </div>
        `;
      }
    }

    // Today's Appointments Timeline
    const todayStr = new Date().toISOString().split("T")[0];
    const allApts = Array.isArray(aptsRes) ? aptsRes : (aptsRes.appointments || []);
    const todayApts = allApts.filter(a => a.appointment_date === todayStr);
    const todayList = document.getElementById("doctor-today-timeline-list");
    if (todayList) {
      if (todayApts.length > 0) {
        todayList.innerHTML = todayApts.map(a => `
          <div class="p-3.5 rounded-lg border border-slate-200 bg-white flex items-center justify-between gap-4 hover:border-teal-400 transition">
            <div class="flex items-center gap-3">
              <span class="px-2.5 py-1 rounded bg-teal-50 text-teal-800 font-bold text-xs font-mono">${a.appointment_time_12h}</span>
              <div>
                <div class="text-xs font-bold text-slate-800">${a.patient_name}</div>
                <div class="text-[11px] text-slate-500">${a.consultation_type} • "${a.reason_for_visit}"</div>
              </div>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-[10px] px-2 py-0.5 rounded font-semibold ${a.status === 'Completed' ? 'badge-completed' : 'badge-confirmed'}">${a.status}</span>
              ${a.status !== 'Completed' ? `
                <button onclick="openConsultationNotesModal(${a.id}, ${a.patient_id}, '${a.patient_name}', '${a.reason_for_visit}')" 
                  class="px-2.5 py-1 text-xs font-semibold text-teal-800 bg-teal-50 hover:bg-teal-100 rounded transition">
                  Consult
                </button>
              ` : ''}
            </div>
          </div>
        `).join("");
      } else {
        todayList.innerHTML = `<p class="text-xs text-slate-400 py-4 text-center">No appointments scheduled for today (${formatDateIST(todayStr)}).</p>`;
      }
    }

  } catch (err) {
    console.error("Error loading doctor dashboard:", err);
  }
}

// ==================== 2. SCHEDULE MANAGEMENT ====================

const DAY_NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"];

async function loadDoctorScheduleManager() {
  const container = document.getElementById("doctor-schedule-days-container");
  const blockedContainer = document.getElementById("doctor-blocked-dates-list");
  if (!container) return;

  container.innerHTML = `<div class="p-6 text-center text-xs text-slate-400">Loading schedule configuration...</div>`;

  try {
    const res = await apiCall("/api/doctors/me/schedule");
    const schedules = res.schedules || [];
    const blocked = res.blocked_dates || [];

    // Render Working Days (Monday - Saturday)
    container.innerHTML = [0, 1, 2, 3, 4, 5].map(dayIdx => {
      const existing = schedules.find(s => s.day_of_week === dayIdx);
      const isActive = existing ? existing.is_active : (dayIdx < 5 ? 1 : 0);
      const startTime = existing ? existing.start_time : "09:00";
      const endTime = existing ? existing.end_time : (dayIdx === 5 ? "13:00" : "17:00");
      const duration = existing ? existing.slot_duration_mins : 30;
      const breakStart = existing ? (existing.break_start || "") : (dayIdx < 5 ? "13:00" : "");
      const breakEnd = existing ? (existing.break_end || "") : (dayIdx < 5 ? "14:00" : "");

      return `
        <div class="p-4 rounded-xl border ${isActive ? 'bg-white border-slate-200' : 'bg-slate-50 border-slate-200 opacity-60'} flex flex-col md:flex-row md:items-center justify-between gap-4 day-sched-row" data-day="${dayIdx}">
          <div class="flex items-center gap-3 w-40">
            <input type="checkbox" class="day-active-chk rounded text-teal-800 focus:ring-teal-700 w-4 h-4 cursor-pointer" ${isActive ? 'checked' : ''} onchange="toggleDayRowActive(this)">
            <span class="font-bold text-sm text-slate-800">${DAY_NAMES[dayIdx]}</span>
          </div>

          <div class="flex-1 grid grid-cols-2 sm:grid-cols-4 gap-3 text-xs">
            <div>
              <label class="block text-slate-500 text-[10px] uppercase font-semibold mb-1">Start Time</label>
              <input type="time" value="${startTime}" class="day-start-time border border-slate-300 rounded px-2 py-1 w-full text-xs outline-none">
            </div>
            <div>
              <label class="block text-slate-500 text-[10px] uppercase font-semibold mb-1">End Time</label>
              <input type="time" value="${endTime}" class="day-end-time border border-slate-300 rounded px-2 py-1 w-full text-xs outline-none">
            </div>
            <div>
              <label class="block text-slate-500 text-[10px] uppercase font-semibold mb-1">Break Window</label>
              <div class="flex items-center gap-1">
                <input type="time" value="${breakStart}" placeholder="Start" class="day-break-start border border-slate-300 rounded px-1.5 py-1 w-1/2 text-[11px] outline-none">
                <span class="text-slate-400">-</span>
                <input type="time" value="${breakEnd}" placeholder="End" class="day-break-end border border-slate-300 rounded px-1.5 py-1 w-1/2 text-[11px] outline-none">
              </div>
            </div>
            <div>
              <label class="block text-slate-500 text-[10px] uppercase font-semibold mb-1">Slot Duration</label>
              <select class="day-slot-duration border border-slate-300 rounded px-2 py-1 w-full text-xs outline-none">
                <option value="15" ${duration === 15 ? 'selected' : ''}>15 Mins</option>
                <option value="20" ${duration === 20 ? 'selected' : ''}>20 Mins</option>
                <option value="30" ${duration === 30 ? 'selected' : ''}>30 Mins</option>
                <option value="45" ${duration === 45 ? 'selected' : ''}>45 Mins</option>
                <option value="60" ${duration === 60 ? 'selected' : ''}>60 Mins</option>
              </select>
            </div>
          </div>
        </div>
      `;
    }).join("");

    // Render Blocked Dates / Leaves
    if (blockedContainer) {
      if (blocked.length > 0) {
        blockedContainer.innerHTML = blocked.map(b => `
          <div class="p-3 rounded-lg bg-white border border-slate-200 flex items-center justify-between gap-4">
            <div>
              <span class="text-xs font-bold text-slate-800">📅 ${formatDateIST(b.blocked_date)}</span>
              <p class="text-xs text-slate-500 mt-0.5">${b.reason || 'Personal Leave'}</p>
            </div>
            <button onclick="deleteBlockedDate(${b.id})" class="px-2.5 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 rounded transition">
              Remove
            </button>
          </div>
        `).join("");
      } else {
        blockedContainer.innerHTML = `<p class="text-xs text-slate-400 py-3">No dates currently blocked.</p>`;
      }
    }

  } catch (err) {
    container.innerHTML = `<div class="p-4 text-center text-rose-600 text-xs">${err.message}</div>`;
  }
}

function toggleDayRowActive(chk) {
  const row = chk.closest(".day-sched-row");
  if (chk.checked) {
    row.classList.remove("bg-slate-50", "opacity-60");
    row.classList.add("bg-white");
  } else {
    row.classList.add("bg-slate-50", "opacity-60");
    row.classList.remove("bg-white");
  }
}

async function saveDoctorSchedule() {
  const rows = document.querySelectorAll(".day-sched-row");
  const schedules = [];

  rows.forEach(r => {
    const day = parseInt(r.dataset.day);
    const isActive = r.querySelector(".day-active-chk").checked;
    const start = r.querySelector(".day-start-time").value || "09:00";
    const end = r.querySelector(".day-end-time").value || "17:00";
    const bStart = r.querySelector(".day-break-start").value || null;
    const bEnd = r.querySelector(".day-break-end").value || null;
    const duration = parseInt(r.querySelector(".day-slot-duration").value) || 30;

    schedules.push({
      day_of_week: day,
      start_time: start,
      end_time: end,
      break_start: bStart,
      break_end: bEnd,
      slot_duration_mins: duration,
      is_active: isActive
    });
  });

  try {
    await apiCall("/api/doctors/me/schedule", {
      method: "PUT",
      body: JSON.stringify({ schedules })
    });
    showToast("Working hours and schedule updated successfully!", "success");
    loadDoctorScheduleManager();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function handleAddBlockedDateSubmit(e) {
  e.preventDefault();
  const f = e.target;
  const dateVal = f.blocked_date.value;
  const reasonVal = f.reason.value.trim() || "Doctor Leave";

  if (!dateVal) {
    showToast("Please select a date to block.", "warning");
    return;
  }

  try {
    await apiCall("/api/doctors/me/blocked-dates", {
      method: "POST",
      body: JSON.stringify({
        blocked_date: dateVal,
        reason: reasonVal
      })
    });
    f.reset();
    showToast(`Date ${formatDateIST(dateVal)} marked unavailable.`, "success");
    loadDoctorScheduleManager();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function deleteBlockedDate(blockedId) {
  if (!confirm("Are you sure you want to unblock this date and reopen available appointment slots?")) {
    return;
  }

  try {
    await apiCall(`/api/doctors/me/blocked-dates/${blockedId}`, { method: "DELETE" });
    showToast("Blocked date removed. Slots reopened for booking.", "success");
    loadDoctorScheduleManager();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 3. DOCTOR APPOINTMENTS ====================

async function loadDoctorAppointments(filter = "all") {
  const container = document.getElementById("doctor-appointments-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="7" class="p-6 text-center text-xs text-slate-400">Loading appointments...</td></tr>`;

  try {
    const res = await apiCall("/api/appointments/my?tab=all");
    let apts = Array.isArray(res) ? res : (res.appointments || []);

    const todayStr = new Date().toISOString().split("T")[0];

    if (filter === "today") {
      apts = apts.filter(a => a.appointment_date === todayStr);
    } else if (filter === "upcoming") {
      apts = apts.filter(a => (a.status === 'Confirmed' || a.status === 'Requested') && a.appointment_date >= todayStr);
    } else if (filter === "completed") {
      apts = apts.filter(a => a.status === 'Completed');
    } else if (filter === "cancelled") {
      apts = apts.filter(a => a.status === 'Cancelled' || a.status === 'Rejected');
    }

    if (apts.length === 0) {
      container.innerHTML = `<tr><td colspan="7" class="p-8 text-center text-xs text-slate-500">No appointments found matching this filter.</td></tr>`;
      return;
    }

    container.innerHTML = apts.map(a => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5 font-mono text-slate-500">${a.reference_id}</td>
        <td class="p-3.5">
          <div class="font-bold text-slate-900">${a.patient_name}</div>
          <div class="text-[11px] text-slate-400">${a.patient_gender || ''} • ${a.patient_city || ''}</div>
        </td>
        <td class="p-3.5">
          <div class="font-semibold text-slate-800">${a.appointment_date_formatted}</div>
          <div class="text-teal-700 font-medium">${a.appointment_time_12h}</div>
        </td>
        <td class="p-3.5">${a.consultation_type}</td>
        <td class="p-3.5 max-w-xs truncate" title="${a.reason_for_visit}">${a.reason_for_visit}</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
            a.status === 'Confirmed' ? 'badge-confirmed' :
            (a.status === 'Completed' ? 'badge-completed' :
            (a.status === 'Cancelled' ? 'badge-cancelled' : 'badge-requested'))
          }">
            ${a.status}
          </span>
        </td>
        <td class="p-3.5 text-right space-x-1.5 whitespace-nowrap">
          ${a.status === 'Requested' ? `
            <button onclick="updateDoctorAppointmentStatus(${a.id}, 'Confirmed')" class="px-2.5 py-1 bg-teal-800 text-white font-semibold rounded hover:bg-teal-700 transition">Confirm</button>
            <button onclick="updateDoctorAppointmentStatus(${a.id}, 'Rejected')" class="px-2.5 py-1 bg-rose-50 text-rose-700 font-semibold rounded hover:bg-rose-100 transition">Reject</button>
          ` : ''}

          ${a.status === 'Confirmed' ? `
            <button onclick="openConsultationNotesModal(${a.id}, ${a.patient_id}, '${a.patient_name}', '${a.reason_for_visit}')" class="px-2.5 py-1 bg-teal-800 text-white font-semibold rounded hover:bg-teal-700 transition">
              Add Notes / Complete
            </button>
            <button onclick="updateDoctorAppointmentStatus(${a.id}, 'No Show')" class="px-2 py-1 text-slate-600 hover:bg-slate-100 rounded transition" title="Mark No Show">
              No Show
            </button>
          ` : ''}

          ${a.status === 'Completed' ? `
            <button onclick="viewPatientRecordByAppointment(${a.id})" class="px-2 py-1 text-teal-800 font-medium hover:underline">
              View Notes
            </button>
          ` : ''}
        </td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="7" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}

async function updateDoctorAppointmentStatus(appointmentId, newStatus) {
  try {
    await apiCall(`/api/appointments/${appointmentId}/status`, {
      method: "PUT",
      body: JSON.stringify({ status: newStatus })
    });
    showToast(`Appointment status updated to '${newStatus}'.`, "success");
    loadDoctorAppointments();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 4. CONSULTATION NOTES & PRESCRIPTIONS ====================

let activeConsultationAppointmentId = null;
let activeConsultationPatientId = null;

function openConsultationNotesModal(appointmentId, patientId, patientName, reason) {
  activeConsultationAppointmentId = appointmentId;
  activeConsultationPatientId = patientId;

  const modal = document.getElementById("consultation-modal");
  if (!modal) return;

  document.getElementById("cm-patient-name").textContent = patientName;
  document.getElementById("cm-visit-reason").textContent = reason || "Routine check";
  
  document.getElementById("cm-diagnosis-title").value = "";
  document.getElementById("cm-clinical-notes").value = "";
  document.getElementById("cm-prescriptions").value = "";
  document.getElementById("cm-follow-up-date").value = "";

  modal.classList.remove("hidden");
}

function closeConsultationModal() {
  const modal = document.getElementById("consultation-modal");
  if (modal) modal.classList.add("hidden");
  activeConsultationAppointmentId = null;
  activeConsultationPatientId = null;
}

async function handleConsultationNotesSubmit(e) {
  e.preventDefault();
  if (!activeConsultationPatientId) return;

  const f = e.target;
  const diagnosis = f.diagnosis_title.value.trim();
  const notes = f.clinical_notes.value.trim();
  const rx = f.prescription_notes.value.trim();
  const followUp = f.follow_up_date.value || null;
  const recType = f.record_type.value || "Consultation";

  if (!diagnosis || !notes) {
    showToast("Please provide Diagnosis Title and Clinical Observations.", "warning");
    return;
  }

  const btn = document.getElementById("cm-submit-btn");
  try {
    btn.disabled = true;
    btn.textContent = "Saving Medical Record...";

    await apiCall("/api/medical-records", {
      method: "POST",
      body: JSON.stringify({
        patient_id: activeConsultationPatientId,
        appointment_id: activeConsultationAppointmentId,
        diagnosis_title: diagnosis,
        record_type: recType,
        clinical_notes: notes,
        prescription_notes: rx,
        follow_up_date: followUp
      })
    });

    closeConsultationModal();
    showToast("Consultation notes & medical record successfully archived! Appointment marked as Completed.", "success");
    loadDoctorDashboard();
    loadDoctorAppointments();
  } catch (err) {
    showToast(err.message, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = "Save Record & Complete Appointment";
  }
}

// ==================== 5. DOCTOR PATIENTS DIRECTORY ====================

async function loadDoctorPatients() {
  const container = document.getElementById("doctor-patients-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="6" class="p-6 text-center text-xs text-slate-400">Loading patients...</td></tr>`;

  try {
    const res = await apiCall("/api/doctors/me/patients");
    const patients = res.patients || [];

    if (patients.length === 0) {
      container.innerHTML = `<tr><td colspan="6" class="p-8 text-center text-xs text-slate-500">No patients recorded yet.</td></tr>`;
      return;
    }

    container.innerHTML = patients.map(p => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5">
          <button onclick="openDoctorPatientModal(${p.id})" class="font-bold text-slate-900 hover:text-teal-700 text-left transition">${p.full_name}</button>
          <div class="text-[11px] text-slate-400">${p.email}</div>
        </td>
        <td class="p-3.5">${p.gender || '-'} / Blood: <strong>${p.blood_group || '-'}</strong></td>
        <td class="p-3.5">${p.city || '-'}</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded bg-teal-50 text-teal-800 font-bold">${p.total_appointments} visits</span>
        </td>
        <td class="p-3.5">${formatDateIST(p.last_consultation_date)}</td>
        <td class="p-3.5 text-right space-x-1.5">
          <button onclick="openDoctorPatientModal(${p.id})" 
            class="px-2.5 py-1 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded transition">
            👁️ View Profile & Vitals
          </button>
          <button onclick="openConsultationNotesModal(null, ${p.id}, '${p.full_name}', 'New Clinical Note')" 
            class="px-2.5 py-1 text-xs font-semibold text-teal-800 bg-teal-50 hover:bg-teal-100 rounded transition">
            + Add Notes
          </button>
        </td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="6" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}

async function openDoctorPatientModal(patientId) {
  const modal = document.getElementById("doctor-patient-modal");
  const content = document.getElementById("dpm-content");
  if (!modal || !content) return;

  modal.classList.remove("hidden");
  content.innerHTML = `<div class="p-8 text-center text-xs text-slate-400">Loading patient health record and vitals...</div>`;

  try {
    const res = await apiCall(`/api/doctors/patient-detail/${patientId}`);
    const u = res.patient || {};
    const p = res.profile || {};
    const recs = res.records || [];

    document.getElementById("dpm-patient-name").textContent = u.full_name;
    document.getElementById("dpm-patient-sub").textContent = `${u.gender || 'Patient'} • DOB: ${u.dob || '-'} • City: ${u.city || '-'}`;
    document.getElementById("dpm-avatar").textContent = u.full_name ? u.full_name.charAt(0).toUpperCase() : 'P';

    const bp = (p.bp_systolic && p.bp_diastolic) ? `${p.bp_systolic}/${p.bp_diastolic} mmHg` : '120/80 mmHg';
    const pulse = p.pulse_rate ? `${p.pulse_rate} bpm` : '72 bpm';
    const temp = p.temperature_f ? `${p.temperature_f} °F` : '98.6 °F';
    const spo2 = p.spo2_percent ? `${p.spo2_percent}%` : '99%';
    const ht = p.height_cm ? `${p.height_cm} cm` : '175 cm';
    const wt = p.weight_kg ? `${p.weight_kg} kg` : '72 kg';

    content.innerHTML = `
      <div class="grid grid-cols-2 sm:grid-cols-4 gap-3 bg-slate-50 p-4 rounded-xl border border-slate-100 text-xs">
        <div>
          <span class="text-slate-400 block text-[10px] uppercase font-bold">Email</span>
          <span class="font-semibold text-slate-800 truncate block">${u.email}</span>
        </div>
        <div>
          <span class="text-slate-400 block text-[10px] uppercase font-bold">Mobile</span>
          <span class="font-semibold text-slate-800 block">${u.mobile || '-'}</span>
        </div>
        <div>
          <span class="text-slate-400 block text-[10px] uppercase font-bold">Blood Group</span>
          <span class="font-bold text-teal-800 block">${u.blood_group || p.blood_group || '-'}</span>
        </div>
        <div>
          <span class="text-slate-400 block text-[10px] uppercase font-bold">Emergency Contact</span>
          <span class="font-semibold text-slate-800 block">${p.emergency_name || u.emergency_contact || '-'} (${p.emergency_phone || '-'})</span>
        </div>
      </div>

      <div>
        <h3 class="text-xs font-bold text-slate-800 uppercase tracking-wide mb-2.5 flex items-center gap-1.5">
          <span>❤️</span> Current Patient Vitals
        </h3>
        <div class="grid grid-cols-2 sm:grid-cols-3 gap-2.5 text-xs">
          <div class="p-3 rounded-lg bg-rose-50 border border-rose-100">
            <div class="text-[10px] text-rose-700 font-bold uppercase">Blood Pressure</div>
            <div class="text-sm font-black text-rose-900 mt-0.5">${bp}</div>
          </div>
          <div class="p-3 rounded-lg bg-emerald-50 border border-emerald-100">
            <div class="text-[10px] text-emerald-700 font-bold uppercase">Pulse / Heart Rate</div>
            <div class="text-sm font-black text-emerald-900 mt-0.5">${pulse}</div>
          </div>
          <div class="p-3 rounded-lg bg-amber-50 border border-amber-100">
            <div class="text-[10px] text-amber-700 font-bold uppercase">Body Temperature</div>
            <div class="text-sm font-black text-amber-900 mt-0.5">${temp}</div>
          </div>
          <div class="p-3 rounded-lg bg-blue-50 border border-blue-100">
            <div class="text-[10px] text-blue-700 font-bold uppercase">Oxygen (SpO2)</div>
            <div class="text-sm font-black text-blue-900 mt-0.5">${spo2}</div>
          </div>
          <div class="p-3 rounded-lg bg-slate-100 border border-slate-200">
            <div class="text-[10px] text-slate-500 font-bold uppercase">Height</div>
            <div class="text-xs font-bold text-slate-800 mt-0.5">${ht}</div>
          </div>
          <div class="p-3 rounded-lg bg-slate-100 border border-slate-200">
            <div class="text-[10px] text-slate-500 font-bold uppercase">Weight</div>
            <div class="text-xs font-bold text-slate-800 mt-0.5">${wt}</div>
          </div>
        </div>
      </div>

      <div class="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
        <div class="p-3.5 bg-rose-50/50 rounded-xl border border-rose-100">
          <div class="font-bold text-rose-800 uppercase text-[10px] mb-1">Known Allergies</div>
          <div class="text-slate-700 font-medium">${p.allergies || 'None reported.'}</div>
        </div>
        <div class="p-3.5 bg-amber-50/50 rounded-xl border border-amber-100">
          <div class="font-bold text-amber-800 uppercase text-[10px] mb-1">Chronic Conditions / History</div>
          <div class="text-slate-700 font-medium">${p.chronic_conditions || 'None reported.'}</div>
        </div>
        <div class="p-3.5 bg-teal-50/50 rounded-xl border border-teal-100">
          <div class="font-bold text-teal-800 uppercase text-[10px] mb-1">Active Medications</div>
          <div class="text-slate-700 font-medium">${p.current_medications || 'None reported.'}</div>
        </div>
      </div>

      <div>
        <h3 class="text-xs font-bold text-slate-800 uppercase tracking-wide mb-2.5 flex items-center gap-1.5">
          <span>📋</span> Clinical Notes & Prescriptions History (${recs.length})
        </h3>
        ${recs.length === 0 ? '<p class="text-xs text-slate-400 py-3 italic">No previous consultation notes recorded for this patient.</p>' : `
          <div class="space-y-3 max-h-60 overflow-y-auto pr-1">
            ${recs.map(r => `
              <div class="p-3.5 rounded-xl border border-slate-200 bg-slate-50/60 text-xs">
                <div class="flex items-center justify-between font-bold text-slate-800">
                  <span>${r.diagnosis_title}</span>
                  <span class="text-[11px] text-slate-500 font-normal">${r.record_date_formatted || r.created_at}</span>
                </div>
                <p class="text-slate-600 mt-1 whitespace-pre-line">${r.clinical_notes}</p>
                ${r.prescriptions ? `<div class="mt-2 p-2 bg-white rounded border border-slate-200 font-mono text-[11px] text-teal-900">${r.prescriptions}</div>` : ''}
              </div>
            `).join("")}
          </div>
        `}
      </div>
    `;
  } catch (err) {
    content.innerHTML = `<div class="p-6 text-center text-xs text-rose-600">${err.message}</div>`;
  }
}

function closeDoctorPatientModal() {
  const modal = document.getElementById("doctor-patient-modal");
  if (modal) modal.classList.add("hidden");
}

// ==================== 6. DOCTOR FEEDBACK ====================

async function loadDoctorFeedback() {
  const container = document.getElementById("doctor-feedback-list");
  if (!container) return;

  container.innerHTML = `<div class="p-6 text-center text-xs text-slate-400">Loading feedback...</div>`;

  try {
    const res = await apiCall("/api/feedback");
    const reviews = res.feedback || [];

    if (reviews.length === 0) {
      container.innerHTML = `<p class="p-6 text-center text-xs text-slate-400">No patient reviews received yet.</p>`;
      return;
    }

    container.innerHTML = reviews.map(r => `
      <div class="p-4 rounded-xl bg-white border border-slate-200/90 shadow-sm">
        <div class="flex items-center justify-between text-xs">
          <span class="font-bold text-slate-800">${r.patient_name}</span>
          <span class="text-amber-500 font-bold text-sm">★ ${r.rating}.0</span>
        </div>
        <p class="text-xs text-slate-600 mt-2 leading-relaxed">"${r.comment || 'Patient consultation completed successfully.'}"</p>
        <div class="mt-3 text-[10px] text-slate-400">
          Ref: #${r.reference_id} • Date: ${formatDateIST(r.appointment_date)}
        </div>
      </div>
    `).join("");

  } catch (err) {
    container.innerHTML = `<div class="p-4 text-center text-rose-600 text-xs">${err.message}</div>`;
  }
}
