# 🤝 Contributing to LocalMind

Thank you for your interest in contributing to **LocalMind**! Every contribution makes on-device AI better for everyone.

---

## 📋 Table of Contents

- [Code of Conduct](#-code-of-conduct)
- [How Can I Contribute?](#-how-can-i-contribute)
- [Development Setup](#-development-setup)
- [Coding Guidelines](#-coding-guidelines)
- [Commit Convention](#-commit-convention)
- [Pull Request Process](#-pull-request-process)

---

## 📜 Code of Conduct

This project adheres to a [Code of Conduct](CODE_OF_CONDUCT.md). By participating, you are expected to uphold this code. Please report unacceptable behavior via [GitHub Issues](https://github.com/tk85457/LocalMind/issues).

---

## 🚀 How Can I Contribute?

### 🐛 Reporting Bugs

- Use the [Bug Report](https://github.com/tk85457/LocalMind/issues/new?template=bug_report.md) template
- Include device info, Android version, and model being used
- Attach logcat output if possible

### 💡 Suggesting Features

- Use the [Feature Request](https://github.com/tk85457/LocalMind/issues/new?template=feature_request.md) template
- Describe the use case and expected behavior
- Check existing issues to avoid duplicates

### 🔧 Code Contributions

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes following the [Coding Guidelines](#-coding-guidelines)
4. Write/update tests as needed
5. Submit a Pull Request

### 🌐 Translations

- Add new language files in `app/src/main/res/values-<locale>/strings.xml`
- Use the `translate_strings.py` script as a starting point
- Ensure all strings are translated — don't leave English fallbacks

### 📖 Documentation

- Improve README, BUILD.md, or inline code documentation
- Add usage examples or tutorials

---

## 🛠️ Development Setup

### Prerequisites

| Tool | Version |
|------|---------|
| Android Studio | Hedgehog 2023.1.1+ |
| JDK | 17+ |
| NDK | 26.1.10909125 |
| CMake | 3.22.1+ |

### Steps

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/LocalMind.git
cd LocalMind

# Initialize submodules
git submodule update --init --recursive

# Open in Android Studio and sync Gradle
# Build: ./gradlew assembleDebug
```

---

## 📐 Coding Guidelines

### Kotlin

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use `StateFlow` over `LiveData` for new ViewModels
- Prefer `sealed class` / `sealed interface` for UI states
- Use Hilt `@Inject` for dependency injection — no manual instantiation
- Write pure functions in the `domain` layer — no Android framework dependencies

### Compose

- Extract reusable composables into `ui/components/`
- Use `Material3` components and theming
- Avoid hardcoded colors/dimensions — use theme tokens
- Add `@Preview` for all new composable functions

### Native (C++)

- Follow the existing JNI bridge pattern in `jni_bridge.cpp`
- Use `LOGE` / `LOGI` macros for logging
- Manage native memory carefully — always pair `new` with `delete`

---

## 📝 Commit Convention

We follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <short summary>

<optional body>

<optional footer>
```

### Types

| Type | Description |
|------|------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (formatting, no logic change) |
| `refactor` | Code refactoring |
| `perf` | Performance improvement |
| `test` | Adding/updating tests |
| `build` | Build system or dependencies |
| `ci` | CI/CD configuration |
| `chore` | Maintenance tasks |

### Examples

```
feat(chat): add markdown code block syntax highlighting
fix(engine): resolve TTFT regression on Snapdragon 8 Gen 3
docs(readme): update build instructions for NDK 26
perf(inference): optimize token sampling with top-k pruning
```

---

## 🔀 Pull Request Process

1. **Branch from `main`** — Use descriptive branch names: `feat/voice-input`, `fix/download-crash`
2. **Keep PRs focused** — One feature or fix per PR
3. **Update documentation** — If your change affects usage, update the docs
4. **Test thoroughly** — Verify on a physical device if possible
5. **Fill out the PR template** — Describe what and why
6. **Request review** — Tag maintainers for review

### PR Checklist

- [ ] Code follows the project's coding guidelines
- [ ] Self-reviewed the code changes
- [ ] Added/updated relevant documentation
- [ ] Tested on a physical Android device
- [ ] No new warnings or lint errors
- [ ] Commit messages follow conventional commits

---

## 🏷️ Issue Labels

| Label | Description |
|-------|------------|
| `bug` | Something isn't working |
| `enhancement` | New feature request |
| `documentation` | Documentation improvements |
| `good first issue` | Good for newcomers |
| `help wanted` | Community help appreciated |
| `performance` | Performance-related |
| `ui/ux` | User interface improvements |
| `native` | C++/JNI/llama.cpp related |

---

<p align="center">
  <strong>Thank you for helping make LocalMind better! 🧠❤️</strong>
</p>
