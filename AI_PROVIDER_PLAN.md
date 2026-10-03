# AI provider architecture

EmreView AI uses a provider-neutral contract. Providers expose model discovery and multimodal image analysis. OpenAI-compatible providers can be configured with a base URL, API key, and selected vision model; this is compatible in principle with OpenAI-compatible gateways and hosted inference services.

The next integration step is wiring the existing UI/ViewModel to persist these three settings and replacing the Gemini-only tagging call with the provider interface.