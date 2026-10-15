/**
 * SehatSetu - Patient & Public Discovery Controller
 * Handles doctor search, slot picking, booking flow, appointments, medical records, and feedback.
 */

let selectedDoctorForBooking = null;
let selectedDateForBooking = null;
let selectedSlotForBooking = null;

// ==================== 1. LANDING PAGE ====================

async function loadLandingStats() {
  try {
    const [docsRes, specRes] = await Promise.all([
      apiCall("/api/doctors"),
      apiCall("/api/specialities")
    ]);
    const docs = docsRes.doctors || [];
    const specs = specRes.specialities || [];

    const totalDocsEl = document.getElementById("landing-stat-doctors");
    if (totalDocsEl) totalDocsEl.textContent = `${docs.length}+`;

    // Render Popular Specialities
    const specContainer = document.getElementById("landing-specialities-grid");
    if (specContainer) {
      specContainer.innerHTML = specs.slice(0, 8).map(s => {
        const count = docs.filter(d => d.speciality.toLowerCase() === s.name.toLowerCase()).length;
        return `
          <div class="bg-white p-5 rounded-xl border border-slate-200/80 hover:shadow-md hover:border-teal-500/50 transition cursor-pointer group" onclick="searchBySpeciality('${s.name}')">
            <div class="w-10 h-10 rounded-lg bg-teal-50 text-teal-800 flex items-center justify-center font-bold text-lg mb-3 group-hover:bg-teal-700 group-hover:text-white transition">
              ✚
            </div>
            <h4 class="font-semibold text-slate-800 group-hover:text-teal-800 text-sm transition">${s.name}</h4>
            <p class="text-xs text-slate-500 mt-1 line-clamp-2">${s.description || 'Expert consultation & specialized care.'}</p>
            <div class="mt-3 text-[11px] font-medium text-teal-700 flex items-center gap-1">
              <span>${count} Verified Doctors</span> →
            </div>
          </div>
        `;
      }).join("");
    }
  } catch (e) {
    console.warn("Error loading landing stats:", e);
  }
}

function searchBySpeciality(specName) {
  navigate("doctors");
  setTimeout(() => {
    const specSelect = document.getElementById("filter-speciality");
    if (specSelect) {
      specSelect.value = specName;
      loadDoctorsList();
    }
  }, 50);
}

function handleHeroSearch(e) {
  e.preventDefault();
  const searchInput = document.getElementById("hero-search-input");
  const citySelect = document.getElementById("hero-city-select");
  const query = searchInput ? searchInput.value.trim() : "";
  const city = citySelect ? citySelect.value : "";

  navigate("doctors");
  setTimeout(() => {
    const fSearch = document.getElementById("filter-search");
    const fCity = document.getElementById("filter-city");
    if (fSearch && query) fSearch.value = query;
    if (fCity && city && city !== "all") fCity.value = city;
    loadDoctorsList();
  }, 50);
}

// ==================== 2. DOCTOR DISCOVERY ====================

async function loadDoctorsList() {
  const container = document.getElementById("doctors-cards-container");
  const countEl = document.getElementById("doctors-results-count");
  if (!container) return;

  // Show skeletons
  container.innerHTML = Array(4).fill(0).map(() => `
    <div class="bg-white rounded-xl border border-slate-200 p-5 shadow-sm skeleton-shimmer h-60"></div>
  `).join("");

  const search = document.getElementById("filter-search")?.value.trim() || "";
  const speciality = document.getElementById("filter-speciality")?.value || "all";
  const city = document.getElementById("filter-city")?.value || "all";
  const mode = document.getElementById("filter-mode")?.value || "all";
  const sort_by = document.getElementById("filter-sort")?.value || "rating";

  const params = new URLSearchParams();
  if (search) params.append("search", search);
  if (speciality !== "all") params.append("speciality", speciality);
  if (city !== "all") params.append("city", city);
  if (mode !== "all") params.append("mode", mode);
  if (sort_by) params.append("sort_by", sort_by);

  try {
    const res = await apiCall(`/api/doctors?${params.toString()}`);
    const doctors = res.doctors || [];

    if (countEl) countEl.textContent = `Showing ${doctors.length} verified doctors`;

    if (doctors.length === 0) {
      container.innerHTML = `
        <div class="col-span-full bg-white rounded-xl border border-slate-200 p-12 text-center">
          <div class="w-16 h-16 bg-slate-100 rounded-full flex items-center justify-center mx-auto text-2xl mb-4 text-slate-400">🔍</div>
          <h3 class="text-base font-semibold text-slate-800">No doctors match your current search</h3>
          <p class="text-sm text-slate-500 mt-1 max-w-md mx-auto">Try clearing your filters, searching for another speciality, or selecting a different city.</p>
          <button onclick="resetDoctorFilters()" class="mt-4 px-4 py-2 bg-teal-50 text-teal-800 text-sm font-medium rounded-lg hover:bg-teal-100 transition">Reset All Filters</button>
        </div>
      `;
      return;
    }

    container.innerHTML = doctors.map(doc => `
      <div class="bg-white rounded-xl border border-slate-200/90 hover:border-teal-500/50 hover:shadow-md transition p-5 flex flex-col justify-between">
        <div>
          <!-- Header -->
          <div class="flex items-start gap-4">
            <div class="w-14 h-14 rounded-xl bg-teal-800 text-white font-bold text-lg flex items-center justify-center shrink-0 shadow-sm">
              ${doc.full_name.replace("Dr. ", "").split(" ").map(n => n[0]).join("")}
            </div>
            <div class="flex-1 min-w-0">
              <div class="flex items-center gap-1.5 flex-wrap">
                <h3 class="font-bold text-slate-900 text-base truncate">${doc.full_name}</h3>
                <span class="inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-semibold bg-teal-100 text-teal-800">
                  ✓ Verified
                </span>
              </div>
              <p class="text-xs font-medium text-teal-700 mt-0.5">${doc.speciality}</p>
              <p class="text-xs text-slate-500 truncate mt-0.5" title="${doc.qualifications}">${doc.qualifications}</p>
            </div>
          </div>

          <!-- Metadata -->
          <div class="mt-4 space-y-1.5 text-xs text-slate-600">
            <div class="flex items-center gap-2">
              <span class="text-slate-400">🏥</span>
              <span class="truncate">${doc.hospital_clinic}, ${doc.clinic_city}</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-slate-400">⏳</span>
              <span>${doc.experience_years} years experience</span>
            </div>
            <div class="flex items-center gap-2">
              <span class="text-slate-400">🗣️</span>
              <span class="truncate">${doc.languages}</span>
            </div>
          </div>

          <!-- Tags & Modes -->
          <div class="mt-3 flex flex-wrap gap-1.5">
            ${(doc.consultation_modes || []).map(m => `
              <span class="px-2 py-0.5 rounded text-[11px] bg-slate-100 text-slate-700 font-medium">
                ${m === 'In-Clinic' ? '🏥 In-Clinic' : '💻 Video Consult'}
              </span>
            `).join("")}
          </div>
        </div>

        <!-- Footer & Booking CTA -->
        <div class="mt-5 pt-4 border-t border-slate-100 flex items-center justify-between gap-2">
          <div>
            <div class="flex items-center gap-1 text-xs">
              <span class="text-amber-500 font-bold">★ ${doc.rating}</span>
              <span class="text-slate-400">(${doc.review_count})</span>
            </div>
            <div class="text-sm font-bold text-slate-900 mt-0.5">
              ${formatCurrency(doc.consultation_fee)} <span class="text-[11px] font-normal text-slate-500">fee</span>
            </div>
          </div>

          <div class="flex items-center gap-2">
            <button onclick="navigate('doctor-profile?id=${doc.id}')" class="px-3 py-2 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition">
              Profile
            </button>
            <button onclick="navigate('doctor-profile?id=${doc.id}')" class="px-3.5 py-2 text-xs font-semibold text-white bg-teal-800 hover:bg-teal-700 rounded-lg shadow-sm transition">
              Book Slot
            </button>
          </div>
        </div>
      </div>
    `).join("");

  } catch (err) {
    container.innerHTML = `<div class="col-span-full p-8 text-center text-rose-600 text-sm">Failed to load doctors: ${err.message}</div>`;
  }
}

function resetDoctorFilters() {
  const fSearch = document.getElementById("filter-search");
  const fSpec = document.getElementById("filter-speciality");
  const fCity = document.getElementById("filter-city");
  const fMode = document.getElementById("filter-mode");
  const fSort = document.getElementById("filter-sort");

  if (fSearch) fSearch.value = "";
  if (fSpec) fSpec.value = "all";
  if (fCity) fCity.value = "all";
  if (fMode) fMode.value = "all";
  if (fSort) fSort.value = "rating";
  loadDoctorsList();
}

// ==================== 3. DOCTOR PROFILE & DYNAMIC SLOTS ====================

async function loadDoctorProfileView(doctorId) {
  if (!doctorId) {
    navigate("doctors");
    return;
  }

  const container = document.getElementById("doctor-profile-content");
  if (!container) return;

  container.innerHTML = `<div class="p-12 text-center text-slate-400 text-sm">Loading doctor profile and available slots...</div>`;

  try {
    const res = await apiCall(`/api/doctors/${doctorId}`);
    const doc = res.doctor;
    selectedDoctorForBooking = doc;

    // Default to today or tomorrow
    const today = new Date();
    const todayStr = today.toISOString().split("T")[0];
    selectedDateForBooking = todayStr;

    container.innerHTML = `
      <!-- Top Card -->
      <div class="bg-white rounded-xl border border-slate-200/90 p-6 shadow-sm">
        <div class="flex flex-col md:flex-row md:items-center justify-between gap-6">
          <div class="flex items-start gap-5">
            <div class="w-20 h-20 rounded-2xl bg-teal-800 text-white font-bold text-2xl flex items-center justify-center shrink-0 shadow-md">
              ${doc.full_name.replace("Dr. ", "").split(" ").map(n => n[0]).join("")}
            </div>
            <div>
              <div class="flex items-center gap-2 flex-wrap">
                <h1 class="text-xl font-bold text-slate-900">${doc.full_name}</h1>
                <span class="badge-verified px-2 py-0.5 rounded text-xs font-semibold">✓ Verified Doctor</span>
              </div>
              <p class="text-sm font-medium text-teal-700 mt-1">${doc.speciality} • ${doc.experience_years} Years Experience</p>
              <p class="text-xs text-slate-600 mt-1">${doc.qualifications}</p>
              <div class="flex items-center gap-4 mt-2 text-xs text-slate-500">
                <span>🏥 ${doc.hospital_clinic}, ${doc.clinic_city}</span>
                <span>🗣️ ${doc.languages}</span>
              </div>
            </div>
          </div>

          <div class="bg-slate-50 p-4 rounded-xl border border-slate-200/80 md:text-right shrink-0">
            <div class="text-xs text-slate-500">Consultation Fee</div>
            <div class="text-2xl font-bold text-slate-900 mt-0.5">${formatCurrency(doc.consultation_fee)}</div>
            <div class="flex items-center md:justify-end gap-1 text-xs font-semibold text-amber-500 mt-1">
              ★ ${doc.rating} <span class="text-slate-400 font-normal">(${doc.review_count} reviews)</span>
            </div>
          </div>
        </div>

        <!-- Bio -->
        <div class="mt-6 pt-5 border-t border-slate-100">
          <h4 class="text-xs font-semibold text-slate-400 uppercase tracking-wider">About Doctor</h4>
          <p class="text-sm text-slate-700 mt-1 leading-relaxed">${doc.bio || 'Dedicated medical professional.'}</p>
        </div>
      </div>

      <!-- Schedule & Slot Selection Engine -->
      <div class="mt-6 bg-white rounded-xl border border-slate-200/90 p-6 shadow-sm">
        <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-100">
          <div>
            <h2 class="text-base font-bold text-slate-900 flex items-center gap-2">
              <span>📅</span> Available Appointment Slots
            </h2>
            <p class="text-xs text-slate-500 mt-0.5">Select a date and choose an available consultation time.</p>
          </div>
          <div class="flex items-center gap-2">
            <label class="text-xs text-slate-500 font-medium">Select Date:</label>
            <input type="date" id="profile-date-picker" value="${todayStr}" min="${todayStr}" 
              onchange="changeSlotDate(${doc.id}, this.value)"
              class="text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 font-medium text-slate-800 focus:ring-1 focus:ring-teal-700 outline-none">
          </div>
        </div>

        <!-- Quick Date Pills (Next 5 Days) -->
        <div class="mt-4 flex items-center gap-2 overflow-x-auto pb-2" id="profile-date-tabs">
          <!-- Populated dynamically -->
        </div>

        <!-- Slots Container -->
        <div id="profile-slots-grid" class="mt-5">
          <div class="p-6 text-center text-slate-400 text-xs">Loading available slots...</div>
        </div>
      </div>

      <!-- Reviews Section -->
      <div class="mt-6 bg-white rounded-xl border border-slate-200/90 p-6 shadow-sm">
        <h2 class="text-base font-bold text-slate-900 mb-4">Patient Reviews & Feedback (${doc.reviews?.length || 0})</h2>
        <div class="space-y-4">
          ${(doc.reviews && doc.reviews.length > 0) ? doc.reviews.map(r => `
            <div class="p-4 rounded-xl bg-slate-50 border border-slate-100">
              <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-slate-800">${r.patient_name}</span>
                <span class="text-amber-500 font-bold">★ ${r.rating}.0</span>
              </div>
              <p class="text-xs text-slate-600 mt-2 leading-relaxed">"${r.comment || 'Consultation went smoothly.'}"</p>
            </div>
          `).join("") : `
            <p class="text-xs text-slate-400 italic">No reviews yet for this doctor.</p>
          `}
        </div>
      </div>
    `;

    renderDateTabs(doc.id, todayStr);
    fetchDoctorSlots(doc.id, todayStr);

  } catch (err) {
    container.innerHTML = `<div class="p-8 text-center text-rose-600 text-sm">Failed to load doctor profile: ${err.message}</div>`;
  }
}

function renderDateTabs(doctorId, selectedDate) {
  const container = document.getElementById("profile-date-tabs");
  if (!container) return;

  const dates = [];
  const today = new Date();

  for (let i = 0; i < 7; i++) {
    const d = new Date(today);
    d.setDate(today.getDate() + i);
    const dateStr = d.toISOString().split("T")[0];
    const dayName = i === 0 ? "Today" : (i === 1 ? "Tomorrow" : d.toLocaleDateString('en-IN', { weekday: 'short' }));
    const displayDate = d.toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });
    dates.push({ dateStr, dayName, displayDate });
  }

  container.innerHTML = dates.map(item => `
    <button onclick="changeSlotDate(${doctorId}, '${item.dateStr}')" 
      class="px-3.5 py-2 rounded-lg text-xs font-medium border transition shrink-0 text-center ${item.dateStr === selectedDate ? 'bg-teal-800 text-white border-teal-800 shadow-sm' : 'bg-slate-50 text-slate-700 border-slate-200 hover:bg-slate-100'}">
      <div class="font-semibold">${item.dayName}</div>
      <div class="text-[10px] opacity-80">${item.displayDate}</div>
    </button>
  `).join("");
}

function changeSlotDate(doctorId, newDateStr) {
  selectedDateForBooking = newDateStr;
  const picker = document.getElementById("profile-date-picker");
  if (picker) picker.value = newDateStr;
  renderDateTabs(doctorId, newDateStr);
  fetchDoctorSlots(doctorId, newDateStr);
}

async function fetchDoctorSlots(doctorId, dateStr) {
  const grid = document.getElementById("profile-slots-grid");
  if (!grid) return;

  grid.innerHTML = `<div class="p-6 text-center text-slate-400 text-xs">Checking real-time slot availability for ${formatDateIST(dateStr)}...</div>`;

  try {
    const res = await apiCall(`/api/doctors/${doctorId}/slots?date=${dateStr}`);
    const slots = res.slots || [];

    if (res.is_blocked) {
      grid.innerHTML = `
        <div class="p-6 bg-amber-50 rounded-xl border border-amber-200 text-center text-amber-800 text-xs font-medium">
          ⚠️ ${res.message || 'Doctor is unavailable on this date.'}
        </div>
      `;
      return;
    }

    if (res.is_past) {
      grid.innerHTML = `
        <div class="p-6 bg-slate-50 rounded-xl border border-slate-200 text-center text-slate-500 text-xs">
          Appointments cannot be scheduled for past dates.
        </div>
      `;
      return;
    }

    if (slots.length === 0) {
      grid.innerHTML = `
        <div class="p-6 bg-slate-50 rounded-xl border border-slate-200 text-center text-slate-500 text-xs">
          ${res.message || 'No scheduled consultation hours on this day. Please select another date.'}
        </div>
      `;
      return;
    }

    const availCount = slots.filter(s => s["is_available"]).length;

    grid.innerHTML = `
      <div class="flex items-center justify-between mb-3 text-xs">
        <span class="font-semibold text-slate-700">${availCount} slots available on ${formatDateIST(dateStr)}</span>
        <div class="flex items-center gap-3 text-[11px] text-slate-500">
          <span class="flex items-center gap-1"><span class="w-2.5 h-2.5 rounded-full bg-teal-500 inline-block"></span> Available</span>
          <span class="flex items-center gap-1"><span class="w-2.5 h-2.5 rounded-full bg-slate-300 inline-block"></span> Booked</span>
        </div>
      </div>

      <div class="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-6 gap-2.5">
        ${slots.map(s => {
          if (s.is_available) {
            return `
              <button onclick="selectBookingSlot('${s.time_24}', '${s.time_12}')" 
                class="slot-pill slot-available py-2 px-3 rounded-lg text-xs font-semibold text-center">
                ${s.time_12}
              </button>
            `;
          } else {
            return `
              <div class="slot-pill slot-booked py-2 px-3 rounded-lg text-xs text-center" title="${s.reason}">
                ${s.time_12}
              </div>
            `;
          }
        }).join("")}
      </div>
    `;

  } catch (err) {
    grid.innerHTML = `<div class="p-6 text-center text-rose-600 text-xs">Failed to load slots: ${err.message}</div>`;
  }
}

function selectBookingSlot(time24, time12) {
  if (!AppState.currentUser) {
    showToast("Please log in to book your appointment.", "warning");
    openAuthModal("login");
    return;
  }
  if (AppState.currentUser.role !== "PATIENT") {
    showToast("Only patients can book consultations. Please switch to a patient account.", "warning");
    return;
  }

  selectedSlotForBooking = { time24, time12 };
  openBookingConfirmModal();
}

function openBookingConfirmModal() {
  const modal = document.getElementById("booking-modal");
  if (!modal || !selectedDoctorForBooking || !selectedSlotForBooking) return;

  document.getElementById("bm-doc-name").textContent = selectedDoctorForBooking.full_name;
  document.getElementById("bm-doc-spec").textContent = selectedDoctorForBooking.speciality;
  document.getElementById("bm-doc-clinic").textContent = `${selectedDoctorForBooking.hospital_clinic}, ${selectedDoctorForBooking.clinic_city}`;
  document.getElementById("bm-doc-fee").textContent = formatCurrency(selectedDoctorForBooking.consultation_fee);
  document.getElementById("bm-date").textContent = formatDateIST(selectedDateForBooking);
  document.getElementById("bm-time").textContent = selectedSlotForBooking.time12;

  // Clear reason input
  const reasonInput = document.getElementById("bm-reason");
  if (reasonInput) reasonInput.value = "";

  modal.classList.remove("hidden");
}

function closeBookingModal() {
  const modal = document.getElementById("booking-modal");
  if (modal) modal.classList.add("hidden");
}

async function handleConfirmBookingSubmit(e) {
  e.preventDefault();
  if (!selectedDoctorForBooking || !selectedSlotForBooking) return;

  const form = e.target;
  const reason = form.reason.value.trim();
  const consultType = form.consultation_type.value;
  const btn = document.getElementById("bm-submit-btn");

  if (!reason) {
    showToast("Please enter a brief reason for your consultation.", "warning");
    return;
  }

  try {
    btn.disabled = true;
    btn.textContent = "Confirming Slot...";

    const res = await apiCall("/api/appointments/book", {
      method: "POST",
      body: JSON.stringify({
        doctor_id: selectedDoctorForBooking.id,
        appointment_date: selectedDateForBooking,
        appointment_time: selectedSlotForBooking.time24,
        consultation_type: consultType,
        reason_for_visit: reason
      })
    });

    closeBookingModal();
    showToast(`Appointment booked successfully! Reference: ${res.reference_id}`, "success");
    
    // Refresh slots
    fetchDoctorSlots(selectedDoctorForBooking.id, selectedDateForBooking);

    // Redirect to patient appointments
    navigate("patient-appointments");

  } catch (err) {
    showToast(err.message, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = "Confirm Appointment";
  }
}

// ==================== 4. PATIENT DASHBOARD ====================

async function loadPatientDashboard() {
  const user = AppState.currentUser;
  if (!user) return;

  // Greeting
  const greetingEl = document.getElementById("patient-greeting-name");
  if (greetingEl) greetingEl.textContent = user.full_name.split(" ")[0];

  try {
    const [aptsRes, recsRes] = await Promise.all([
      apiCall("/api/appointments/my"),
      apiCall("/api/medical-records")
    ]);

    const apts = aptsRes.appointments || [];
    const recs = recsRes.records || [];

    const upcoming = apts.filter(a => a.status === 'Confirmed' || a.status === 'Requested');
    const completed = apts.filter(a => a.status === 'Completed');
    const distinctDoctors = new Set(apts.map(a => a.doctor_id)).size;

    // Set Dashboard Stats Cards
    const statUpcoming = document.getElementById("p-stat-upcoming");
    const statCompleted = document.getElementById("p-stat-completed");
    const statRecords = document.getElementById("p-stat-records");
    const statDoctors = document.getElementById("p-stat-doctors");

    if (statUpcoming) statUpcoming.textContent = upcoming.length;
    if (statCompleted) statCompleted.textContent = completed.length;
    if (statRecords) statRecords.textContent = recs.length;
    if (statDoctors) statDoctors.textContent = distinctDoctors;

    // Next Upcoming Appointment Focus Card
    const nextAptCard = document.getElementById("patient-next-appointment-card");
    if (nextAptCard) {
      if (upcoming.length > 0) {
        const next = upcoming[0];
        nextAptCard.innerHTML = `
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div class="flex items-start gap-4">
              <div class="w-12 h-12 rounded-xl bg-teal-800 text-white font-bold text-base flex items-center justify-center shrink-0 shadow-sm">
                ${next.doctor_name.replace("Dr. ", "").split(" ").map(n => n[0]).join("")}
              </div>
              <div>
                <div class="flex items-center gap-2">
                  <h4 class="font-bold text-slate-900 text-base">${next.doctor_name}</h4>
                  <span class="badge-confirmed px-2 py-0.5 rounded text-[11px] font-semibold">${next.status}</span>
                </div>
                <p class="text-xs font-medium text-teal-700 mt-0.5">${next.speciality} • ${next.hospital_clinic}</p>
                <div class="mt-2 flex items-center gap-3 text-xs text-slate-600">
                  <span class="font-semibold text-slate-800">📅 ${next.appointment_date_formatted}</span>
                  <span class="font-semibold text-slate-800">⏰ ${next.appointment_time_12h}</span>
                  <span class="text-slate-500">(${next.consultation_type})</span>
                </div>
              </div>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <button onclick="navigate('patient-appointments')" class="px-3.5 py-2 text-xs font-semibold text-white bg-teal-800 hover:bg-teal-700 rounded-lg shadow-sm transition">
                View Appointments
              </button>
            </div>
          </div>
        `;
      } else {
        nextAptCard.innerHTML = `
          <div class="text-center py-6">
            <p class="text-xs text-slate-500">You don't have any upcoming appointments.</p>
            <button onclick="navigate('doctors')" class="mt-3 px-4 py-2 text-xs font-semibold text-white bg-teal-800 hover:bg-teal-700 rounded-lg shadow-sm transition">
              Find a Doctor & Book
            </button>
          </div>
        `;
      }
    }

    // Recent Medical Records Preview
    const recentRecsList = document.getElementById("patient-recent-records-list");
    if (recentRecsList) {
      if (recs.length > 0) {
        recentRecsList.innerHTML = recs.slice(0, 3).map(r => `
          <div class="p-3.5 rounded-lg border border-slate-100 bg-slate-50/70 flex items-center justify-between gap-4">
            <div>
              <div class="text-xs font-bold text-slate-800">${r.diagnosis_title}</div>
              <div class="text-[11px] text-slate-500 mt-0.5">${r.doctor_name} (${r.speciality}) • ${r.record_date_formatted}</div>
            </div>
            <button onclick="navigate('patient-records')" class="text-xs font-medium text-teal-700 hover:underline shrink-0">
              View Notes →
            </button>
          </div>
        `).join("");
      } else {
        recentRecsList.innerHTML = `<p class="text-xs text-slate-400 py-4 text-center">No consultation notes recorded yet.</p>`;
      }
    }

    // Render Recent Health Vitals Grid
    const vitalsGrid = document.getElementById("patient-vitals-grid");
    if (vitalsGrid) {
      const p = user.patient_profile || {};
      const bp = (p.bp_systolic && p.bp_diastolic) ? `${p.bp_systolic}/${p.bp_diastolic} mmHg` : '120/80 mmHg';
      const pulse = p.pulse_rate ? `${p.pulse_rate} bpm` : '72 bpm';
      const temp = p.temperature_f ? `${p.temperature_f} °F` : '98.6 °F';
      const spo2 = p.spo2_percent ? `${p.spo2_percent}%` : '99%';
      const ht = p.height_cm ? `${p.height_cm} cm` : '175 cm';
      const wt = p.weight_kg ? `${p.weight_kg} kg` : '72 kg';

      vitalsGrid.innerHTML = `
        <div class="p-2.5 rounded-lg bg-rose-50 border border-rose-100">
          <div class="text-[10px] text-rose-700 font-bold uppercase">Blood Pressure</div>
          <div class="text-sm font-black text-rose-900 mt-0.5">${bp}</div>
        </div>
        <div class="p-2.5 rounded-lg bg-emerald-50 border border-emerald-100">
          <div class="text-[10px] text-emerald-700 font-bold uppercase">Pulse / Heart Rate</div>
          <div class="text-sm font-black text-emerald-900 mt-0.5">${pulse}</div>
        </div>
        <div class="p-2.5 rounded-lg bg-amber-50 border border-amber-100">
          <div class="text-[10px] text-amber-700 font-bold uppercase">Temperature</div>
          <div class="text-sm font-black text-amber-900 mt-0.5">${temp}</div>
        </div>
        <div class="p-2.5 rounded-lg bg-blue-50 border border-blue-100">
          <div class="text-[10px] text-blue-700 font-bold uppercase">Oxygen (SpO2)</div>
          <div class="text-sm font-black text-blue-900 mt-0.5">${spo2}</div>
        </div>
        <div class="p-2.5 rounded-lg bg-slate-100 border border-slate-200">
          <div class="text-[10px] text-slate-500 font-bold uppercase">Height</div>
          <div class="text-xs font-bold text-slate-800 mt-0.5">${ht}</div>
        </div>
        <div class="p-2.5 rounded-lg bg-slate-100 border border-slate-200">
          <div class="text-[10px] text-slate-500 font-bold uppercase">Weight</div>
          <div class="text-xs font-bold text-slate-800 mt-0.5">${wt}</div>
        </div>
      `;
    }

  } catch (err) {
    console.error("Error loading patient dashboard:", err);
  }
}

// ==================== 5. PATIENT APPOINTMENTS MANAGEMENT ====================

let patientAppointmentsCache = [];

async function loadPatientAppointments(tab = "all") {
  const container = document.getElementById("patient-appointments-list");
  if (!container) return;

  container.innerHTML = `<div class="p-8 text-center text-slate-400 text-xs">Loading appointments...</div>`;

  // Update tabs UI
  document.querySelectorAll(".p-apt-tab-btn").forEach(btn => {
    btn.classList.remove("border-teal-700", "text-teal-800", "font-bold");
    if (btn.dataset.tab === tab) {
      btn.classList.add("border-teal-700", "text-teal-800", "font-bold");
    }
  });

  try {
    const res = await apiCall(`/api/appointments/my?tab=${tab}`);
    patientAppointmentsCache = Array.isArray(res) ? res : (res.appointments || []);

    if (patientAppointmentsCache.length === 0) {
      container.innerHTML = `
        <div class="bg-white rounded-xl border border-slate-200 p-10 text-center">
          <p class="text-sm font-medium text-slate-600">No appointments found in this category.</p>
          <button onclick="navigate('doctors')" class="mt-4 px-4 py-2 bg-teal-800 text-white text-xs font-semibold rounded-lg shadow-sm hover:bg-teal-700 transition">
            Book an Appointment
          </button>
        </div>
      `;
      return;
    }

    container.innerHTML = patientAppointmentsCache.map(apt => `
      <div class="bg-white rounded-xl border border-slate-200/90 p-5 shadow-sm hover:border-slate-300 transition flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div class="flex items-start gap-4">
          <div class="w-12 h-12 rounded-xl bg-teal-800 text-white font-bold text-base flex items-center justify-center shrink-0 shadow-sm">
            ${apt.doctor_name.replace("Dr. ", "").split(" ").map(n => n[0]).join("")}
          </div>
          <div>
            <div class="flex items-center gap-2 flex-wrap">
              <h4 class="font-bold text-slate-900 text-sm">${apt.doctor_name}</h4>
              <span class="text-xs text-slate-400 font-mono">#${apt.reference_id}</span>
              <span class="px-2 py-0.5 rounded text-[10px] font-bold uppercase ${
                apt.status === 'Confirmed' ? 'badge-confirmed' :
                (apt.status === 'Completed' ? 'badge-completed' :
                (apt.status === 'Cancelled' ? 'badge-cancelled' : 'badge-requested'))
              }">
                ${apt.status}
              </span>
            </div>
            <p class="text-xs text-teal-700 font-medium mt-0.5">${apt.speciality} • ${apt.hospital_clinic}</p>
            <div class="mt-2 flex items-center gap-4 text-xs text-slate-600">
              <span>📅 <strong>${apt.appointment_date_formatted}</strong></span>
              <span>⏰ <strong>${apt.appointment_time_12h}</strong></span>
              <span>🏥 ${apt.consultation_type}</span>
            </div>
            <p class="text-xs text-slate-500 mt-1 italic">Reason: "${apt.reason_for_visit}"</p>
            ${apt.cancellation_reason ? `<p class="text-xs text-rose-600 mt-1">Cancellation Reason: ${apt.cancellation_reason}</p>` : ''}
          </div>
        </div>

        <!-- Actions -->
        <div class="flex items-center gap-2 shrink-0 flex-wrap">
          ${(apt.status === 'Confirmed' || apt.status === 'Requested') ? `
            <button onclick="openRescheduleModal(${apt.id}, ${apt.doctor_id}, '${apt.reference_id}')" class="px-3 py-1.5 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition">
              Reschedule
            </button>
            <button onclick="openCancelAppointmentModal(${apt.id}, '${apt.reference_id}')" class="px-3 py-1.5 text-xs font-semibold text-rose-700 bg-rose-50 hover:bg-rose-100 rounded-lg transition">
              Cancel
            </button>
          ` : ''}

          ${(apt.status === 'Completed') ? `
            ${!apt.feedback_id ? `
              <button onclick="openFeedbackModal(${apt.id}, '${apt.doctor_name}', '${apt.reference_id}')" class="px-3 py-1.5 text-xs font-semibold text-amber-700 bg-amber-50 hover:bg-amber-100 rounded-lg transition">
                ★ Rate Doctor
              </button>
            ` : `
              <span class="text-xs font-medium text-emerald-700 bg-emerald-50 px-2 py-1 rounded">✓ Reviewed (${apt.feedback_rating}★)</span>
            `}

            ${apt.medical_record_id ? `
              <button onclick="navigate('patient-records')" class="px-3 py-1.5 text-xs font-semibold text-teal-800 bg-teal-50 hover:bg-teal-100 rounded-lg transition">
                View Prescription
              </button>
            ` : ''}
          ` : ''}
        </div>
      </div>
    `).join("");

  } catch (err) {
    container.innerHTML = `<div class="p-8 text-center text-rose-600 text-xs">Failed to load appointments: ${err.message}</div>`;
  }
}

// ==================== 6. RESCHEDULE & CANCEL MODALS ====================

let rescheduleAptId = null;
let rescheduleDoctorId = null;

async function openRescheduleModal(appointmentId, doctorId, referenceId) {
  rescheduleAptId = appointmentId;
  rescheduleDoctorId = doctorId;

  const modal = document.getElementById("reschedule-modal");
  if (!modal) return;

  document.getElementById("resched-ref-id").textContent = referenceId;
  
  const todayStr = new Date().toISOString().split("T")[0];
  const dateInput = document.getElementById("resched-date-input");
  if (dateInput) {
    dateInput.value = todayStr;
    dateInput.min = todayStr;
  }

  modal.classList.remove("hidden");
  loadRescheduleSlots(doctorId, todayStr);
}

function closeRescheduleModal() {
  const modal = document.getElementById("reschedule-modal");
  if (modal) modal.classList.add("hidden");
  rescheduleAptId = null;
}

async function loadRescheduleSlots(doctorId, dateStr) {
  const container = document.getElementById("resched-slots-container");
  if (!container) return;

  container.innerHTML = `<div class="p-4 text-center text-xs text-slate-400">Loading available slots...</div>`;

  try {
    const res = await apiCall(`/api/doctors/${doctorId}/slots?date=${dateStr}`);
    const availSlots = (res.slots || []).filter(s => s.is_available);

    if (availSlots.length === 0) {
      container.innerHTML = `<div class="p-4 text-center text-xs text-amber-700 bg-amber-50 rounded-lg">No slots available on this date. Please pick another date.</div>`;
      return;
    }

    container.innerHTML = `
      <div class="grid grid-cols-3 sm:grid-cols-4 gap-2">
        ${availSlots.map(s => `
          <button type="button" onclick="confirmRescheduleSelection('${dateStr}', '${s.time_24}', '${s.time_12}')" 
            class="py-2 px-3 text-xs font-semibold rounded-lg bg-teal-50 text-teal-800 hover:bg-teal-700 hover:text-white transition">
            ${s.time_12}
          </button>
        `).join("")}
      </div>
    `;
  } catch (err) {
    container.innerHTML = `<div class="p-4 text-center text-xs text-rose-600">${err.message}</div>`;
  }
}

async function confirmRescheduleSelection(dateStr, time24, time12) {
  if (!confirm(`Are you sure you want to reschedule this appointment to ${formatDateIST(dateStr)} at ${time12}?`)) {
    return;
  }

  try {
    await apiCall(`/api/appointments/${rescheduleAptId}/reschedule`, {
      method: "PUT",
      body: JSON.stringify({
        appointment_date: dateStr,
        appointment_time: time24
      })
    });

    closeRescheduleModal();
    showToast(`Appointment rescheduled to ${formatDateIST(dateStr)} at ${time12}!`, "success");
    loadPatientAppointments();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// Cancel Modal
let cancelTargetAptId = null;

function openCancelAppointmentModal(appointmentId, referenceId) {
  cancelTargetAptId = appointmentId;
  const modal = document.getElementById("cancel-modal");
  if (!modal) return;
  document.getElementById("cancel-modal-ref").textContent = referenceId;
  const reasonEl = document.getElementById("cancel-modal-reason");
  if (reasonEl) reasonEl.value = "";
  modal.classList.remove("hidden");
}

function closeCancelModal() {
  const modal = document.getElementById("cancel-modal");
  if (modal) modal.classList.add("hidden");
  cancelTargetAptId = null;
}

async function handleCancelAppointmentSubmit(e) {
  e.preventDefault();
  if (!cancelTargetAptId) return;

  const reason = document.getElementById("cancel-modal-reason").value.trim() || "Patient personal conflict";

  try {
    await apiCall(`/api/appointments/${cancelTargetAptId}/cancel`, {
      method: "PUT",
      body: JSON.stringify({ cancellation_reason: reason, status: "Cancelled" })
    });

    closeCancelModal();
    showToast("Appointment successfully cancelled. The slot has been released.", "success");
    loadPatientAppointments();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// Feedback Modal
let feedbackAptId = null;
let selectedFeedbackRating = 5;

function openFeedbackModal(appointmentId, doctorName, referenceId) {
  feedbackAptId = appointmentId;
  selectedFeedbackRating = 5;

  const modal = document.getElementById("feedback-modal");
  if (!modal) return;

  document.getElementById("fb-doc-name").textContent = doctorName;
  document.getElementById("fb-ref-id").textContent = referenceId;
  document.getElementById("fb-comment").value = "";
  renderStarRatingPicker(5);

  modal.classList.remove("hidden");
}

function closeFeedbackModal() {
  const modal = document.getElementById("feedback-modal");
  if (modal) modal.classList.add("hidden");
  feedbackAptId = null;
}

function renderStarRatingPicker(rating) {
  selectedFeedbackRating = rating;
  const container = document.getElementById("fb-stars-container");
  if (!container) return;

  container.innerHTML = [1, 2, 3, 4, 5].map(star => `
    <button type="button" onclick="renderStarRatingPicker(${star})" 
      class="text-2xl transition transform hover:scale-110 ${star <= rating ? 'text-amber-500' : 'text-slate-300'}">
      ★
    </button>
  `).join("");
}

async function handleFeedbackSubmit(e) {
  e.preventDefault();
  if (!feedbackAptId) return;

  const comment = document.getElementById("fb-comment").value.trim();

  try {
    await apiCall("/api/feedback", {
      method: "POST",
      body: JSON.stringify({
        appointment_id: feedbackAptId,
        rating: selectedFeedbackRating,
        comment: comment
      })
    });

    closeFeedbackModal();
    showToast("Thank you for your valuable feedback!", "success");
    loadPatientAppointments();
  } catch (err) {
    showToast(err.message, "error");
  }
}

// ==================== 7. PATIENT MEDICAL RECORDS TIMELINE ====================

async function loadPatientMedicalRecords() {
  const container = document.getElementById("patient-records-timeline");
  if (!container) return;

  container.innerHTML = `<div class="p-8 text-center text-slate-400 text-xs">Loading clinical records...</div>`;

  try {
    const res = await apiCall("/api/medical-records");
    const records = res.records || [];

    if (records.length === 0) {
      container.innerHTML = `
        <div class="bg-white rounded-xl border border-slate-200 p-10 text-center">
          <p class="text-sm font-medium text-slate-600">Your medical history will appear here after your consultations.</p>
          <p class="text-xs text-slate-400 mt-1">Whenever a doctor records diagnosis or prescriptions, they will be archived here securely.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = records.map(rec => `
      <div class="timeline-stem mb-8">
        <div class="timeline-dot"></div>
        <div class="bg-white rounded-xl border border-slate-200/90 p-5 shadow-sm hover:border-slate-300 transition">
          <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-2 pb-3 border-b border-slate-100">
            <div>
              <div class="flex items-center gap-2">
                <h3 class="font-bold text-slate-900 text-base">${rec.diagnosis_title}</h3>
                <span class="px-2 py-0.5 rounded text-[10px] font-semibold bg-teal-100 text-teal-800">${rec.record_type}</span>
              </div>
              <p class="text-xs text-slate-500 mt-0.5">Consulted with <strong>${rec.doctor_name}</strong> (${rec.speciality} • ${rec.hospital_clinic})</p>
            </div>
            <div class="text-right shrink-0">
              <span class="text-xs font-semibold text-slate-700">📅 ${rec.record_date_formatted}</span>
            </div>
          </div>

          <!-- Clinical Notes & Vitals -->
          <div class="mt-4">
            <h4 class="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">Clinical Observations & Vitals</h4>
            <p class="text-xs text-slate-700 mt-1 whitespace-pre-line leading-relaxed bg-slate-50 p-3 rounded-lg border border-slate-100">${rec.clinical_notes || 'No notes provided.'}</p>
          </div>

          <!-- Prescriptions -->
          ${rec.prescription_notes ? `
            <div class="mt-3">
              <h4 class="text-[11px] font-semibold text-teal-800 uppercase tracking-wider flex items-center gap-1">
                <span>💊</span> Rx / Medications & Instructions
              </h4>
              <p class="text-xs text-slate-800 mt-1 whitespace-pre-line leading-relaxed bg-teal-50/50 p-3 rounded-lg border border-teal-100 font-mono">${rec.prescription_notes}</p>
            </div>
          ` : ''}

          <!-- Follow up date -->
          ${rec.follow_up_date_formatted ? `
            <div class="mt-3 flex items-center gap-2 text-xs font-medium text-amber-800 bg-amber-50 px-3 py-1.5 rounded-lg border border-amber-200 w-fit">
              <span>🔔</span> Next Follow-up Recommended: <strong>${rec.follow_up_date_formatted}</strong>
            </div>
          ` : ''}

          <div class="mt-4 pt-3 border-t border-slate-100 flex justify-end">
            <button onclick="printMedicalRecord(${rec.id})" class="px-3 py-1.5 text-xs font-semibold text-slate-700 bg-slate-100 hover:bg-slate-200 rounded-lg transition flex items-center gap-1.5">
              <span>🖨️</span> Print Prescription
            </button>
          </div>
        </div>
      </div>
    `).join("");

  } catch (err) {
    container.innerHTML = `<div class="p-8 text-center text-rose-600 text-xs">Failed to load medical records: ${err.message}</div>`;
  }
}

async function printMedicalRecord(recordId) {
  try {
    const res = await apiCall(`/api/medical-records/${recordId}`);
    const rec = res.record;
    
    let printArea = document.getElementById("printable-record-area");
    if (!printArea) {
      printArea = document.createElement("div");
      printArea.id = "printable-record-area";
      document.body.appendChild(printArea);
    }

    printArea.innerHTML = `
      <div style="font-family: Arial, sans-serif; max-width: 800px; margin: 0 auto; padding: 30px; border: 2px solid #0F766E;">
        <div style="display: flex; justify-content: space-between; border-bottom: 2px solid #0F766E; padding-bottom: 15px;">
          <div>
            <h1 style="color: #0F766E; margin: 0; font-size: 24px;">SehatSetu Healthcare</h1>
            <p style="margin: 4px 0 0; font-size: 12px; color: #666;">Better Care. Simpler Healthcare. • India</p>
          </div>
          <div style="text-align: right;">
            <h3 style="margin: 0; font-size: 16px;">${rec.doctor_name}</h3>
            <p style="margin: 2px 0; font-size: 12px; color: #444;">${rec.speciality}</p>
            <p style="margin: 2px 0; font-size: 11px; color: #666;">${rec.hospital_clinic}</p>
          </div>
        </div>

        <div style="display: flex; justify-content: space-between; margin-top: 15px; font-size: 12px; background: #f8f8f8; padding: 10px; border-radius: 6px;">
          <div>
            <strong>Patient Name:</strong> ${rec.patient_name}<br>
            <strong>Gender/Blood Group:</strong> ${rec.patient_gender || '-'} / ${rec.patient_blood_group || '-'}
          </div>
          <div style="text-align: right;">
            <strong>Consultation Date:</strong> ${formatDateIST(rec.record_date)}<br>
            <strong>Record Type:</strong> ${rec.record_type}
          </div>
        </div>

        <div style="margin-top: 20px;">
          <h3 style="color: #0F766E; border-bottom: 1px solid #ddd; padding-bottom: 5px; font-size: 14px;">Diagnosis: ${rec.diagnosis_title}</h3>
          <p style="font-size: 12px; line-height: 1.6; white-space: pre-line;">${rec.clinical_notes}</p>
        </div>

        ${rec.prescription_notes ? `
          <div style="margin-top: 20px;">
            <h3 style="color: #0F766E; border-bottom: 1px solid #ddd; padding-bottom: 5px; font-size: 14px;">Rx (Prescribed Medicines & Regimen)</h3>
            <div style="font-family: monospace; font-size: 12px; background: #fafafa; padding: 12px; border: 1px dashed #ccc; line-height: 1.6; white-space: pre-line;">${rec.prescription_notes}</div>
          </div>
        ` : ''}

        ${rec.follow_up_date ? `
          <p style="margin-top: 20px; font-size: 12px;"><strong>Follow-up Date:</strong> ${formatDateIST(rec.follow_up_date)}</p>
        ` : ''}

        <div style="margin-top: 50px; display: flex; justify-content: space-between; font-size: 11px; color: #888; border-top: 1px solid #eee; padding-top: 10px;">
          <span>Generated by SehatSetu Digital Health Record System</span>
          <span>Doctor Digital Signature / Verified</span>
        </div>
      </div>
    `;

    window.print();
  } catch (err) {
    showToast("Could not prepare print document: " + err.message, "error");
  }
}

// ==================== 8. PATIENT PROFILE ====================

async function loadPatientProfileForm() {
  try {
    const res = await apiCall("/api/auth/me");
    const u = res.user;
    const p = u.patient_profile || {};

    const f = document.getElementById("patient-profile-form");
    if (!f) return;

    f.full_name.value = u.full_name || "";
    f.email.value = u.email || "";
    f.mobile.value = u.mobile || "";
    f.city.value = u.city || "";
    f.dob.value = u.dob || "";
    f.gender.value = u.gender || "Male";
    f.blood_group.value = u.blood_group || "";
    f.address.value = u.address || "";

    f.allergies.value = p.allergies || "";
    f.chronic_conditions.value = p.chronic_conditions || "";
    if (f.current_medications) f.current_medications.value = p.current_medications || "";
    f.emergency_name.value = p.emergency_name || "";
    f.emergency_phone.value = p.emergency_phone || u.emergency_contact || "";
    if (f.emergency_relation) f.emergency_relation.value = p.emergency_relation || "";

    if (f.bp_systolic) f.bp_systolic.value = p.bp_systolic || "";
    if (f.bp_diastolic) f.bp_diastolic.value = p.bp_diastolic || "";
    if (f.pulse_rate) f.pulse_rate.value = p.pulse_rate || "";
    if (f.temperature_f) f.temperature_f.value = p.temperature_f || "";
    if (f.spo2_percent) f.spo2_percent.value = p.spo2_percent || "";
    if (f.height_cm) f.height_cm.value = p.height_cm || "";
    if (f.weight_kg) f.weight_kg.value = p.weight_kg || "";

  } catch (err) {
    console.error("Error loading patient profile:", err);
  }
}

async function handlePatientProfileSubmit(e) {
  e.preventDefault();
  const f = e.target;
  const payload = {
    full_name: f.full_name.value.trim(),
    mobile: f.mobile.value.trim(),
    city: f.city.value.trim(),
    dob: f.dob.value || null,
    gender: f.gender.value,
    blood_group: f.blood_group.value || null,
    address: f.address.value.trim(),
    allergies: f.allergies.value.trim(),
    chronic_conditions: f.chronic_conditions.value.trim(),
    current_medications: f.current_medications ? f.current_medications.value.trim() : "",
    emergency_name: f.emergency_name.value.trim(),
    emergency_phone: f.emergency_phone.value.trim(),
    emergency_relation: f.emergency_relation ? f.emergency_relation.value.trim() : "Family",
    bp_systolic: f.bp_systolic && f.bp_systolic.value ? parseInt(f.bp_systolic.value) : null,
    bp_diastolic: f.bp_diastolic && f.bp_diastolic.value ? parseInt(f.bp_diastolic.value) : null,
    pulse_rate: f.pulse_rate && f.pulse_rate.value ? parseInt(f.pulse_rate.value) : null,
    temperature_f: f.temperature_f && f.temperature_f.value ? parseFloat(f.temperature_f.value) : null,
    spo2_percent: f.spo2_percent && f.spo2_percent.value ? parseInt(f.spo2_percent.value) : null,
    height_cm: f.height_cm && f.height_cm.value ? parseFloat(f.height_cm.value) : null,
    weight_kg: f.weight_kg && f.weight_kg.value ? parseFloat(f.weight_kg.value) : null
  };

  try {
    await apiCall("/api/auth/profile", {
      method: "PUT",
      body: JSON.stringify(payload)
    });
    showToast("Profile and vitals successfully updated.", "success");
    // Refresh user state
    const meRes = await apiCall("/api/auth/me");
    AppState.currentUser = meRes.user;
    updateUserNavUI();
    if (AppState.activeRoute === "patient-dashboard") {
      loadPatientDashboard();
    }
  } catch (err) {
    showToast(err.message, "error");
  }
}
