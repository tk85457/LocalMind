# 📋 Changelog

All notable changes to LocalMind will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] — 2026-03-04

### 🎉 Initial Release

#### Added
- **On-Device LLM Inference** — Full llama.cpp integration via JNI/NDK for 100% offline AI
- **Chat Interface** — Material 3 Compose UI with Markdown rendering and syntax highlighting
- **Model Management** — Browse, download, and manage GGUF models from Hugging Face
- **Real-Time Streaming** — Token-by-token generation with live performance metrics
- **RAG / Document Chat** — PDF upload and context-aware conversations
- **Collections** — Organize conversations into custom folders
- **Advanced Settings** — Temperature, top-p, top-k, repeat penalty, stop words, BOS/EOS tokens
- **Performance Monitor** — Live tokens/second, TTFT, and GPU layer tracking
- **Dark & Light Themes** — Material You dynamic theming with smooth transitions
- **Biometric Lock** — Fingerprint and face unlock support
- **Multi-Language Support** — Full i18n/l10n localization framework
- **Lottie Animations** — Premium micro-animations throughout the UI
- **Background Downloads** — WorkManager-powered model downloads with progress tracking

#### Technical
- Clean Architecture (MVVM + Use Cases)
- Hilt dependency injection
- Room database with KSP
- DataStore preferences
- Retrofit + OkHttp for Hugging Face API
- NDK 26 + CMake 3.22 for native builds
- arm64-v8a architecture support

---

[1.0.0]: https://github.com/tk85457/LocalMind/releases/tag/v1.0.0
