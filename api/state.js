const { readState } = require("./_lib/github-store");

module.exports = async function handler(request, response) {
  if (request.method !== "GET") {
    response.status(405).json({ error: "Nur GET ist erlaubt." });
    return;
  }

  try {
    const { state } = await readState();
    response.status(200).json(state);
  } catch (error) {
    response.status(500).json({ error: error.message || "Status konnte nicht geladen werden." });
  }
};
