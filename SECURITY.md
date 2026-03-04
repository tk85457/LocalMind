# 🔐 Security Policy

## Supported Versions

| Version | Supported          |
|---------|--------------------|
| 1.0.x   | ✅ Active support  |
| < 1.0   | ❌ Not supported   |

## Reporting a Vulnerability

We take the security of LocalMind seriously. If you discover a security vulnerability, please follow responsible disclosure:

### 🚨 Do NOT

- Open a public GitHub issue for security vulnerabilities
- Share vulnerability details publicly before a fix is released

### ✅ Do

1. **Report privately** via [GitHub Security Advisories](https://github.com/tk85457/LocalMind/security/advisories/new)
2. Include a detailed description of the vulnerability
3. Provide steps to reproduce (if applicable)
4. Suggest a fix (if possible)

### Response Timeline

| Action | Timeframe |
|--------|-----------|
| Acknowledgment | Within 48 hours |
| Initial assessment | Within 1 week |
| Fix & release | Within 30 days (critical: ASAP) |

## Security Architecture

LocalMind is designed with privacy and security as core principles:

- **Zero network requests** during AI inference — all processing is on-device
- **No telemetry or analytics** — we do not collect any user data
- **Biometric authentication** — optional device-level lock
- **Local Room database** — all data encrypted and stored locally
- **No cloud dependencies** — the app works fully offline

## Scope

The following are **in scope** for security reports:

- Data leakage from the app
- Unauthorized access to stored conversations
- JNI/native code vulnerabilities
- Insecure data storage
- Permission escalation

The following are **out of scope**:

- Vulnerabilities in third-party GGUF models
- Physical device access attacks
- Social engineering
- Denial of service on local device

---

Thank you for helping keep LocalMind and its users safe! 🛡️
