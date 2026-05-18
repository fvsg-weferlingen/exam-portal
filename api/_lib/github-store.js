const DEFAULT_STATE = {
  teachers: [
    {
      id: "teacher-default-wag",
      name: "Herr Wagner",
      code: "WAG",
      subjects: ["Mathematik", "Physik"]
    }
  ],
  pendingUploads: [],
  approvedUploads: []
};

const STATE_PATH = "data/state.json";

function getConfig() {
  const {
    GITHUB_TOKEN,
    GITHUB_OWNER,
    GITHUB_REPO,
    GITHUB_BRANCH
  } = process.env;

  if (!GITHUB_TOKEN || !GITHUB_OWNER || !GITHUB_REPO || !GITHUB_BRANCH) {
    throw new Error("GitHub-Umgebungsvariablen fehlen. Benötigt werden GITHUB_TOKEN, GITHUB_OWNER, GITHUB_REPO und GITHUB_BRANCH.");
  }

  return {
    token: GITHUB_TOKEN,
    owner: GITHUB_OWNER,
    repo: GITHUB_REPO,
    branch: GITHUB_BRANCH
  };
}

async function githubRequest(path, options = {}) {
  const config = getConfig();
  const url = `https://api.github.com/repos/${config.owner}/${config.repo}${path}`;
  const response = await fetch(url, {
    ...options,
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${config.token}`,
      "User-Agent": "exam-portal-server",
      ...(options.headers || {})
    }
  });

  if (response.status === 204) {
    return null;
  }

  const body = await response.json();
  if (!response.ok) {
    const error = new Error(body.message || "GitHub-Anfrage fehlgeschlagen.");
    error.status = response.status;
    throw error;
  }

  return body;
}

async function getRepositoryFile(path) {
  const config = getConfig();

  try {
    const file = await githubRequest(`/contents/${encodePath(path)}?ref=${encodeURIComponent(config.branch)}`);
    return {
      sha: file.sha,
      content: Buffer.from(file.content, "base64").toString("utf8")
    };
  } catch (error) {
    if (error.status === 404) {
      return null;
    }
    throw error;
  }
}

async function putRepositoryFile(path, contentBuffer, message, sha) {
  const config = getConfig();
  const payload = {
    message,
    content: contentBuffer.toString("base64"),
    branch: config.branch
  };

  if (sha) {
    payload.sha = sha;
  }

  return githubRequest(`/contents/${encodePath(path)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload)
  });
}

async function ensureStateFile() {
  const existing = await getRepositoryFile(STATE_PATH);
  if (existing) {
    return existing;
  }

  const content = Buffer.from(JSON.stringify(DEFAULT_STATE, null, 2), "utf8");
  const result = await putRepositoryFile(STATE_PATH, content, "Initialize exam portal state");

  return {
    sha: result.content.sha,
    content: JSON.stringify(DEFAULT_STATE)
  };
}

async function readState() {
  const file = await ensureStateFile();
  return {
    sha: file.sha,
    state: JSON.parse(file.content)
  };
}

async function writeState(state, message, sha) {
  const buffer = Buffer.from(JSON.stringify(state, null, 2), "utf8");
  const result = await putRepositoryFile(STATE_PATH, buffer, message, sha);
  return {
    sha: result.content.sha,
    state
  };
}

async function mutateState(message, mutator) {
  const { sha, state } = await readState();
  const draft = structuredClone(state);
  const result = await mutator(draft);
  const written = await writeState(draft, message, sha);

  return {
    state: written.state,
    result
  };
}

async function saveUploadFile({ fileName, fileDataUrl, teacherCode, subject, classLevel, year }) {
  const parsed = parseDataUrl(fileDataUrl);
  const extension = getFileExtension(fileName);
  const safeName = slugify(stripExtension(fileName)) || "upload";
  const safeSubject = slugify(subject) || "fach";
  const safeTeacherCode = slugify(teacherCode) || "teacher";
  const safeClass = slugify(`klasse-${classLevel}`);
  const timePart = new Date().toISOString().replaceAll(":", "-");
  const uploadPath = `uploads/${year}/${safeTeacherCode}/${safeSubject}/${timePart}-${safeName}${extension}`;

  await putRepositoryFile(
    uploadPath,
    Buffer.from(parsed.base64Content, "base64"),
    `Add upload ${fileName}`
  );

  return {
    path: uploadPath,
    previewUrl: buildRawUrl(uploadPath)
  };
}

function parseDataUrl(dataUrl) {
  const match = /^data:(.*?);base64,(.*)$/.exec(dataUrl || "");
  if (!match) {
    throw new Error("Ungültige Dateidaten.");
  }

  return {
    mimeType: match[1],
    base64Content: match[2]
  };
}

function buildRawUrl(path) {
  const config = getConfig();
  return `https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/${encodeURI(path)}`;
}

function encodePath(path) {
  return path
    .split("/")
    .map((segment) => encodeURIComponent(segment))
    .join("/");
}

function slugify(value) {
  return String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
}

function stripExtension(fileName) {
  return String(fileName || "").replace(/\.[^.]+$/, "");
}

function getFileExtension(fileName) {
  const match = /\.([^.]+)$/.exec(String(fileName || ""));
  return match ? `.${match[1].toLowerCase()}` : "";
}

module.exports = {
  DEFAULT_STATE,
  mutateState,
  readState,
  saveUploadFile
};
