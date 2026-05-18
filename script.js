const SITE_PASSWORD = "1208";
const ADMIN_PASSWORD = "2702";
const STORAGE_KEY = "schularchiv-state-v1";
const CLASS_LEVELS = ["5", "6", "7", "8", "9", "10", "11", "12"];

const defaultState = {
  teachers: [
    {
      id: crypto.randomUUID(),
      name: "Herr Wagner",
      code: "WAG",
      subjects: ["Mathematik", "Physik"]
    }
  ],
  pendingUploads: [],
  approvedUploads: []
};

let state = loadState();
let selectedTeacherId = null;
let selectedSubject = null;
let selectedClass = null;
let adminTeacherFilter = "";
let adminSubjectFilter = "";
let adminClassFilter = "";

const gateScreen = document.getElementById("gateScreen");
const appScreen = document.getElementById("appScreen");
const gateForm = document.getElementById("gateForm");
const gateMessage = document.getElementById("gateMessage");
const teacherList = document.getElementById("teacherList");
const subjectList = document.getElementById("subjectList");
const classList = document.getElementById("classList");
const archiveList = document.getElementById("archiveList");
const archivePanel = document.querySelector(".archive-panel");
const subjectHint = document.getElementById("subjectHint");
const classHint = document.getElementById("classHint");
const archiveHint = document.getElementById("archiveHint");
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
  openModal(uploadModal);
  populateUploadTeachers();
});
uploadTeacher.addEventListener("change", populateUploadSubjects);
uploadForm.addEventListener("submit", handleUpload);
teacherForm.addEventListener("submit", handleTeacherSave);
adminTeacherFilterSelect.addEventListener("change", handleAdminTeacherFilterChange);
adminSubjectFilterSelect.addEventListener("change", handleAdminSubjectFilterChange);
adminClassFilterSelect.addEventListener("change", handleAdminClassFilterChange);

document.querySelectorAll("[data-close]").forEach((button) => {
  button.addEventListener("click", () => {
    const modal = document.getElementById(button.dataset.close);
    closeModal(modal);
  });
});

window.addEventListener("click", (event) => {
  if (event.target.classList.contains("modal")) {
    closeModal(event.target);
  }
});

renderAll();

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

function loadState() {
  const storedState = localStorage.getItem(STORAGE_KEY);
  if (!storedState) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(defaultState));
    return structuredClone(defaultState);
  }

  try {
    const parsed = JSON.parse(storedState);
    return {
      teachers: Array.isArray(parsed.teachers) ? parsed.teachers : structuredClone(defaultState.teachers),
      pendingUploads: Array.isArray(parsed.pendingUploads) ? parsed.pendingUploads : [],
      approvedUploads: Array.isArray(parsed.approvedUploads) ? parsed.approvedUploads : []
    };
  } catch {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(defaultState));
    return structuredClone(defaultState);
  }
}

function saveState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function renderAll() {
  renderTeachers();
  renderSubjects();
  renderClasses();
  renderArchive();
  populateUploadTeachers();
}

function renderTeachers() {
  teacherList.innerHTML = "";

  if (!state.teachers.length) {
    teacherList.textContent = "Noch keine Lehrer angelegt.";
    return;
  }

  state.teachers
    .sort((a, b) => a.code.localeCompare(b.code, "de"))
    .forEach((teacher) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = "chip";
      if (teacher.id === selectedTeacherId) {
        button.classList.add("active");
      }
      button.innerHTML = `<strong>${escapeHtml(teacher.code)}</strong><span>${escapeHtml(teacher.name)}</span>`;
      button.addEventListener("click", () => {
        selectedTeacherId = teacher.id;
        selectedSubject = null;
        selectedClass = null;
        renderAll();
      });
      teacherList.appendChild(button);
    });
}

function renderSubjects() {
  subjectList.innerHTML = "";
  const teacher = getSelectedTeacher();

  if (!teacher) {
    subjectHint.textContent = "Bitte zuerst einen Lehrer auswählen.";
    subjectList.textContent = "Keine Fächer sichtbar.";
    return;
  }

  subjectHint.textContent = `${teacher.name} (${teacher.code})`;

  teacher.subjects.forEach((subject) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "chip";
    if (subject === selectedSubject) {
      button.classList.add("active");
    }
    button.innerHTML = `<strong>${escapeHtml(subject)}</strong><span>Fach öffnen</span>`;
    button.addEventListener("click", () => {
      selectedSubject = subject;
      selectedClass = null;
      renderAll();
    });
    subjectList.appendChild(button);
  });
}

function renderClasses() {
  classList.innerHTML = "";

  if (!selectedSubject) {
    classHint.textContent = "Bitte zuerst ein Fach auswählen.";
    classList.textContent = "Noch keine Klasse ausgewählt.";
    return;
  }

  classHint.textContent = `${selectedSubject}`;

  CLASS_LEVELS.forEach((classLevel) => {
    const hasEntries = hasApprovedEntriesForClass(classLevel);
    const button = document.createElement("button");
    button.type = "button";
    button.className = "chip";
    if (!hasEntries) {
      button.classList.add("disabled");
    }
    if (classLevel === selectedClass) {
      button.classList.add("active");
    }
    button.innerHTML = `<strong>Klasse ${classLevel}</strong><span>${hasEntries ? "Arbeiten ansehen" : "Noch keine Inhalte"}</span>`;
    button.addEventListener("click", () => {
      if (!hasEntries) {
        return;
      }
      selectedClass = classLevel;
      renderAll();
    });
    classList.appendChild(button);
  });
}

function renderArchive() {
  archiveList.innerHTML = "";

  if (!selectedTeacherId || !selectedSubject || !selectedClass) {
    archivePanel.classList.add("hidden-panel");
    archiveHint.textContent = "Bitte Lehrer, Fach und Klasse auswählen.";
    archiveList.textContent = "Hier erscheinen die freigegebenen Inhalte.";
    return;
  }

  archivePanel.classList.remove("hidden-panel");

  const teacher = getSelectedTeacher();
  archiveHint.textContent = `${teacher.code} • ${selectedSubject} • Klasse ${selectedClass}`;

  const items = state.approvedUploads
    .filter((item) => (
      item.teacherId === selectedTeacherId &&
      item.subject === selectedSubject &&
      item.classLevel === selectedClass
    ))
    .sort(sortUploads);

  if (!items.length) {
    archiveList.textContent = "Für diese Auswahl gibt es noch keine freigegebenen Arbeiten.";
    return;
  }

  const groupedItems = {
    Klassenarbeit: items.filter((item) => item.type === "Klassenarbeit"),
    Test: items.filter((item) => item.type === "Test")
  };

  Object.entries(groupedItems).forEach(([type, entries]) => {
    if (!entries.length) {
      return;
    }
    const group = document.createElement("section");
    group.className = "archive-group";
    group.innerHTML = `
      <div class="archive-group-head">
        <h4>${type}</h4>
        <span class="badge">${entries.length} Einträge</span>
      </div>
    `;

    entries.forEach((item) => {
      const fragment = archiveItemTemplate.content.cloneNode(true);
      fragment.querySelector(".pill").textContent = item.type;
      fragment.querySelector(".summary-date").textContent = getShortDateLabel(item);
      fragment.querySelector(".date-line").textContent = getLongDateLabel(item);
      fragment.querySelector("h4").textContent = item.title;
      fragment.querySelector(".meta-line").textContent = `${teacher.name} • ${item.subject} • Klasse ${item.classLevel} • Jahr ${item.year}`;
      const noteLine = fragment.querySelector(".note-line");
      if (item.note) {
        noteLine.textContent = item.note;
        noteLine.classList.remove("hidden");
      }
      const previewWrap = fragment.querySelector(".preview-frame-wrap");
      const previewFrame = fragment.querySelector(".preview-frame");
      if (isPreviewableFile(item.fileName, item.fileDataUrl)) {
        previewFrame.src = item.fileDataUrl;
        previewWrap.classList.remove("hidden");
      }
      const link = fragment.querySelector("a");
      link.href = item.fileDataUrl;
      group.appendChild(fragment);
    });

    archiveList.appendChild(group);
  });
}

function populateUploadTeachers() {
  uploadTeacher.innerHTML = "";
  uploadClass.innerHTML = CLASS_LEVELS.map((level) => `<option value="${level}">${level}</option>`).join("");

  state.teachers
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

  const fileDataUrl = await readFileAsDataUrl(file);

  state.pendingUploads.push({
    id: crypto.randomUUID(),
    teacherId: uploadTeacher.value,
    subject: uploadSubject.value,
    classLevel: uploadClass.value,
    type: document.getElementById("uploadType").value,
    year: document.getElementById("uploadYear").value.trim(),
    title: document.getElementById("uploadTitle").value.trim(),
    note: document.getElementById("uploadNote").value.trim(),
    fileName: file.name,
    fileDataUrl,
    uploadedAt: new Date().toISOString()
  });

  saveState();
  uploadForm.reset();
  populateUploadTeachers();
  uploadMessage.textContent = "Der Upload wurde gespeichert und wartet jetzt auf Freigabe in der Adminzentrale.";
}

function handleTeacherSave(event) {
  event.preventDefault();
  const editId = document.getElementById("teacherEditId").value.trim();
  const name = document.getElementById("teacherName").value.trim();
  const code = document.getElementById("teacherCode").value.trim().toUpperCase();
  const subjects = document.getElementById("teacherSubjects").value
    .split(",")
    .map((entry) => entry.trim())
    .filter(Boolean);

  if (!name || !code || !subjects.length) {
    return;
  }

  const duplicateTeacher = state.teachers.find((teacher) => teacher.code === code && teacher.id !== editId);

  if (duplicateTeacher) {
    window.alert("Dieses Kürzel gibt es bereits. Bitte verwende ein anderes Kürzel.");
    return;
  }

  const existingTeacher = state.teachers.find((teacher) => teacher.id === editId);

  if (existingTeacher) {
    existingTeacher.name = name;
    existingTeacher.subjects = subjects;
    existingTeacher.code = code;
  } else {
    state.teachers.push({
      id: crypto.randomUUID(),
      name,
      code,
      subjects
    });
  }

  saveState();
  teacherForm.reset();
  document.getElementById("teacherEditId").value = "";
  renderAll();
  renderAdmin();
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

  const teacherOptions = [`<option value="">Lehrer auswählen</option>`].concat(
    state.teachers
      .sort((a, b) => a.code.localeCompare(b.code, "de"))
      .map((teacher) => `<option value="${teacher.id}">${escapeHtml(teacher.name)} (${escapeHtml(teacher.code)})</option>`)
  );
  adminTeacherFilterSelect.innerHTML = teacherOptions.join("");
  adminTeacherFilterSelect.value = adminTeacherFilter;

  const selectedTeacher = state.teachers.find((teacher) => teacher.id === adminTeacherFilter);
  const subjectOptions = [`<option value="">Fach auswählen</option>`];
  if (selectedTeacher) {
    selectedTeacher.subjects.forEach((subject) => {
      subjectOptions.push(`<option value="${escapeAttribute(subject)}">${escapeHtml(subject)}</option>`);
    });
  }
  adminSubjectFilterSelect.innerHTML = subjectOptions.join("");
  adminSubjectFilterSelect.value = adminSubjectFilter;

  const classOptions = [`<option value="">Klasse auswählen</option>`].concat(
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
    button.addEventListener("click", () => {
      const teacherId = button.dataset.deleteTeacher;
      const stillUsed = [...state.pendingUploads, ...state.approvedUploads].some((item) => item.teacherId === teacherId);
      if (stillUsed) {
        window.alert("Dieser Lehrer wird noch in Uploads verwendet und kann deshalb nicht gelöscht werden.");
        return;
      }
      const teacher = state.teachers.find((entry) => entry.id === teacherId);
      const confirmed = window.confirm(`Soll ${teacher?.name ?? "dieser Lehrer"} wirklich gelöscht werden?`);
      if (!confirmed) {
        return;
      }
      state.teachers = state.teachers.filter((teacher) => teacher.id !== teacherId);
      if (selectedTeacherId === teacherId) {
        selectedTeacherId = null;
        selectedSubject = null;
        selectedClass = null;
      }
      saveState();
      renderAll();
      renderAdmin();
    });
  });
}

function renderPendingUploads() {
  pendingList.innerHTML = "";

  if (!adminTeacherFilter || !adminSubjectFilter || !adminClassFilter) {
    pendingList.textContent = "Bitte zuerst Lehrer, Fach und Klasse auswählen.";
    return;
  }

  const filteredPendingUploads = state.pendingUploads.filter((item) => (
    item.teacherId === adminTeacherFilter &&
    item.subject === adminSubjectFilter &&
    item.classLevel === adminClassFilter
  ));

  if (!filteredPendingUploads.length) {
    pendingList.textContent = "Zurzeit warten keine Uploads auf Prüfung.";
    return;
  }

  filteredPendingUploads
    .sort(sortUploads)
    .forEach((item) => {
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
          <a class="secondary-btn" href="${item.fileDataUrl}" download="${escapeAttribute(item.fileName)}">Datei öffnen</a>
          <button type="button" class="primary-btn" data-approve="${item.id}">Freigeben</button>
          <button type="button" class="ghost-btn" data-reject="${item.id}">Löschen</button>
        </div>
      `;
      pendingList.appendChild(wrapper);
    });

  pendingList.querySelectorAll("[data-field]").forEach((field) => {
    field.addEventListener("change", () => {
      const item = state.pendingUploads.find((entry) => entry.id === field.dataset.id);
      if (!item) {
        return;
      }
      item[field.dataset.field] = field.value.trim();
      saveState();
    });
  });

  pendingList.querySelectorAll("[data-approve]").forEach((button) => {
    button.addEventListener("click", () => approveUpload(button.dataset.approve));
  });

  pendingList.querySelectorAll("[data-reject]").forEach((button) => {
    button.addEventListener("click", () => rejectUpload(button.dataset.reject));
  });
}

function approveUpload(uploadId) {
  const item = state.pendingUploads.find((entry) => entry.id === uploadId);
  if (!item) {
    return;
  }

  state.pendingUploads = state.pendingUploads.filter((entry) => entry.id !== uploadId);
  state.approvedUploads.push(item);
  saveState();
  renderAll();
  renderAdmin();
}

function rejectUpload(uploadId) {
  const confirmed = window.confirm("Soll dieser Upload wirklich gelöscht werden?");
  if (!confirmed) {
    return;
  }
  state.pendingUploads = state.pendingUploads.filter((entry) => entry.id !== uploadId);
  saveState();
  renderAdmin();
}

function renderApprovedUploads() {
  approvedList.innerHTML = "";

  if (!adminTeacherFilter || !adminSubjectFilter || !adminClassFilter) {
    approvedList.textContent = "Bitte zuerst Lehrer, Fach und Klasse auswählen.";
    return;
  }

  const filteredApprovedUploads = state.approvedUploads.filter((item) => (
    item.teacherId === adminTeacherFilter &&
    item.subject === adminSubjectFilter &&
    item.classLevel === adminClassFilter
  ));

  if (!filteredApprovedUploads.length) {
    approvedList.textContent = "Noch keine freigegebenen Inhalte vorhanden.";
    return;
  }

  filteredApprovedUploads
    .sort(sortUploads)
    .forEach((item) => {
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
          <a class="secondary-btn" href="${item.fileDataUrl}" download="${escapeAttribute(item.fileName)}">Datei öffnen</a>
          <button type="button" class="ghost-btn" data-unapprove="${item.id}">Zur Prüfung zurück</button>
          <button type="button" class="ghost-btn" data-delete-approved="${item.id}">Löschen</button>
        </div>
      `;
      approvedList.appendChild(wrapper);
    });

  approvedList.querySelectorAll("[data-approved-field]").forEach((field) => {
    field.addEventListener("change", () => {
      const item = state.approvedUploads.find((entry) => entry.id === field.dataset.id);
      if (!item) {
        return;
      }
      item[field.dataset.approvedField] = field.value.trim();
      saveState();
      renderAll();
    });
  });

  approvedList.querySelectorAll("[data-unapprove]").forEach((button) => {
    button.addEventListener("click", () => moveBackToPending(button.dataset.unapprove));
  });

  approvedList.querySelectorAll("[data-delete-approved]").forEach((button) => {
    button.addEventListener("click", () => deleteApprovedUpload(button.dataset.deleteApproved));
  });
}

function moveBackToPending(uploadId) {
  const item = state.approvedUploads.find((entry) => entry.id === uploadId);
  if (!item) {
    return;
  }
  state.approvedUploads = state.approvedUploads.filter((entry) => entry.id !== uploadId);
  state.pendingUploads.push(item);
  saveState();
  renderAll();
  renderAdmin();
}

function deleteApprovedUpload(uploadId) {
  const confirmed = window.confirm("Soll dieser freigegebene Eintrag wirklich gelöscht werden?");
  if (!confirmed) {
    return;
  }
  state.approvedUploads = state.approvedUploads.filter((entry) => entry.id !== uploadId);
  saveState();
  renderAll();
  renderAdmin();
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

function readFileAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = () => reject(new Error("Datei konnte nicht gelesen werden."));
    reader.readAsDataURL(file);
  });
}

function formatDisplayDate(dateValue) {
  if (!dateValue) {
    return "";
  }
  return new Intl.DateTimeFormat("de-DE", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  }).format(new Date(dateValue));
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

function hasApprovedEntriesForClass(classLevel) {
  return state.approvedUploads.some((item) => (
    item.teacherId === selectedTeacherId &&
    item.subject === selectedSubject &&
    item.classLevel === classLevel
  ));
}

function isPreviewableFile(fileName, dataUrl) {
  const lowerName = String(fileName ?? "").toLowerCase();
  return dataUrl.startsWith("data:application/pdf") ||
    [".pdf", ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".txt", ".html"].some((ending) => lowerName.endsWith(ending));
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
