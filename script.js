const SITE_PASSWORD = "1208";
const ADMIN_PASSWORD = "2702";
const CLASS_LEVELS = ["5", "6", "7", "8", "9", "10", "11", "12"];
const EMPTY_STATE = {
  teachers: [],
  pendingUploads: [],
  approvedUploads: []
};

let state = structuredClone(EMPTY_STATE);
let selectedTeacherId = null;
let selectedSubject = null;
let selectedClass = null;
let selectedEntryId = null;
let teacherSearchTerm = "";
let adminTeacherFilter = "";
let adminSubjectFilter = "";
let adminClassFilter = "";
let isLoadingState = false;
let loadError = "";

const gateScreen = document.getElementById("gateScreen");
const appScreen = document.getElementById("appScreen");
const gateForm = document.getElementById("gateForm");
const gateMessage = document.getElementById("gateMessage");
const teacherSearch = document.getElementById("teacherSearch");
const teacherSelect = document.getElementById("teacherSelect");
const subjectWrap = document.getElementById("subjectWrap");
const classWrap = document.getElementById("classWrap");
const subjectSelect = document.getElementById("subjectSelect");
const classSelect = document.getElementById("classSelect");
const archivePanel = document.querySelector(".archive-panel");
const archiveHint = document.getElementById("archiveHint");
const previewHint = document.getElementById("previewHint");
const entryList = document.getElementById("entryList");
const previewPanel = document.getElementById("previewPanel");
const previewType = document.getElementById("previewType");
const previewTitle = document.getElementById("previewTitle");
const previewMeta = document.getElementById("previewMeta");
const previewNote = document.getElementById("previewNote");
const previewImageWrap = document.getElementById("previewImageWrap");
const previewImage = document.getElementById("previewImage");
const previewFrameWrap = document.getElementById("previewFrameWrap");
const previewFrame = document.getElementById("previewFrame");
const previewFallback = document.getElementById("previewFallback");
const adminAccessBtn = document.getElementById("adminAccessBtn");
const uploadModal = document.getElementById("uploadModal");
const adminModal = document.getElementById("adminModal");
const openUploadBtn = document.getElementById("openUploadBtn");
const uploadForm = document.getElementById("uploadForm");
const uploadTeacher = document.getElementById("uploadTeacher");
const uploadSubject = document.getElementById("uploadSubject");
const uploadClass = document.getElementById("uploadClass");
const uploadMessage = document.getElementById("uploadMessage");
const teacherForm = document.getElementById("teacherForm");
const adminTeacherList = document.getElementById("adminTeacherList");
const adminTeacherFilterSelect = document.getElementById("adminTeacherFilter");
const adminSubjectFilterSelect = document.getElementById("adminSubjectFilter");
const adminClassFilterSelect = document.getElementById("adminClassFilter");
const pendingList = document.getElementById("pendingList");
const approvedList = document.getElementById("approvedList");
const archiveItemTemplate = document.getElementById("archiveItemTemplate");

gateForm.addEventListener("submit", handleSiteAccess);
adminAccessBtn.addEventListener("click", requestAdminAccess);
openUploadBtn.addEventListener("click", () => {
  populateUploadTeachers();
  uploadMessage.textContent = "";
  openModal(uploadModal);
});
teacherSearch.addEventListener("input", handleTeacherSearch);
teacherSelect.addEventListener("change", handleTeacherSelection);
subjectSelect.addEventListener("change", handleSubjectSelection);
classSelect.addEventListener("change", handleClassSelection);
uploadTeacher.addEventListener("change", populateUploadSubjects);
uploadForm.addEventListener("submit", handleUpload);
teacherForm.addEventListener("submit", handleTeacherSave);
adminTeacherFilterSelect.addEventListener("change", handleAdminTeacherFilterChange);
adminSubjectFilterSelect.addEventListener("change", handleAdminSubjectFilterChange);
adminClassFilterSelect.addEventListener("change", handleAdminClassFilterChange);

document.querySelectorAll("[data-close]").forEach((button) => {
  button.addEventListener("click", () => {
    closeModal(document.getElementById(button.dataset.close));
  });
});

window.addEventListener("click", (event) => {
  if (event.target.classList.contains("modal")) {
    closeModal(event.target);
  }
});

initializeApp();

async function initializeApp() {
  renderAll();
  await refreshState();
}

function handleSiteAccess(event) {
  event.preventDefault();
  const enteredPassword = document.getElementById("sitePassword").value.trim();

  if (enteredPassword === SITE_PASSWORD) {
    gateScreen.classList.add("hidden");
    appScreen.classList.remove("hidden");
    gateMessage.textContent = "";
    return;
  }

  gateMessage.textContent = "Das Passwort ist falsch. Bitte versuche es noch einmal.";
}

function requestAdminAccess() {
  const password = window.prompt("Bitte Admin-Passwort eingeben:");
  if (password === null) {
    return;
  }

  if (password === ADMIN_PASSWORD) {
    renderAdmin();
    openModal(adminModal);
    return;
  }

  window.alert("Admin-Passwort falsch.");
}

async function refreshState() {
  isLoadingState = true;
  loadError = "";
  renderAll();

  try {
    const nextState = await apiRequest("/api/state");
    state = normalizeState(nextState);
    sanitizeSelections();
  } catch (error) {
    loadError = error.message || "Die Daten konnten nicht geladen werden.";
  } finally {
    isLoadingState = false;
    renderAll();
  }
}

function normalizeState(nextState) {
  return {
    teachers: Array.isArray(nextState.teachers) ? nextState.teachers : [],
    pendingUploads: Array.isArray(nextState.pendingUploads) ? nextState.pendingUploads : [],
    approvedUploads: Array.isArray(nextState.approvedUploads) ? nextState.approvedUploads : []
  };
}

function sanitizeSelections() {
  if (!state.teachers.some((teacher) => teacher.id === selectedTeacherId)) {
    selectedTeacherId = null;
    selectedSubject = null;
    selectedClass = null;
    selectedEntryId = null;
  }

  const selectedTeacher = getSelectedTeacher();
  if (selectedTeacher && !selectedTeacher.subjects.includes(selectedSubject)) {
    selectedSubject = null;
    selectedClass = null;
    selectedEntryId = null;
  }

  if (selectedClass && !hasApprovedEntriesForClass(selectedClass)) {
    selectedClass = null;
    selectedEntryId = null;
  }

  if (selectedEntryId && !getVisibleEntries().some((item) => item.id === selectedEntryId)) {
    selectedEntryId = null;
  }

  if (!state.teachers.some((teacher) => teacher.id === adminTeacherFilter)) {
    adminTeacherFilter = "";
    adminSubjectFilter = "";
    adminClassFilter = "";
  }

  const adminTeacher = state.teachers.find((teacher) => teacher.id === adminTeacherFilter);
  if (adminTeacher && !adminTeacher.subjects.includes(adminSubjectFilter)) {
    adminSubjectFilter = "";
    adminClassFilter = "";
  }
}

function renderAll() {
  renderSelectionFlow();
  renderArchive();
  populateUploadTeachers();
  if (!adminModal.classList.contains("hidden")) {
    renderAdmin();
  }
}

function renderSelectionFlow() {
  teacherSelect.innerHTML = '<option value="">Lehrer auswählen</option>';
  const teachers = getFilteredTeachers();

  if (isLoadingState) {
    archiveHint.textContent = "Daten werden geladen...";
    return;
  }

  if (loadError) {
    archiveHint.textContent = loadError;
    return;
  }

  teachers.forEach((teacher) => {
    const option = document.createElement("option");
    option.value = teacher.id;
    option.textContent = `${teacher.name} (${teacher.code})`;
    teacherSelect.appendChild(option);
  });
  teacherSelect.value = selectedTeacherId ?? "";

  const teacher = getSelectedTeacher();
  subjectWrap.classList.toggle("hidden", !teacher);
  classWrap.classList.toggle("hidden", !selectedSubject);

  renderSubjectSelect(teacher);
  renderClassSelect();

  if (!teacher) {
    archiveHint.textContent = teachers.length ? "Bitte zuerst einen Lehrer auswählen." : "Keine Lehrer gefunden.";
  } else if (!selectedSubject) {
    archiveHint.textContent = `${teacher.name} ausgewählt. Bitte jetzt ein Fach auswählen.`;
  } else if (!selectedClass) {
    archiveHint.textContent = `${teacher.name} • ${selectedSubject}. Bitte jetzt eine Klasse auswählen.`;
  } else {
    archiveHint.textContent = `${teacher.name} • ${selectedSubject} • Klasse ${selectedClass}`;
  }
}

function renderSubjectSelect(teacher) {
  subjectSelect.innerHTML = '<option value="">Fach auswählen</option>';
  if (!teacher) {
    return;
  }

  teacher.subjects.forEach((subject) => {
    const option = document.createElement("option");
    option.value = subject;
    option.textContent = subject;
    subjectSelect.appendChild(option);
  });
  subjectSelect.value = selectedSubject ?? "";
}

function renderClassSelect() {
  classSelect.innerHTML = '<option value="">Klasse auswählen</option>';
  if (!selectedSubject) {
    return;
  }

  CLASS_LEVELS.forEach((classLevel) => {
    const option = document.createElement("option");
    const hasEntries = hasApprovedEntriesForClass(classLevel);
    option.value = classLevel;
    option.textContent = hasEntries ? classLevel : `${classLevel} - keine Inhalte`;
    option.disabled = !hasEntries;
    classSelect.appendChild(option);
  });
  classSelect.value = selectedClass ?? "";
}

function renderArchive() {
  entryList.innerHTML = "";
  previewPanel.classList.add("hidden");
  previewImageWrap.classList.add("hidden");
  previewFrameWrap.classList.add("hidden");
  previewFallback.classList.add("hidden");
  previewNote.classList.add("hidden");
  previewImage.removeAttribute("src");
  previewFrame.removeAttribute("src");

  if (!selectedTeacherId || !selectedSubject || !selectedClass) {
    archivePanel.classList.add("hidden-panel");
    previewHint.textContent = "Bitte zuerst Lehrer, Fach und Klasse auswählen.";
    entryList.textContent = "Hier erscheinen danach die Titel.";
    return;
  }

  archivePanel.classList.remove("hidden-panel");
  const items = getVisibleEntries();
  const teacher = getSelectedTeacher();

  if (!items.length) {
    previewHint.textContent = "Für diese Auswahl gibt es noch keine freigegebenen Arbeiten.";
    entryList.textContent = "Keine Titel vorhanden.";
    return;
  }

  items.forEach((item) => {
    const fragment = archiveItemTemplate.content.cloneNode(true);
    const button = fragment.querySelector(".entry-button");
    fragment.querySelector(".pill").textContent = item.type;
    fragment.querySelector("strong").textContent = item.title;
    fragment.querySelector(".meta-line").textContent = `${teacher.code} • Jahr ${item.year}`;
    fragment.querySelector(".summary-date").textContent = getShortDateLabel(item);
    if (item.id === selectedEntryId) {
      button.classList.add("active");
    }
    button.addEventListener("click", () => {
      selectedEntryId = item.id;
      renderArchive();
    });
    entryList.appendChild(fragment);
  });

  const selectedEntry = items.find((item) => item.id === selectedEntryId);
  if (!selectedEntry) {
    previewHint.textContent = "Bitte jetzt einen Titel auswählen.";
    return;
  }

  renderPreview(selectedEntry, teacher);
}

function renderPreview(item, teacher) {
  previewPanel.classList.remove("hidden");
  previewHint.textContent = item.title;
  previewType.textContent = item.type;
  previewTitle.textContent = item.title;
  previewMeta.textContent = `${teacher.name} • ${item.subject} • Klasse ${item.classLevel} • Jahr ${item.year}`;

  if (item.note) {
    previewNote.textContent = item.note;
    previewNote.classList.remove("hidden");
  }

  if (isImageFile(item.fileName)) {
    previewImage.src = item.previewUrl;
    previewImage.alt = item.title;
    previewImageWrap.classList.remove("hidden");
    return;
  }

  if (isFramePreviewableFile(item.fileName, item.previewUrl)) {
    previewFrame.src = item.previewUrl;
    previewFrameWrap.classList.remove("hidden");
    return;
  }

  previewFallback.classList.remove("hidden");
}

function populateUploadTeachers() {
  uploadTeacher.innerHTML = "";
  uploadClass.innerHTML = CLASS_LEVELS.map((level) => `<option value="${level}">${level}</option>`).join("");

  if (!state.teachers.length) {
    uploadSubject.innerHTML = "";
    return;
  }

  state.teachers
    .slice()
    .sort((a, b) => a.code.localeCompare(b.code, "de"))
    .forEach((teacher) => {
      const option = document.createElement("option");
      option.value = teacher.id;
      option.textContent = `${teacher.name} (${teacher.code})`;
      uploadTeacher.appendChild(option);
    });

  populateUploadSubjects();
}

function populateUploadSubjects() {
  uploadSubject.innerHTML = "";
  const teacher = state.teachers.find((entry) => entry.id === uploadTeacher.value) || state.teachers[0];

  if (!teacher) {
    return;
  }

  teacher.subjects.forEach((subject) => {
    const option = document.createElement("option");
    option.value = subject;
    option.textContent = subject;
    uploadSubject.appendChild(option);
  });
}

async function handleUpload(event) {
  event.preventDefault();
  const fileInput = document.getElementById("uploadFile");
  const file = fileInput.files[0];

  if (!file) {
    uploadMessage.textContent = "Bitte eine Datei auswählen.";
    return;
  }

  uploadMessage.textContent = "Upload wird gespeichert...";

  try {
    const fileDataUrl = await readFileAsDataUrl(file);
    const response = await postAction("submitUpload", {
      teacherId: uploadTeacher.value,
      subject: uploadSubject.value,
      classLevel: uploadClass.value,
      type: document.getElementById("uploadType").value,
      year: document.getElementById("uploadYear").value.trim(),
      title: document.getElementById("uploadTitle").value.trim(),
      note: document.getElementById("uploadNote").value.trim(),
      fileName: file.name,
      fileDataUrl
    });

    state = normalizeState(response.state);
    sanitizeSelections();
    uploadForm.reset();
    populateUploadTeachers();
    renderAll();
    uploadMessage.textContent = "Der Upload wurde auf GitHub gespeichert und wartet jetzt auf Freigabe.";
  } catch (error) {
    uploadMessage.textContent = error.message || "Der Upload konnte nicht gespeichert werden.";
  }
}

async function handleTeacherSave(event) {
  event.preventDefault();
  const teacherId = document.getElementById("teacherEditId").value.trim();
  const name = document.getElementById("teacherName").value.trim();
  const code = document.getElementById("teacherCode").value.trim().toUpperCase();
  const subjects = document.getElementById("teacherSubjects").value
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);

  if (!name || !code || !subjects.length) {
    return;
  }

  try {
    const response = await postAction("saveTeacher", { teacherId, name, code, subjects });
    state = normalizeState(response.state);
    sanitizeSelections();
    teacherForm.reset();
    document.getElementById("teacherEditId").value = "";
    renderAll();
    renderAdmin();
  } catch (error) {
    window.alert(error.message || "Der Lehrer konnte nicht gespeichert werden.");
  }
}

function renderAdmin() {
  renderAdminFilters();
  renderAdminTeachers();
  renderPendingUploads();
  renderApprovedUploads();
}

function renderAdminFilters() {
  adminTeacherFilterSelect.innerHTML = "";
  adminSubjectFilterSelect.innerHTML = "";
  adminClassFilterSelect.innerHTML = "";

  const teacherOptions = ['<option value="">Lehrer auswählen</option>'].concat(
    state.teachers
      .slice()
      .sort((a, b) => a.code.localeCompare(b.code, "de"))
      .map((teacher) => `<option value="${teacher.id}">${escapeHtml(teacher.name)} (${escapeHtml(teacher.code)})</option>`)
  );
  adminTeacherFilterSelect.innerHTML = teacherOptions.join("");
  adminTeacherFilterSelect.value = adminTeacherFilter;

  const teacher = state.teachers.find((entry) => entry.id === adminTeacherFilter);
  const subjectOptions = ['<option value="">Fach auswählen</option>'];
  if (teacher) {
    teacher.subjects.forEach((subject) => {
      subjectOptions.push(`<option value="${escapeAttribute(subject)}">${escapeHtml(subject)}</option>`);
    });
  }
  adminSubjectFilterSelect.innerHTML = subjectOptions.join("");
  adminSubjectFilterSelect.value = adminSubjectFilter;

  const classOptions = ['<option value="">Klasse auswählen</option>'].concat(
    CLASS_LEVELS.map((level) => `<option value="${level}">${level}</option>`)
  );
  adminClassFilterSelect.innerHTML = classOptions.join("");
  adminClassFilterSelect.value = adminClassFilter;
}

function handleAdminTeacherFilterChange() {
  adminTeacherFilter = adminTeacherFilterSelect.value;
  adminSubjectFilter = "";
  adminClassFilter = "";
  renderAdmin();
}

function handleAdminSubjectFilterChange() {
  adminSubjectFilter = adminSubjectFilterSelect.value;
  adminClassFilter = "";
  renderAdmin();
}

function handleAdminClassFilterChange() {
  adminClassFilter = adminClassFilterSelect.value;
  renderAdmin();
}

function renderAdminTeachers() {
  adminTeacherList.innerHTML = "";

  if (!state.teachers.length) {
    adminTeacherList.textContent = "Noch keine Lehrer vorhanden.";
    return;
  }

  state.teachers
    .slice()
    .sort((a, b) => a.code.localeCompare(b.code, "de"))
    .forEach((teacher) => {
      const item = document.createElement("article");
      item.className = "admin-item";
      item.innerHTML = `
        <h4>${escapeHtml(teacher.name)} (${escapeHtml(teacher.code)})</h4>
        <p class="meta-line">${escapeHtml(teacher.subjects.join(", "))}</p>
        <div class="admin-actions">
          <button type="button" class="ghost-btn" data-edit-teacher="${teacher.id}">Bearbeiten</button>
          <button type="button" class="ghost-btn" data-delete-teacher="${teacher.id}">Löschen</button>
        </div>
      `;
      adminTeacherList.appendChild(item);
    });

  adminTeacherList.querySelectorAll("[data-edit-teacher]").forEach((button) => {
    button.addEventListener("click", () => {
      const teacher = state.teachers.find((entry) => entry.id === button.dataset.editTeacher);
      if (!teacher) {
        return;
      }
      document.getElementById("teacherEditId").value = teacher.id;
      document.getElementById("teacherName").value = teacher.name;
      document.getElementById("teacherCode").value = teacher.code;
      document.getElementById("teacherSubjects").value = teacher.subjects.join(", ");
      teacherForm.scrollIntoView({ behavior: "smooth", block: "nearest" });
    });
  });

  adminTeacherList.querySelectorAll("[data-delete-teacher]").forEach((button) => {
    button.addEventListener("click", async () => {
      const teacherId = button.dataset.deleteTeacher;
      const teacher = state.teachers.find((entry) => entry.id === teacherId);
      const confirmed = window.confirm(`Soll ${teacher?.name ?? "dieser Lehrer"} wirklich gelöscht werden?`);
      if (!confirmed) {
        return;
      }

      try {
        const response = await postAction("deleteTeacher", { teacherId });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Lehrer konnte nicht gelöscht werden.");
      }
    });
  });
}

function renderPendingUploads() {
  pendingList.innerHTML = "";

  if (!adminTeacherFilter || !adminSubjectFilter || !adminClassFilter) {
    pendingList.textContent = "Bitte zuerst Lehrer, Fach und Klasse auswählen.";
    return;
  }

  const items = state.pendingUploads
    .filter((item) => item.teacherId === adminTeacherFilter && item.subject === adminSubjectFilter && item.classLevel === adminClassFilter)
    .sort(sortUploads);

  if (!items.length) {
    pendingList.textContent = "Zurzeit warten keine Uploads auf Prüfung.";
    return;
  }

  items.forEach((item) => {
    const teacher = state.teachers.find((entry) => entry.id === item.teacherId);
    const wrapper = document.createElement("article");
    wrapper.className = "admin-item";
    wrapper.innerHTML = `
      <h4>${escapeHtml(item.title)}</h4>
      <p class="meta-line">${escapeHtml(teacher ? `${teacher.name} (${teacher.code})` : "Unbekannter Lehrer")} • ${escapeHtml(item.subject)} • Klasse ${escapeHtml(item.classLevel)} • ${escapeHtml(item.type)}</p>
      <p class="meta-line">${escapeHtml(getLongDateLabel(item))} • Datei: ${escapeHtml(item.fileName)}</p>
      <div class="inline-fields">
        <input type="text" value="${escapeAttribute(item.title)}" data-field="title" data-id="${item.id}">
        <input type="number" value="${escapeAttribute(item.year)}" data-field="year" data-id="${item.id}">
        <input type="text" value="${escapeAttribute(item.classLevel)}" data-field="classLevel" data-id="${item.id}">
        <select data-field="type" data-id="${item.id}">
          <option value="Klassenarbeit" ${item.type === "Klassenarbeit" ? "selected" : ""}>Klassenarbeit</option>
          <option value="Test" ${item.type === "Test" ? "selected" : ""}>Test</option>
        </select>
        <textarea rows="2" data-field="note" data-id="${item.id}">${escapeHtml(item.note ?? "")}</textarea>
      </div>
      <div class="admin-actions">
        <button type="button" class="primary-btn" data-approve="${item.id}">Freigeben</button>
        <button type="button" class="ghost-btn" data-reject="${item.id}">Löschen</button>
      </div>
    `;
    pendingList.appendChild(wrapper);
  });

  attachPendingUploadEvents();
}

function attachPendingUploadEvents() {
  pendingList.querySelectorAll("[data-field]").forEach((field) => {
    field.addEventListener("change", async () => {
      try {
        const response = await postAction("updatePendingUpload", {
          uploadId: field.dataset.id,
          changes: { [field.dataset.field]: field.value.trim() }
        });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Upload konnte nicht bearbeitet werden.");
      }
    });
  });

  pendingList.querySelectorAll("[data-approve]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        const response = await postAction("approveUpload", { uploadId: button.dataset.approve });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Upload konnte nicht freigegeben werden.");
      }
    });
  });

  pendingList.querySelectorAll("[data-reject]").forEach((button) => {
    button.addEventListener("click", async () => {
      const confirmed = window.confirm("Soll dieser Upload wirklich gelöscht werden?");
      if (!confirmed) {
        return;
      }

      try {
        const response = await postAction("rejectUpload", { uploadId: button.dataset.reject });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Upload konnte nicht gelöscht werden.");
      }
    });
  });
}

function renderApprovedUploads() {
  approvedList.innerHTML = "";

  if (!adminTeacherFilter || !adminSubjectFilter || !adminClassFilter) {
    approvedList.textContent = "Bitte zuerst Lehrer, Fach und Klasse auswählen.";
    return;
  }

  const items = state.approvedUploads
    .filter((item) => item.teacherId === adminTeacherFilter && item.subject === adminSubjectFilter && item.classLevel === adminClassFilter)
    .sort(sortUploads);

  if (!items.length) {
    approvedList.textContent = "Noch keine freigegebenen Inhalte vorhanden.";
    return;
  }

  items.forEach((item) => {
    const teacher = state.teachers.find((entry) => entry.id === item.teacherId);
    const wrapper = document.createElement("article");
    wrapper.className = "admin-item";
    wrapper.innerHTML = `
      <h4>${escapeHtml(item.title)}</h4>
      <p class="meta-line">${escapeHtml(teacher ? `${teacher.name} (${teacher.code})` : "Unbekannter Lehrer")} • ${escapeHtml(item.subject)} • Klasse ${escapeHtml(item.classLevel)} • ${escapeHtml(item.type)}</p>
      <p class="meta-line">${escapeHtml(getLongDateLabel(item))} • Datei: ${escapeHtml(item.fileName)}</p>
      <div class="inline-fields">
        <input type="text" value="${escapeAttribute(item.title)}" data-approved-field="title" data-id="${item.id}">
        <input type="number" value="${escapeAttribute(item.year)}" data-approved-field="year" data-id="${item.id}">
        <input type="text" value="${escapeAttribute(item.classLevel)}" data-approved-field="classLevel" data-id="${item.id}">
        <select data-approved-field="type" data-id="${item.id}">
          <option value="Klassenarbeit" ${item.type === "Klassenarbeit" ? "selected" : ""}>Klassenarbeit</option>
          <option value="Test" ${item.type === "Test" ? "selected" : ""}>Test</option>
        </select>
        <input type="text" value="${escapeAttribute(item.subject)}" data-approved-field="subject" data-id="${item.id}">
        <textarea rows="2" data-approved-field="note" data-id="${item.id}">${escapeHtml(item.note ?? "")}</textarea>
      </div>
      <div class="admin-actions">
        <button type="button" class="ghost-btn" data-unapprove="${item.id}">Zur Prüfung zurück</button>
        <button type="button" class="ghost-btn" data-delete-approved="${item.id}">Löschen</button>
      </div>
    `;
    approvedList.appendChild(wrapper);
  });

  attachApprovedUploadEvents();
}

function attachApprovedUploadEvents() {
  approvedList.querySelectorAll("[data-approved-field]").forEach((field) => {
    field.addEventListener("change", async () => {
      try {
        const response = await postAction("updateApprovedUpload", {
          uploadId: field.dataset.id,
          changes: { [field.dataset.approvedField]: field.value.trim() }
        });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Eintrag konnte nicht bearbeitet werden.");
      }
    });
  });

  approvedList.querySelectorAll("[data-unapprove]").forEach((button) => {
    button.addEventListener("click", async () => {
      try {
        const response = await postAction("moveBackToPending", { uploadId: button.dataset.unapprove });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Eintrag konnte nicht zurückgestellt werden.");
      }
    });
  });

  approvedList.querySelectorAll("[data-delete-approved]").forEach((button) => {
    button.addEventListener("click", async () => {
      const confirmed = window.confirm("Soll dieser freigegebene Eintrag wirklich gelöscht werden?");
      if (!confirmed) {
        return;
      }

      try {
        const response = await postAction("deleteApprovedUpload", { uploadId: button.dataset.deleteApproved });
        state = normalizeState(response.state);
        sanitizeSelections();
        renderAll();
        renderAdmin();
      } catch (error) {
        window.alert(error.message || "Der Eintrag konnte nicht gelöscht werden.");
      }
    });
  });
}

function sortUploads(a, b) {
  if (a.type !== b.type) {
    return a.type === "Klassenarbeit" ? -1 : 1;
  }
  return getSortableTimestamp(b) - getSortableTimestamp(a);
}

function openModal(modal) {
  modal.classList.remove("hidden");
  modal.setAttribute("aria-hidden", "false");
}

function closeModal(modal) {
  modal.classList.add("hidden");
  modal.setAttribute("aria-hidden", "true");
}

function getSelectedTeacher() {
  return state.teachers.find((teacher) => teacher.id === selectedTeacherId) || null;
}

function getFilteredTeachers() {
  const query = teacherSearchTerm.trim().toLowerCase();
  const teachers = state.teachers.slice().sort((a, b) => a.code.localeCompare(b.code, "de"));
  if (!query) {
    return teachers;
  }
  return teachers.filter((teacher) => `${teacher.name} ${teacher.code}`.toLowerCase().includes(query));
}

function getVisibleEntries() {
  return state.approvedUploads
    .filter((item) => item.teacherId === selectedTeacherId && item.subject === selectedSubject && item.classLevel === selectedClass)
    .sort(sortUploads);
}

function hasApprovedEntriesForClass(classLevel) {
  return state.approvedUploads.some((item) => item.teacherId === selectedTeacherId && item.subject === selectedSubject && item.classLevel === classLevel);
}

function isImageFile(fileName) {
  const lowerName = String(fileName ?? "").toLowerCase();
  return [".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg"].some((ending) => lowerName.endsWith(ending));
}

function isFramePreviewableFile(fileName, previewUrl) {
  const lowerName = String(fileName ?? "").toLowerCase();
  return Boolean(previewUrl) && [".pdf", ".txt", ".html"].some((ending) => lowerName.endsWith(ending));
}

function getSortableTimestamp(item) {
  const year = Number(item.year);
  return Number.isNaN(year) ? 0 : new Date(`${year}-12-31`).getTime();
}

function getShortDateLabel(item) {
  return `Jahr ${item.year}`;
}

function getLongDateLabel(item) {
  return `Geschrieben im Jahr ${item.year}`;
}

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("Datei konnte nicht gelesen werden."));
    reader.readAsDataURL(file);
  });
}

async function postAction(action, payload) {
  return apiRequest("/api/action", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action, payload })
  });
}

async function apiRequest(url, options = {}) {
  const response = await fetch(url, options);
  let body = null;

  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    throw new Error(body?.error || "Die Anfrage konnte nicht verarbeitet werden.");
  }

  return body;
}

function handleTeacherSearch() {
  teacherSearchTerm = teacherSearch.value;
  const visibleIds = new Set(getFilteredTeachers().map((teacher) => teacher.id));
  if (selectedTeacherId && !visibleIds.has(selectedTeacherId)) {
    selectedTeacherId = null;
    selectedSubject = null;
    selectedClass = null;
    selectedEntryId = null;
  }
  renderAll();
}

function handleTeacherSelection() {
  selectedTeacherId = teacherSelect.value || null;
  selectedSubject = null;
  selectedClass = null;
  selectedEntryId = null;
  renderAll();
}

function handleSubjectSelection() {
  selectedSubject = subjectSelect.value || null;
  selectedClass = null;
  selectedEntryId = null;
  renderAll();
}

function handleClassSelection() {
  selectedClass = classSelect.value || null;
  selectedEntryId = null;
  renderAll();
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function escapeAttribute(value) {
  return escapeHtml(value ?? "");
}
