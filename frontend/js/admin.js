/**
 * SehatSetu - Admin Console & Analytics Controller
 * Handles platform KPIs, Chart.js operational charts, user & doctor moderation, and system settings.
 */

let trendsChartInstance = null;
let specialityChartInstance = null;
let cityChartInstance = null;
let modesChartInstance = null;

// ==================== 1. ADMIN DASHBOARD & CHARTS ====================

async function loadAdminDashboard() {
  const user = AppState.currentUser;
  if (!user || user.role !== "ADMIN") return;

  try {
    const [statsRes, analyticsRes, logsRes] = await Promise.all([
      apiCall("/api/admin/stats"),
      apiCall("/api/admin/analytics"),
      apiCall("/api/admin/activity-logs?limit=5")
    ]);

    const s = statsRes.stats;
    document.getElementById("a-stat-users").textContent = s.total_users;
    document.getElementById("a-stat-doctors").textContent = s.total_doctors;
    document.getElementById("a-stat-patients").textContent = s.total_patients;
    document.getElementById("a-stat-today-apts").textContent = s.today_appointments;
    document.getElementById("a-stat-completed").textContent = s.completed_appointments;
    document.getElementById("a-stat-cancelled").textContent = s.cancelled_appointments;
    document.getElementById("a-stat-success-rate").textContent = `${s.success_rate}%`;

    // Render Dynamic Chart.js charts
    renderAdminCharts(analyticsRes);

    // Top Doctors Table
    const topDocsContainer = document.getElementById("admin-top-doctors-list");
    if (topDocsContainer) {
      topDocsContainer.innerHTML = (analyticsRes.top_doctors || []).map(d => `
        <div class="p-3 rounded-lg border border-slate-100 bg-slate-50/70 flex items-center justify-between text-xs">
          <div>
            <div class="font-bold text-slate-800">${d.full_name}</div>
            <div class="text-[11px] text-slate-500">${d.speciality} • ${d.city}</div>
          </div>
          <div class="text-right">
            <div class="font-bold text-teal-800">${d.appointment_count} appointments</div>
            <div class="text-[11px] text-amber-500 font-semibold">★ ${d.rating}</div>
          </div>
        </div>
      `).join("");
    }

    // Recent Activity Feed
    const logsContainer = document.getElementById("admin-recent-logs-list");
    if (logsContainer) {
      logsContainer.innerHTML = (logsRes.logs || []).map(l => `
        <div class="flex items-start gap-2.5 text-xs pb-3 border-b border-slate-100 last:border-none">
          <span class="text-teal-700 font-bold">●</span>
          <div class="flex-1">
            <p class="text-slate-800 font-medium">${l.details || l.action}</p>
            <div class="text-[10px] text-slate-400 mt-0.5">
              ${l.user_name || 'System'} • ${l.created_at ? l.created_at.slice(11, 16) : ''}
            </div>
          </div>
        </div>
      `).join("");
    }

  } catch (err) {
    console.error("Error loading admin dashboard:", err);
  }
}

function renderAdminCharts(analytics) {
  if (typeof Chart === "undefined") {
    console.warn("Chart.js not loaded yet");
    return;
  }

  // 1. Daily Trends Chart
  const ctxTrends = document.getElementById("chart-trends")?.getContext("2d");
  if (ctxTrends) {
    if (trendsChartInstance) trendsChartInstance.destroy();
    const trendData = analytics.trends || [];
    trendsChartInstance = new Chart(ctxTrends, {
      type: "line",
      data: {
        labels: trendData.map(t => formatDateIST(t.appointment_date)),
        datasets: [
          {
            label: "Total Booked",
            data: trendData.map(t => t.total),
            borderColor: "#0F766E",
            backgroundColor: "rgba(15, 118, 110, 0.1)",
            fill: true,
            tension: 0.3
          },
          {
            label: "Completed",
            data: trendData.map(t => t.completed),
            borderColor: "#10B981",
            backgroundColor: "transparent",
            tension: 0.3
          },
          {
            label: "Cancelled",
            data: trendData.map(t => t.cancelled),
            borderColor: "#F43F5E",
            backgroundColor: "transparent",
            tension: 0.3
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'top', labels: { boxWidth: 12, font: { size: 11 } } } },
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1, font: { size: 10 } } },
          x: { ticks: { font: { size: 10 } } }
        }
      }
    });
  }

  // 2. Speciality Distribution Doughnut Chart
  const ctxSpec = document.getElementById("chart-speciality")?.getContext("2d");
  if (ctxSpec) {
    if (specialityChartInstance) specialityChartInstance.destroy();
    const specData = analytics.by_speciality || [];
    specialityChartInstance = new Chart(ctxSpec, {
      type: "doughnut",
      data: {
        labels: specData.map(s => s.speciality),
        datasets: [{
          data: specData.map(s => s.count),
          backgroundColor: ["#0F766E", "#14B8A6", "#38BDF8", "#F59E0B", "#EC4899", "#8B5CF6", "#10B981"]
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 10 } } } }
      }
    });
  }

  // 3. Appointments by City Bar Chart
  const ctxCity = document.getElementById("chart-city")?.getContext("2d");
  if (ctxCity) {
    if (cityChartInstance) cityChartInstance.destroy();
    const cityData = analytics.by_city || [];
    cityChartInstance = new Chart(ctxCity, {
      type: "bar",
      data: {
        labels: cityData.map(c => c.city),
        datasets: [{
          label: "Appointments",
          data: cityData.map(c => c.count),
          backgroundColor: "#0D9488",
          borderRadius: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { display: false } },
        scales: {
          y: { beginAtZero: true, ticks: { stepSize: 1, font: { size: 10 } } },
          x: { ticks: { font: { size: 10 } } }
        }
      }
    });
  }

  // 4. Consultation Modes Chart
  const ctxModes = document.getElementById("chart-modes")?.getContext("2d");
  if (ctxModes) {
    if (modesChartInstance) modesChartInstance.destroy();
    const modesData = analytics.by_consultation_type || [];
    modesChartInstance = new Chart(ctxModes, {
      type: "pie",
      data: {
        labels: modesData.map(m => m.consultation_type),
        datasets: [{
          data: modesData.map(m => m.count),
          backgroundColor: ["#0F766E", "#38BDF8", "#F59E0B"]
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: { legend: { position: 'bottom', labels: { boxWidth: 10, font: { size: 10 } } } }
      }
    });
  }
}

// ==================== 2. ADMIN USER MANAGEMENT ====================

async function loadAdminUsers() {
  const container = document.getElementById("admin-users-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="7" class="p-6 text-center text-xs text-slate-400">Loading users...</td></tr>`;

  const search = document.getElementById("admin-user-search")?.value.trim() || "";
  const role = document.getElementById("admin-user-role-filter")?.value || "all";
  const status = document.getElementById("admin-user-status-filter")?.value || "all";

  const params = new URLSearchParams();
  if (search) params.append("search", search);
  if (role !== "all") params.append("role", role);
  if (status !== "all") params.append("status", status);

  try {
    const res = await apiCall(`/api/admin/users?${params.toString()}`);
    const users = res.users || [];

    if (users.length === 0) {
      container.innerHTML = `<tr><td colspan="7" class="p-8 text-center text-xs text-slate-500">No users found.</td></tr>`;
      return;
    }

    container.innerHTML = users.map(u => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5">
          <div class="font-bold text-slate-900">${u.full_name}</div>
          <div class="text-[11px] text-slate-400">${u.email}</div>
        </td>
        <td class="p-3.5 font-medium">${u.mobile || '-'}</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
            u.role === 'ADMIN' ? 'bg-purple-100 text-purple-800' :
            (u.role === 'DOCTOR' ? 'bg-teal-100 text-teal-800' : 'bg-blue-100 text-blue-800')
          }">
            ${u.role}
          </span>
        </td>
        <td class="p-3.5">${u.city || '-'}</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-semibold ${u.status === 'ACTIVE' ? 'bg-emerald-100 text-emerald-800' : 'bg-rose-100 text-rose-800'}">
            ${u.status}
          </span>
        </td>
        <td class="p-3.5 font-semibold text-slate-700">${u.appointment_count || 0}</td>
        <td class="p-3.5 text-right space-x-1 whitespace-nowrap">
          ${u.role !== 'ADMIN' ? `
            <button onclick="toggleUserStatus(${u.id}, '${u.status === 'ACTIVE' ? 'SUSPENDED' : 'ACTIVE'}')" 
              class="px-2.5 py-1 text-xs font-medium rounded ${u.status === 'ACTIVE' ? 'text-amber-700 bg-amber-50 hover:bg-amber-100' : 'text-emerald-700 bg-emerald-50 hover:bg-emerald-100'} transition">
              ${u.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
            </button>
            <button onclick="deleteUserAccount(${u.id}, '${u.email}')" 
              class="px-2 py-1 text-xs font-medium text-rose-700 hover:bg-rose-50 rounded transition">
              Delete
            </button>
          ` : '<span class="text-slate-400 text-[11px] italic">Admin Protected</span>'}
        </td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="7" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}

async function toggleUserStatus(userId, newStatus) {
  try {
    await apiCall(`/api/admin/users/${userId}/status`, {
      method: "PUT",
      body: JSON.stringify({ status: newStatus })
    });
    showToast(`User status updated to ${newStatus}.`, "success");
    loadAdminUsers();
  } catch (err) {
    showToast(err.message, "error");
  }
}

async function deleteUserAccount(userId, email) {
  if (!confirm(`Are you sure you want to permanently delete user ${email}? This action cannot be undone.`)) {
    return;
  }
  try {
    await apiCall(`/api/admin/users/${userId}`, { method: "DELETE" });
    showToast("User permanently deleted.", "success");
    loadAdminUsers();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 3. ADMIN DOCTOR MANAGEMENT ====================

async function loadAdminDoctors() {
  const container = document.getElementById("admin-doctors-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="8" class="p-6 text-center text-xs text-slate-400">Loading doctors...</td></tr>`;

  try {
    const res = await apiCall("/api/admin/doctors");
    const doctors = res.doctors || [];

    if (doctors.length === 0) {
      container.innerHTML = `<tr><td colspan="8" class="p-8 text-center text-xs text-slate-500">No doctor profiles found.</td></tr>`;
      return;
    }

    container.innerHTML = doctors.map(d => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5">
          <div class="font-bold text-slate-900">${d.full_name}</div>
          <div class="text-[11px] text-slate-400">${d.email}</div>
        </td>
        <td class="p-3.5 font-semibold text-teal-800">${d.speciality}</td>
        <td class="p-3.5">${d.experience_years} Yrs</td>
        <td class="p-3.5 max-w-xs truncate">${d.hospital_clinic}, ${d.clinic_city}</td>
        <td class="p-3.5 font-bold">${formatCurrency(d.consultation_fee)}</td>
        <td class="p-3.5">
          <span class="text-amber-500 font-bold">★ ${d.rating}</span>
          <span class="text-slate-400 text-[10px]">(${d.review_count})</span>
        </td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
            d.verification_status === 'VERIFIED' ? 'badge-verified' :
            (d.verification_status === 'PENDING' ? 'badge-pending' : 'badge-cancelled')
          }">
            ${d.verification_status}
          </span>
        </td>
        <td class="p-3.5 text-right space-x-1 whitespace-nowrap">
          ${d.verification_status !== 'VERIFIED' ? `
            <button onclick="setDoctorVerification(${d.id}, 'VERIFIED')" class="px-2 py-1 bg-emerald-50 text-emerald-700 font-semibold rounded hover:bg-emerald-100 transition">
              Verify
            </button>
          ` : `
            <button onclick="setDoctorVerification(${d.id}, 'PENDING')" class="px-2 py-1 text-slate-600 hover:bg-slate-100 rounded transition">
              Revoke
            </button>
          `}
        </td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="8" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}

async function setDoctorVerification(doctorId, status) {
  try {
    await apiCall(`/api/admin/doctors/${doctorId}/verification`, {
      method: "PUT",
      body: JSON.stringify({ verification_status: status })
    });
    showToast(`Doctor verification set to ${status}.`, "success");
    loadAdminDoctors();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 4. ADMIN APPOINTMENTS ====================

async function loadAdminAppointments() {
  const container = document.getElementById("admin-appointments-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="8" class="p-6 text-center text-xs text-slate-400">Loading master appointments...</td></tr>`;

  const search = document.getElementById("admin-apt-search")?.value.trim() || "";
  const status = document.getElementById("admin-apt-status-filter")?.value || "all";

  const params = new URLSearchParams();
  if (search) params.append("search", search);
  if (status !== "all") params.append("status", status);

  try {
    const res = await apiCall(`/api/admin/appointments?${params.toString()}`);
    const apts = res.appointments || [];

    if (apts.length === 0) {
      container.innerHTML = `<tr><td colspan="8" class="p-8 text-center text-xs text-slate-500">No appointments found.</td></tr>`;
      return;
    }

    container.innerHTML = apts.map(a => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5 font-mono text-slate-500">${a.reference_id}</td>
        <td class="p-3.5 font-semibold text-slate-800">${a.patient_name}</td>
        <td class="p-3.5">
          <div class="font-bold text-slate-900">${a.doctor_name}</div>
          <div class="text-[11px] text-teal-700">${a.speciality}</div>
        </td>
        <td class="p-3.5">
          <div>${a.appointment_date_formatted}</div>
          <div class="text-slate-400">${a.appointment_time}</div>
        </td>
        <td class="p-3.5">${a.consultation_type}</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
            a.status === 'Confirmed' ? 'badge-confirmed' :
            (a.status === 'Completed' ? 'badge-completed' :
            (a.status === 'Cancelled' ? 'badge-cancelled' : 'badge-requested'))
          }">
            ${a.status}
          </span>
        </td>
        <td class="p-3.5 max-w-xs truncate">${a.reason_for_visit}</td>
        <td class="p-3.5 text-right whitespace-nowrap">
          ${a.status !== 'Cancelled' ? `
            <button onclick="adminCancelAppointment(${a.id})" class="px-2 py-1 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 rounded transition">
              Cancel
            </button>
          ` : '<span class="text-slate-400 text-[11px]">Cancelled</span>'}
        </td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="8" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}

async function adminCancelAppointment(aptId) {
  const reason = prompt("Enter administrative cancellation reason:", "Administrative scheduling conflict");
  if (!reason) return;

  try {
    await apiCall(`/api/appointments/${aptId}/cancel`, {
      method: "PUT",
      body: JSON.stringify({ cancellation_reason: reason, status: "Cancelled" })
    });
    showToast("Appointment cancelled by admin.", "success");
    loadAdminAppointments();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 5. ADMIN SETTINGS ====================

async function loadAdminSettings() {
  const f = document.getElementById("admin-settings-form");
  if (!f) return;

  try {
    const res = await apiCall("/api/admin/settings");
    const s = res.settings || {};

    f.platform_name.value = s.platform_name || "SehatSetu";
    f.tagline.value = s.tagline || "Better Care. Simpler Healthcare.";
    f.default_slot_duration.value = s.default_slot_duration || "30";
    f.advance_booking_days.value = s.advance_booking_days || "30";
    f.emergency_notice.value = s.emergency_notice || "";
    f.support_phone.value = s.support_phone || "";
    f.support_email.value = s.support_email || "";
    f.maintenance_mode.checked = (s.maintenance_mode === "true");

  } catch (err) {
    console.error("Error loading admin settings:", err);
  }
}

async function handleAdminSettingsSubmit(e) {
  e.preventDefault();
  const f = e.target;
  const settings = {
    platform_name: f.platform_name.value.trim(),
    tagline: f.tagline.value.trim(),
    default_slot_duration: f.default_slot_duration.value.trim(),
    advance_booking_days: f.advance_booking_days.value.trim(),
    emergency_notice: f.emergency_notice.value.trim(),
    support_phone: f.support_phone.value.trim(),
    support_email: f.support_email.value.trim(),
    maintenance_mode: f.maintenance_mode.checked ? "true" : "false"
  };

  try {
    await apiCall("/api/admin/settings", {
      method: "PUT",
      body: JSON.stringify({ settings })
    });
    showToast("System configuration settings updated successfully.", "success");
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 6. ADMIN SYSTEM LOGS ====================

async function loadAdminLogs() {
  const container = document.getElementById("admin-logs-table-body");
  if (!container) return;

  container.innerHTML = `<tr><td colspan="5" class="p-6 text-center text-xs text-slate-400">Loading audit trail...</td></tr>`;

  try {
    const res = await apiCall("/api/admin/activity-logs?limit=100");
    const logs = res.logs || [];

    if (logs.length === 0) {
      container.innerHTML = `<tr><td colspan="5" class="p-8 text-center text-xs text-slate-500">No activity logs recorded.</td></tr>`;
      return;
    }

    container.innerHTML = logs.map(l => `
      <tr class="border-b border-slate-100 hover:bg-slate-50/70 transition text-xs">
        <td class="p-3.5 font-mono text-slate-500">${l.created_at || '-'}</td>
        <td class="p-3.5 font-bold text-slate-800">${l.user_name || 'Anonymous'} (${l.user_role || '-'})</td>
        <td class="p-3.5">
          <span class="px-2 py-0.5 rounded text-[10px] font-bold bg-slate-100 text-slate-800 font-mono">${l.action}</span>
        </td>
        <td class="p-3.5 text-slate-700">${l.details}</td>
        <td class="p-3.5 font-mono text-slate-400">${l.ip_address || '127.0.0.1'}</td>
      </tr>
    `).join("");

  } catch (err) {
    container.innerHTML = `<tr><td colspan="5" class="p-4 text-center text-rose-600 text-xs">${err.message}</td></tr>`;
  }
}
