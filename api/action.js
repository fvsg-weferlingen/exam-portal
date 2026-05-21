const crypto = require("node:crypto");
const { mutateState, saveUploadFile } = require("./_lib/github-store");

module.exports = async function handler(request, response) {
  if (request.method !== "POST") {
    response.status(405).json({ error: "Nur POST ist erlaubt." });
    return;
  }

  const { action, payload } = request.body || {};

  try {
    const result = await handleAction(action, payload || {});
    response.status(200).json(result);
  } catch (error) {
    const status = error.statusCode || error.status || 400;
    response.status(status).json({ error: error.message || "Aktion fehlgeschlagen." });
  }
};

async function handleAction(action, payload) {
  switch (action) {
    case "saveTeacher":
      return mutateState("Save teacher", (state) => {
        const name = String(payload.name || "").trim();
        const code = String(payload.code || "").trim().toUpperCase();
        const subjects = Array.isArray(payload.subjects)
          ? payload.subjects.map((subject) => String(subject).trim()).filter(Boolean)
          : [];

        if (!name || !code || !subjects.length) {
          throw createError("Bitte Name, Kürzel und mindestens ein Fach angeben.");
        }

        const duplicateTeacher = state.teachers.find((teacher) => teacher.code === code && teacher.id !== payload.teacherId);
        if (duplicateTeacher) {
          throw createError("Dieses Kürzel gibt es bereits.");
        }

        const existingTeacher = state.teachers.find((teacher) => teacher.id === payload.teacherId);
        if (existingTeacher) {
          existingTeacher.name = name;
          existingTeacher.code = code;
          existingTeacher.subjects = subjects;
        } else {
          state.teachers.push({
            id: crypto.randomUUID(),
            name,
            code,
            subjects
          });
        }
      });

    case "deleteTeacher":
      return mutateState("Delete teacher", (state) => {
        const teacherId = String(payload.teacherId || "");
        const isUsed = [...state.pendingUploads, ...state.approvedUploads].some((item) => item.teacherId === teacherId);
        if (isUsed) {
          throw createError("Dieser Lehrer wird noch in Uploads verwendet und kann nicht gelöscht werden.");
        }
        state.teachers = state.teachers.filter((teacher) => teacher.id !== teacherId);
      });

    case "submitUpload":
      return mutateState("Submit upload", async (state) => {
        const teacher = state.teachers.find((entry) => entry.id === payload.teacherId);
        if (!teacher) {
          throw createError("Der ausgewählte Lehrer wurde nicht gefunden.");
        }

        assertImageUpload(payload.fileName, payload.fileDataUrl);

        const savedFile = await saveUploadFile({
          fileName: payload.fileName,
          fileDataUrl: payload.fileDataUrl,
          teacherCode: teacher.code,
          subject: payload.subject,
          classLevel: payload.classLevel,
          year: payload.year
        });

        state.pendingUploads.push({
          id: crypto.randomUUID(),
          teacherId: payload.teacherId,
          subject: String(payload.subject || "").trim(),
          classLevel: String(payload.classLevel || "").trim(),
          type: String(payload.type || "").trim(),
          year: String(payload.year || "").trim(),
          title: String(payload.title || "").trim(),
          note: String(payload.note || "").trim(),
          fileName: String(payload.fileName || "").trim(),
          filePath: savedFile.path,
          previewUrl: savedFile.previewUrl,
          uploadedAt: new Date().toISOString()
        });
      });

    case "updatePendingUpload":
      return mutateState("Update pending upload", (state) => {
        updateUploadEntry(state.pendingUploads, payload.uploadId, payload.changes);
      });

    case "replacePendingUploadFile":
      return mutateState("Replace pending upload file", async (state) => {
        await replaceUploadFile(state.pendingUploads, state.teachers, payload);
      });

    case "approveUpload":
      return mutateState("Approve upload", (state) => {
        const upload = extractUpload(state.pendingUploads, payload.uploadId);
        state.approvedUploads.push(upload);
      });

    case "rejectUpload":
      return mutateState("Delete pending upload", (state) => {
        removeUpload(state.pendingUploads, payload.uploadId);
      });

    case "updateApprovedUpload":
      return mutateState("Update approved upload", (state) => {
        updateUploadEntry(state.approvedUploads, payload.uploadId, payload.changes);
      });

    case "replaceApprovedUploadFile":
      return mutateState("Replace approved upload file", async (state) => {
        await replaceUploadFile(state.approvedUploads, state.teachers, payload);
      });

    case "moveBackToPending":
      return mutateState("Move upload back to pending", (state) => {
        const upload = extractUpload(state.approvedUploads, payload.uploadId);
        state.pendingUploads.push(upload);
      });

    case "deleteApprovedUpload":
      return mutateState("Delete approved upload", (state) => {
        removeUpload(state.approvedUploads, payload.uploadId);
      });

    default:
      throw createError("Unbekannte Aktion.");
  }
}

function updateUploadEntry(collection, uploadId, changes) {
  const upload = collection.find((entry) => entry.id === uploadId);
  if (!upload) {
    throw createError("Der Upload wurde nicht gefunden.");
  }

  const allowedFields = new Set(["title", "year", "classLevel", "type", "note", "subject"]);
  Object.entries(changes || {}).forEach(([key, value]) => {
    if (allowedFields.has(key)) {
      upload[key] = String(value || "").trim();
    }
  });
}

function extractUpload(collection, uploadId) {
  const index = collection.findIndex((entry) => entry.id === uploadId);
  if (index === -1) {
    throw createError("Der Upload wurde nicht gefunden.");
  }

  const [upload] = collection.splice(index, 1);
  return upload;
}

function removeUpload(collection, uploadId) {
  const index = collection.findIndex((entry) => entry.id === uploadId);
  if (index === -1) {
    throw createError("Der Upload wurde nicht gefunden.");
  }
  collection.splice(index, 1);
}

async function replaceUploadFile(collection, teachers, payload) {
  const upload = collection.find((entry) => entry.id === payload.uploadId);
  if (!upload) {
    throw createError("Der Upload wurde nicht gefunden.");
  }

  const teacher = teachers.find((entry) => entry.id === upload.teacherId);
  if (!teacher) {
    throw createError("Der zugehÃ¶rige Lehrer wurde nicht gefunden.");
  }

  assertImageUpload(payload.fileName, payload.fileDataUrl);

  const savedFile = await saveUploadFile({
    fileName: payload.fileName,
    fileDataUrl: payload.fileDataUrl,
    teacherCode: teacher.code,
    subject: upload.subject,
    classLevel: upload.classLevel,
    year: upload.year
  });

  upload.fileName = String(payload.fileName || "").trim();
  upload.filePath = savedFile.path;
  upload.previewUrl = savedFile.previewUrl;
}

function assertImageUpload(fileName, fileDataUrl) {
  const parsed = parseDataUrl(fileDataUrl);
  if (!String(parsed.mimeType || "").toLowerCase().startsWith("image/")) {
    throw createError("Zur Prüfung sind nur Bilddateien erlaubt.");
  }

  const lowerName = String(fileName || "").toLowerCase();
  const hasImageExtension = [".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg", ".bmp", ".avif", ".tif", ".tiff", ".heic", ".heif"]
    .some((ending) => lowerName.endsWith(ending));

  if (!hasImageExtension) {
    throw createError("Zur Prüfung sind nur Bilddateien erlaubt.");
  }
}

function parseDataUrl(dataUrl) {
  const match = /^data:(.*?);base64,(.*)$/.exec(String(dataUrl || ""));
  if (!match) {
    throw createError("Ungültige Dateidaten.");
  }

  return {
    mimeType: match[1],
    base64Content: match[2]
  };
}

function createError(message, statusCode = 400) {
  const error = new Error(message);
  error.statusCode = statusCode;
  return error;
}
