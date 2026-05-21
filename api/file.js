const { getRepositoryBinaryFile } = require("./_lib/github-store");

module.exports = async function handler(request, response) {
  if (request.method !== "GET") {
    response.status(405).json({ error: "Nur GET ist erlaubt." });
    return;
  }

  const path = String(request.query?.path || "").trim();
  if (!path) {
    response.status(400).json({ error: "Dateipfad fehlt." });
    return;
  }

  try {
    const file = await getRepositoryBinaryFile(path);
    if (!file) {
      response.status(404).json({ error: "Datei wurde nicht gefunden." });
      return;
    }

    response.setHeader("Content-Type", getMimeType(file.fileName));
    response.setHeader("Cache-Control", "public, max-age=3600");
    response.setHeader("Content-Disposition", `inline; filename="${sanitizeHeaderFileName(file.fileName)}"`);
    response.status(200).send(file.content);
  } catch (error) {
    response.status(500).json({ error: error.message || "Datei konnte nicht geladen werden." });
  }
};

function getMimeType(fileName) {
  const lowerName = String(fileName || "").toLowerCase();
  if (lowerName.endsWith(".png")) return "image/png";
  if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg")) return "image/jpeg";
  if (lowerName.endsWith(".gif")) return "image/gif";
  if (lowerName.endsWith(".webp")) return "image/webp";
  if (lowerName.endsWith(".svg")) return "image/svg+xml";
  if (lowerName.endsWith(".bmp")) return "image/bmp";
  if (lowerName.endsWith(".avif")) return "image/avif";
  if (lowerName.endsWith(".tif") || lowerName.endsWith(".tiff")) return "image/tiff";
  if (lowerName.endsWith(".heic")) return "image/heic";
  if (lowerName.endsWith(".heif")) return "image/heif";
  return "application/octet-stream";
}

function sanitizeHeaderFileName(fileName) {
  return String(fileName || "datei").replace(/["\r\n]/g, "_");
}
