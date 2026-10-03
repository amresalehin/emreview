# emreview

Android photo gallery and review app with provider-neutral multimodal AI.

The app works locally without an AI provider. For AI analysis, configure any OpenAI-compatible vision endpoint with:
- Base URL
- API key
- Vision model ID discovered from the provider's `GET /models` endpoint

Gemini is optional; the application contains no compile-time Gemini API key requirement.

The existing gallery, labels, trash, vault, editor, and Google Drive functionality remain part of the app.
