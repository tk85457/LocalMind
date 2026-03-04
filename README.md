<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Platform" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" />
  <img src="https://img.shields.io/badge/LLM-llama.cpp-FF6F00?style=for-the-badge" alt="llama.cpp" />
  <img src="https://img.shields.io/badge/Min%20SDK-26-brightgreen?style=for-the-badge" alt="Min SDK" />
  <img src="https://img.shields.io/badge/License-MIT-blue?style=for-the-badge" alt="License" />
</p>

<h1 align="center">🧠 LocalMind</h1>

<p align="center">
  <strong>Run powerful AI models entirely on your Android device — no cloud, no API keys, no subscriptions.</strong>
</p>

<p align="center">
  <em>Private. Fast. Offline. Your AI, your data, your device.</em>
</p>

---

## ✨ Features

| Feature | Description |
|---------|-------------|
| 🔒 **100% On-Device** | All AI inference runs locally — your data never leaves your phone |
| 💬 **Chat Interface** | Beautiful Material 3 chat UI with Markdown rendering & syntax highlighting |
| 📥 **Model Downloads** | Browse and download GGUF models directly from Hugging Face |
| ⚡ **Real-Time Streaming** | Token-by-token streaming with optimized TTFT (Time To First Token) |
| 🎨 **Dark & Light Themes** | Full Material You dynamic theming with smooth transitions |
| 📁 **RAG / Document Chat** | Upload PDFs and documents for context-aware conversations |
| 🔧 **Advanced Settings** | Fine-tune temperature, top-p, top-k, repeat penalty, stop words & more |
| 📂 **Collections** | Organize chats into collections for easy management |
| 🌐 **Multi-Language** | Full localization support with multiple language translations |
| 🔐 **Biometric Lock** | Secure your conversations with biometric authentication |
| 📊 **Performance Stats** | Live tokens/second, TTFT, and GPU layer monitoring |

---

## 🏗️ Architecture

```
com.localmind.app/
├── core/                  # Foundation layer
│   ├── di/                # Hilt dependency injection modules
│   ├── engine/            # LLM engine orchestration
│   ├── performance/       # Performance monitoring & optimization
│   ├── rollout/           # Feature flags & staged rollout
│   ├── storage/           # File & model storage management
│   └── utils/             # Shared utilities
├── data/                  # Data layer
│   ├── local/             # Room database DAOs & entities
│   ├── mapper/            # Data ↔ Domain model mappers
│   ├── remote/            # Hugging Face API integration
│   └── repository/        # Repository implementations
├── domain/                # Domain layer
│   ├── model/             # Domain models (Chat, Message, Model)
│   └── usecase/           # Business logic use cases
├── llm/                   # LLM integration layer
│   ├── native/            # JNI bridge to llama.cpp
│   ├── nativelib/         # Native library loader
│   └── prompt/            # Prompt template engine
├── navigation/            # Compose Navigation graph
├── receiver/              # Broadcast receivers
├── service/               # Background services
├── ui/                    # Presentation layer
│   ├── components/        # Reusable Compose components
│   ├── screens/           # App screens (Chat, Settings, Models)
│   ├── theme/             # Material 3 theme & design tokens
│   ├── utils/             # UI utilities
│   └── viewmodel/         # ViewModels with StateFlow
└── worker/                # WorkManager background tasks
```

---

## 🛠️ Tech Stack

| Category | Technology |
|----------|-----------|
| **Language** | Kotlin 1.9.x |
| **UI Framework** | Jetpack Compose with Material 3 |
| **Architecture** | Clean Architecture (MVVM + Use Cases) |
| **DI** | Hilt (Dagger) |
| **Database** | Room with KSP |
| **Networking** | Retrofit + OkHttp |
| **AI Engine** | llama.cpp via JNI/NDK (C++) |
| **Image Loading** | Coil |
| **Animations** | Lottie Compose |
| **Markdown** | Markwon + Prism4j syntax highlighting |
| **PDF Parsing** | PdfBox Android |
| **Background Work** | WorkManager |
| **Build System** | Gradle (Kotlin DSL) + CMake |

---

## 🚀 Getting Started

### Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or later
- **JDK 17+**
- **Android SDK 34** with **NDK 26.1.10909125**
- **CMake 3.22.1+**

### Build & Run

```bash
# Clone the repository
git clone https://github.com/YOUR_USERNAME/LocalMind.git
cd LocalMind

# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

### llama.cpp Setup

The native AI engine requires llama.cpp as a git submodule:

```bash
cd app/src/main/cpp
git submodule add https://github.com/ggerganov/llama.cpp llama.cpp
git submodule update --init --recursive
```

> See [BUILD.md](BUILD.md) for detailed build instructions and troubleshooting.

---

## 📱 Supported Devices

- **Minimum**: Android 8.0 (API 26)
- **Target**: Android 14 (API 34)
- **ABI**: `arm64-v8a` (64-bit ARM devices)
- **Recommended RAM**: 6GB+ for 7B models, 4GB+ for smaller models

---

## 📄 License

This project is licensed under the **MIT License** — see the [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) file for attribution of third-party components.

---

## 🙏 Acknowledgments

- [llama.cpp](https://github.com/ggerganov/llama.cpp) — Inference of Meta's LLaMA model in pure C/C++
- [PocketPal AI](https://github.com/a-ghorbani/pocketpal-ai) — Behavior-inspired patterns for model management
- [Hugging Face](https://huggingface.co/) — Model hosting and discovery

---

<p align="center">
  <strong>Built with ❤️ for on-device AI</strong>
</p>
