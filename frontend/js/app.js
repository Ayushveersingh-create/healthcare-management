/**
 * SehatSetu - Core Single-Page Application (SPA) Controller
 * Handles authentication, routing, notifications, modals, and API calls.
 */

// Auto-detect backend URL: If opened directly via file:// or via Live Server (port 5500, etc.),
// route API requests to Spring Boot backend running on port 8000.
const API_BASE = (() => {
  if (typeof window !== "undefined" && (window.location.protocol === "file:" || (window.location.port && window.location.port !== "8000"))) {
    return "http://127.0.0.1:8000";
  }
  return "";
})();

// State
const AppState = {
  currentUser: null,
  token: localStorage.getItem("sehatsetu_token") || null,
  activeRoute: "home",
  notifications: [],
  unreadCount: 0,
  specialities: [],
  clinics: []
};

// Toast Notifications Helper
function showToast(message, type = "success") {
  const container = document.getElementById("toast-container");
  if (!container) return;

  const toast = document.createElement("div");
  const isSuccess = type === "success";
  const isError = type === "error";
  const isWarning = type === "warning";

  const bg = isSuccess ? "bg-teal-800 text-white" : (isError ? "bg-rose-700 text-white" : "bg-amber-700 text-white");
  const icon = isSuccess ? "✓" : (isError ? "✕" : "⚠");

  toast.className = `flex items-center gap-3 px-4 py-3 rounded-lg shadow-lg text-sm font-medium transition-all transform translate-y-2 opacity-0 duration-200 ${bg}`;
  toast.innerHTML = `
    <span class="font-bold text-base">${icon}</span>
    <span>${message}</span>
  `;

  container.appendChild(toast);
  requestAnimationFrame(() => {
    toast.classList.remove("translate-y-2", "opacity-0");
  });

  setTimeout(() => {
    toast.classList.add("opacity-0", "translate-y-2");
    setTimeout(() => toast.remove(), 250);
  }, 4000);
}

// Universal API Client
async function apiCall(endpoint, options = {}) {
  const headers = {
    "Content-Type": "application/json",
    ...(options.headers || {})
  };

  if (AppState.token) {
    headers["Authorization"] = `Bearer ${AppState.token}`;
  }

  try {
    const res = await fetch(`${API_BASE}${endpoint}`, {
      ...options,
      headers
    });

    const data = await res.json().catch(() => ({ detail: res.statusText }));

    if (!res.ok) {
      if (res.status === 401 && AppState.token) {
        // Token expired or invalid
        logout(false);
        showToast("Session expired. Please log in again.", "warning");
        openAuthModal("login");
      }
      throw new Error(data.detail || data.message || "Request failed");
    }

    return data;
  } catch (err) {
    console.error(`API Error on ${endpoint}:`, err);
    throw err;
  }
}

// Indian Date Formatter (DD/MM/YYYY)
function formatDateIST(dateStr) {
  if (!dateStr) return "-";
  try {
    const parts = dateStr.split("-");
    if (parts.length === 3) {
      return `${parts[2]}/${parts[1]}/${parts[0]}`;
    }
    const d = new Date(dateStr);
    const day = String(d.getDate()).padStart(2, "0");
    const month = String(d.getMonth() + 1).padStart(2, "0");
    const year = d.getFullYear();
    return `${day}/${month}/${year}`;
  } catch (e) {
    return dateStr;
  }
}

// Indian Currency Formatter (₹)
function formatCurrency(amount) {
  if (amount == null) return "₹0";
  return `₹${Number(amount).toLocaleString('en-IN')}`;
}

// Initialize App
async function initApp() {
  updateCurrentDateDisplay();
  
  // Load initial public metadata
  try {
    const [specRes, clinRes] = await Promise.all([
      apiCall("/api/specialities"),
      apiCall("/api/clinics")
    ]);
    AppState.specialities = specRes.specialities || [];
    AppState.clinics = clinRes.clinics || [];
    populateSpecialitiesDropdown();
  } catch (e) {
    console.warn("Could not load initial metadata:", e);
  }

  // Check login state
  if (AppState.token) {
    try {
      const meRes = await apiCall("/api/auth/me");
      AppState.currentUser = meRes.user;
      updateUserNavUI();
      fetchNotifications();
    } catch (e) {
      console.warn("Token invalid, continuing as guest");
      logout(false);
    }
  } else {
    updateUserNavUI();
  }

  // Handle URL hash routing
  window.addEventListener("hashchange", handleRouting);
  handleRouting();

  // Polling notifications every 60 seconds if logged in
  setInterval(() => {
    if (AppState.currentUser) {
      fetchNotifications();
    }
  }, 60000);
}

function updateCurrentDateDisplay() {
  const dateSpan = document.getElementById("header-current-date");
  if (dateSpan) {
    const now = new Date();
    const options = { weekday: 'long', day: 'numeric', month: 'short', year: 'numeric' };
    dateSpan.textContent = now.toLocaleDateString('en-IN', options);
  }
}

function toggleProfileSwitcherDropdown(forceState = null) {
  const dropdown = document.getElementById("profile-switcher-dropdown");
  if (!dropdown) return;
  if (forceState !== null) {
    dropdown.classList.toggle("hidden", !forceState);
  } else {
    dropdown.classList.toggle("hidden");
  }
}

// Close profile switcher dropdown when clicking outside
document.addEventListener("click", function(e) {
  const dropdown = document.getElementById("profile-switcher-dropdown");
  const btnUser = document.getElementById("nav-user-pill-btn");
  if (!dropdown || dropdown.classList.contains("hidden")) return;
  if (!dropdown.contains(e.target) && (!btnUser || !btnUser.contains(e.target)) && !e.target.closest("#nav-guest-actions")) {
    dropdown.classList.add("hidden");
  }
});

async function switchSeamlessProfile(email, role) {
  toggleProfileSwitcherDropdown(false);

  if (role === "GUEST" || email === "guest") {
    logout(true);
    navigate("home");
    showToast("Switched to Public Guest Visitor mode.");
    return;
  }

  const roleUpper = role.toUpperCase();
  let targetPassword = null;
  if (roleUpper === "DOCTOR") targetPassword = "Doctor@123";
  else if (roleUpper === "ADMIN") targetPassword = "Admin@123";
  else if (roleUpper === "PATIENT") targetPassword = "Patient@123";

  try {
    const payload = { role: roleUpper, email: email };
    if (targetPassword) payload.password = targetPassword;

    const res = await apiCall("/api/auth/demo-login", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    AppState.token = res.access_token;
    localStorage.setItem("sehatsetu_token", res.access_token);
    AppState.currentUser = res.user;

    closeAuthModal();
    closeDemoPasswordModal();
    updateUserNavUI();
    fetchNotifications();

    showToast(`Switched profile to ${res.user.full_name} (${roleUpper.toLowerCase()})!`, "success");

    // Navigate to role's dashboard unless currently viewing a doctor profile
    if (roleUpper === "PATIENT") {
      if (AppState.activeRoute && AppState.activeRoute.startsWith("doctor-profile")) {
        handleRouting();
      } else {
        navigate("patient-dashboard");
      }
    } else if (roleUpper === "DOCTOR") {
      navigate("doctor-dashboard");
    } else if (roleUpper === "ADMIN") {
      navigate("admin-dashboard");
    }
  } catch (err) {
    showToast(`Profile switch failed: ${err.message}`, "error");
  }
}

// Navigation & Auth UI update
function updateUserNavUI() {
  const user = AppState.currentUser;
  const guestNav = document.getElementById("nav-guest-actions");
  const authNav = document.getElementById("nav-user-actions");
  const userNameSpan = document.getElementById("nav-user-name");
  const userRoleBadge = document.getElementById("nav-user-role-badge");
  const userAvatar = document.getElementById("nav-user-avatar");
  const dashboardLink = document.getElementById("nav-role-dashboard-btn");

  // Demo Switcher buttons active state
  document.querySelectorAll(".demo-switch-btn").forEach(btn => {
    btn.classList.remove("ring-2", "ring-teal-500", "font-bold");
    if (!user && btn.dataset.role === "guest") {
      btn.classList.add("ring-2", "ring-teal-500", "font-bold");
    } else if (user && btn.dataset.role === user.role.toLowerCase()) {
      btn.classList.add("ring-2", "ring-teal-500", "font-bold");
    }
  });

  if (user) {
    if (guestNav) guestNav.classList.add("hidden");
    if (authNav) authNav.classList.remove("hidden");
    if (userNameSpan) userNameSpan.textContent = user.full_name.split(" ")[0];
    if (userAvatar) userAvatar.textContent = user.full_name.charAt(0).toUpperCase();

    if (userRoleBadge) {
      userRoleBadge.textContent = user.role;
      userRoleBadge.className = "text-[10px] font-semibold " +
        (user.role === "ADMIN" ? "text-purple-700" :
        (user.role === "DOCTOR" ? "text-teal-700" : "text-blue-700"));
    }

    if (dashboardLink) {
      dashboardLink.classList.remove("hidden");
      if (user.role === "PATIENT") {
        dashboardLink.href = "#patient-dashboard";
        dashboardLink.textContent = "My Health Portal";
      } else if (user.role === "DOCTOR") {
        dashboardLink.href = "#doctor-dashboard";
        dashboardLink.textContent = "Doctor Workspace";
      } else if (user.role === "ADMIN") {
        dashboardLink.href = "#admin-dashboard";
        dashboardLink.textContent = "Admin Console";
      }
    }

    // Role Subnav toggles
    const subnavPatient = document.getElementById("subnav-patient");
    const subnavDoctor = document.getElementById("subnav-doctor");
    const subnavAdmin = document.getElementById("subnav-admin");
    if (subnavPatient) subnavPatient.classList.toggle("hidden", user.role !== "PATIENT");
    if (subnavDoctor) subnavDoctor.classList.toggle("hidden", user.role !== "DOCTOR");
    if (subnavAdmin) subnavAdmin.classList.toggle("hidden", user.role !== "ADMIN");
  } else {
    if (guestNav) guestNav.classList.remove("hidden");
    if (authNav) authNav.classList.add("hidden");
    if (dashboardLink) dashboardLink.classList.add("hidden");
    document.getElementById("subnav-patient")?.classList.add("hidden");
    document.getElementById("subnav-doctor")?.classList.add("hidden");
    document.getElementById("subnav-admin")?.classList.add("hidden");
  }

  // Update Profile Switcher Header Card & Active Checkmarks
  const psAvatar = document.getElementById("ps-active-avatar");
  const psName = document.getElementById("ps-active-name");
  const psEmail = document.getElementById("ps-active-email");
  const psRoleBadge = document.getElementById("ps-active-role-badge");
  const psMeta = document.getElementById("ps-active-meta");
  const psStatusTag = document.getElementById("ps-active-status-tag");

  if (user) {
    const initials = user.full_name.split(" ").map(n => n[0]).join("").toUpperCase();
    if (psAvatar) psAvatar.textContent = initials;
    if (psName) psName.textContent = user.full_name;
    if (psEmail) psEmail.textContent = user.email;
    if (psRoleBadge) {
      psRoleBadge.textContent = user.role;
      psRoleBadge.className = "px-2 py-0.5 rounded text-[10px] font-bold " +
        (user.role === "ADMIN" ? "bg-purple-900/90 text-purple-200 border border-purple-700" :
        (user.role === "DOCTOR" ? "bg-teal-900/90 text-teal-200 border border-teal-700" : "bg-blue-900/90 text-blue-200 border border-blue-700"));
    }
    if (psMeta) psMeta.textContent = user.city || user.role;
    if (psStatusTag) {
      psStatusTag.textContent = "● Signed In";
      psStatusTag.className = "bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 px-2 py-0.5 rounded-full text-[9px] font-semibold";
    }
  } else {
    if (psAvatar) psAvatar.textContent = "👤";
    if (psName) psName.textContent = "Public Guest Visitor";
    if (psEmail) psEmail.textContent = "guest@sehatsetu.in";
    if (psRoleBadge) {
      psRoleBadge.textContent = "GUEST";
      psRoleBadge.className = "px-2 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-slate-300 border border-slate-700";
    }
    if (psMeta) psMeta.textContent = "Unauthenticated Mode";
    if (psStatusTag) {
      psStatusTag.textContent = "○ Visitor";
      psStatusTag.className = "bg-slate-700/50 text-slate-300 border border-slate-600 px-2 py-0.5 rounded-full text-[9px] font-semibold";
    }
  }

  // Highlight active item checkmarks in profile switcher list
  document.querySelectorAll(".ps-profile-item").forEach(item => {
    const itemEmail = item.dataset.email;
    const check = item.querySelector(".ps-active-check");
    const isActive = (user && itemEmail.toLowerCase() === user.email.toLowerCase()) || (!user && itemEmail === "guest");
    
    if (check) check.classList.toggle("hidden", !isActive);
    if (isActive) {
      item.classList.add("bg-teal-50/80", "border-l-2", "border-teal-700");
    } else {
      item.classList.remove("bg-teal-50/80", "border-l-2", "border-teal-700");
    }
  });
}

// Router
function navigate(route) {
  window.location.hash = route;
}

function handleRouting() {
  const hash = window.location.hash.slice(1) || "home";
  const [route, queryString] = hash.split("?");
  AppState.activeRoute = route;

  // Check RBAC protection
  const user = AppState.currentUser;

  const isPatientPrivateRoute = [
    "patient-dashboard",
    "patient-appointments",
    "patient-records",
    "patient-profile"
  ].includes(route);

  const isDoctorPrivateRoute = [
    "doctor-dashboard",
    "doctor-schedule",
    "doctor-appointments",
    "doctor-patients",
    "doctor-feedback"
  ].includes(route);

  const isAdminPrivateRoute = route.startsWith("admin-");

  if (isPatientPrivateRoute && (!user || user.role !== "PATIENT")) {
    showToast("Please log in as a Patient to access this page.", "warning");
    openAuthModal("login");
    navigate("home");
    return;
  }

  if (isDoctorPrivateRoute && (!user || user.role !== "DOCTOR")) {
    showToast("Please log in as a Doctor to access this workspace.", "warning");
    openAuthModal("login");
    navigate("home");
    return;
  }

  if (isAdminPrivateRoute && (!user || user.role !== "ADMIN")) {
    showToast("Admin access required.", "warning");
    openAuthModal("login");
    navigate("home");
    return;
  }

  // Hide all views
  document.querySelectorAll(".spa-view").forEach(el => el.classList.add("hidden"));

  // Show target view
  const targetView = document.getElementById(`view-${route}`);
  if (targetView) {
    targetView.classList.remove("hidden");
    window.scrollTo({ top: 0, behavior: "smooth" });

    // Route-specific data loaders
    if (route === "home") {
      loadLandingStats();
    } else if (route === "doctors") {
      loadDoctorsList();
    } else if (route === "doctor-profile") {
      const params = new URLSearchParams(queryString);
      loadDoctorProfileView(params.get("id"));
    } else if (route === "patient-dashboard") {
      loadPatientDashboard();
    } else if (route === "patient-appointments") {
      loadPatientAppointments();
    } else if (route === "patient-records") {
      loadPatientMedicalRecords();
    } else if (route === "patient-profile") {
      loadPatientProfileForm();
    } else if (route === "doctor-dashboard") {
      loadDoctorDashboard();
    } else if (route === "doctor-schedule") {
      loadDoctorScheduleManager();
    } else if (route === "doctor-appointments") {
      loadDoctorAppointments();
    } else if (route === "doctor-patients") {
      loadDoctorPatients();
    } else if (route === "doctor-feedback") {
      loadDoctorFeedback();
    } else if (route === "admin-dashboard") {
      loadAdminDashboard();
    } else if (route === "admin-users") {
      loadAdminUsers();
    } else if (route === "admin-doctors") {
      loadAdminDoctors();
    } else if (route === "admin-appointments") {
      loadAdminAppointments();
    } else if (route === "admin-settings") {
      loadAdminSettings();
    } else if (route === "admin-logs") {
      loadAdminLogs();
    }
  } else {
    // Fallback to home
    const homeView = document.getElementById("view-home");
    if (homeView) homeView.classList.remove("hidden");
  }
}

// Password-Protected Demo Role Switcher
let targetDemoRole = null;

async function switchDemoRole(role, password = null) {
  const roleUpper = role.toUpperCase();
  if (role === "guest") {
    logout(true);
    navigate("home");
    showToast("Switched to Public Guest Visitor mode.");
    return;
  }

  // Enforce password verification modal for DOCTOR and ADMIN
  if ((roleUpper === "DOCTOR" || roleUpper === "ADMIN") && !password) {
    openDemoPasswordModal(roleUpper);
    return;
  }

  try {
    const payload = { role: roleUpper };
    if (password) payload.password = password;

    const res = await apiCall("/api/auth/demo-login", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    AppState.token = res.access_token;
    localStorage.setItem("sehatsetu_token", res.access_token);
    AppState.currentUser = res.user;

    closeAuthModal();
    closeDemoPasswordModal();
    updateUserNavUI();
    fetchNotifications();

    showToast(`Authenticated as ${roleUpper.toLowerCase()} (${res.user.full_name})!`, "success");

    // Navigate to role's dashboard unless already on a doctor profile
    if (roleUpper === "PATIENT") {
      if (AppState.activeRoute && AppState.activeRoute.startsWith("doctor-profile")) {
        handleRouting();
      } else {
        navigate("patient-dashboard");
      }
    } else if (roleUpper === "DOCTOR") navigate("doctor-dashboard");
    else if (roleUpper === "ADMIN") navigate("admin-dashboard");
  } catch (err) {
    showToast(err.message, "error");
  }
}

function openDemoPasswordModal(role) {
  targetDemoRole = role.toUpperCase();
  const modal = document.getElementById("demo-password-modal");
  if (!modal) return;

  const isDoc = targetDemoRole === "DOCTOR";
  const titleEl = document.getElementById("dpm-title");
  const emailEl = document.getElementById("dpm-email");
  const passInput = document.getElementById("dpm-password");

  if (titleEl) titleEl.textContent = isDoc ? "Doctor Security Verification" : "Admin Console Security Verification";
  if (emailEl) emailEl.textContent = isDoc ? "ananya.sharma@sehatsetu.in" : "admin@sehatsetu.in";
  if (passInput) passInput.value = isDoc ? "Doctor@123" : "Admin@123";

  modal.classList.remove("hidden");
}

function closeDemoPasswordModal() {
  const modal = document.getElementById("demo-password-modal");
  if (modal) modal.classList.add("hidden");
  targetDemoRole = null;
}

function handleDemoPasswordSubmit(e) {
  e.preventDefault();
  const password = document.getElementById("dpm-password").value;
  if (!password) {
    showToast("Please enter the password.", "warning");
    return;
  }
  switchDemoRole(targetDemoRole, password);
}

// Standard Login
async function handleLoginFormSubmit(e) {
  e.preventDefault();
  const form = e.target;
  const email = form.email.value.trim();
  const password = form.password.value;
  const btn = form.querySelector("button[type='submit']");
  const originalText = btn.textContent;

  try {
    btn.disabled = true;
    btn.textContent = "Verifying...";
    const res = await apiCall("/api/auth/login", {
      method: "POST",
      body: JSON.stringify({ email, password })
    });

    AppState.token = res.access_token;
    localStorage.setItem("sehatsetu_token", res.access_token);
    AppState.currentUser = res.user;

    closeAuthModal();
    updateUserNavUI();
    fetchNotifications();
    showToast(`Welcome back, ${res.user.full_name}!`);

    if (res.user.role === "PATIENT") navigate("patient-dashboard");
    else if (res.user.role === "DOCTOR") navigate("doctor-dashboard");
    else if (res.user.role === "ADMIN") navigate("admin-dashboard");
  } catch (err) {
    showToast(err.message, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = originalText;
  }
}

// Standard Register
async function handleRegisterFormSubmit(e) {
  e.preventDefault();
  const form = e.target;
  const payload = {
    full_name: form.full_name.value.trim(),
    email: form.email.value.trim(),
    mobile: form.mobile.value.trim(),
    password: form.password.value,
    city: form.city.value.trim(),
    dob: form.dob.value || null,
    gender: form.gender.value,
    blood_group: form.blood_group.value || null
  };

  const btn = form.querySelector("button[type='submit']");
  const originalText = btn.textContent;

  try {
    btn.disabled = true;
    btn.textContent = "Creating Account...";
    const res = await apiCall("/api/auth/register", {
      method: "POST",
      body: JSON.stringify(payload)
    });

    AppState.token = res.access_token;
    localStorage.setItem("sehatsetu_token", res.access_token);
    AppState.currentUser = res.user;

    closeAuthModal();
    updateUserNavUI();
    fetchNotifications();
    showToast("Account created successfully! Welcome to SehatSetu.");
    navigate("patient-dashboard");
  } catch (err) {
    showToast(err.message, "error");
  } finally {
    btn.disabled = false;
    btn.textContent = originalText;
  }
}

function logout(silent = false) {
  AppState.currentUser = null;
  AppState.token = null;
  localStorage.removeItem("sehatsetu_token");
  updateUserNavUI();
  if (!silent) {
    showToast("You have been securely logged out.");
    navigate("home");
  }
}

// Notification Drawer / Bell
async function fetchNotifications() {
  if (!AppState.token) return;
  try {
    const res = await apiCall("/api/notifications");
    AppState.notifications = res.notifications || [];
    AppState.unreadCount = res.unread_count || 0;

    const badge = document.getElementById("notif-unread-badge");
    if (badge) {
      if (AppState.unreadCount > 0) {
        badge.textContent = AppState.unreadCount > 9 ? "9+" : AppState.unreadCount;
        badge.classList.remove("hidden");
      } else {
        badge.classList.add("hidden");
      }
    }
  } catch (e) {
    console.warn("Error fetching notifications:", e);
  }
}

function toggleNotificationDropdown() {
  const dd = document.getElementById("notification-dropdown");
  if (!dd) return;
  if (dd.classList.contains("hidden")) {
    renderNotificationList();
    dd.classList.remove("hidden");
  } else {
    dd.classList.add("hidden");
  }
}

function renderNotificationList() {
  const container = document.getElementById("notif-list-container");
  if (!container) return;

  if (AppState.notifications.length === 0) {
    container.innerHTML = `
      <div class="py-6 text-center text-slate-400 text-sm">
        <p>No notifications yet</p>
      </div>
    `;
    return;
  }

  container.innerHTML = AppState.notifications.map(n => `
    <div class="p-3 border-b border-slate-100 hover:bg-slate-50 transition cursor-pointer ${n.is_read ? 'opacity-70' : 'bg-teal-50/40 font-medium'}" onclick="markNotifRead(${n.id}, '${n.link_url || ''}')">
      <div class="flex items-start justify-between gap-2">
        <div class="text-xs font-semibold ${n.is_read ? 'text-slate-700' : 'text-teal-900'}">${n.title}</div>
        <span class="text-[10px] text-slate-400 whitespace-nowrap">${n.created_at ? n.created_at.split(" ")[1]?.slice(0, 5) || '' : ''}</span>
      </div>
      <p class="text-xs text-slate-600 mt-1 line-clamp-2">${n.message}</p>
    </div>
  `).join("");
}

async function markNotifRead(id, linkUrl) {
  try {
    await apiCall(`/api/notifications/${id}/read`, { method: "PUT" });
    fetchNotifications();
    const dd = document.getElementById("notification-dropdown");
    if (dd) dd.classList.add("hidden");
    if (linkUrl) {
      if (linkUrl.startsWith("#")) navigate(linkUrl.slice(1));
      else if (linkUrl.startsWith("/")) navigate(linkUrl.replace("/", "").replace("/", "-"));
    }
  } catch (e) {
    console.warn("Could not mark read:", e);
  }
}

async function markAllNotifsRead() {
  try {
    await apiCall("/api/notifications/read-all", { method: "PUT" });
    fetchNotifications();
    renderNotificationList();
    showToast("All notifications marked as read.");
  } catch (e) {
    showToast(e.message, "error");
  }
}

// Modal Helpers
function openAuthModal(tab = "login") {
  const modal = document.getElementById("auth-modal");
  if (!modal) return;
  switchAuthTab(tab);
  modal.classList.remove("hidden");
}

function closeAuthModal() {
  const modal = document.getElementById("auth-modal");
  if (modal) modal.classList.add("hidden");
}

function switchAuthTab(tab) {
  const loginForm = document.getElementById("auth-form-login");
  const regForm = document.getElementById("auth-form-register");
  const loginTabBtn = document.getElementById("tab-btn-login");
  const regTabBtn = document.getElementById("tab-btn-register");

  if (tab === "login") {
    loginForm.classList.remove("hidden");
    regForm.classList.add("hidden");
    loginTabBtn.className = "flex-1 pb-2 border-b-2 border-teal-700 text-teal-800 font-semibold text-sm";
    regTabBtn.className = "flex-1 pb-2 border-b-2 border-transparent text-slate-500 font-medium text-sm hover:text-slate-700";
  } else {
    loginForm.classList.add("hidden");
    regForm.classList.remove("hidden");
    regTabBtn.className = "flex-1 pb-2 border-b-2 border-teal-700 text-teal-800 font-semibold text-sm";
    loginTabBtn.className = "flex-1 pb-2 border-b-2 border-transparent text-slate-500 font-medium text-sm hover:text-slate-700";
  }
}

function populateSpecialitiesDropdown() {
  const selects = document.querySelectorAll(".speciality-select-populate");
  selects.forEach(select => {
    const currentVal = select.value;
    select.innerHTML = `<option value="all">All Specialities</option>` +
      AppState.specialities.map(s => `<option value="${s.name}">${s.name}</option>`).join("");
    if (currentVal) select.value = currentVal;
  });
}

// Auto-run on DOM ready
document.addEventListener("DOMContentLoaded", initApp);
