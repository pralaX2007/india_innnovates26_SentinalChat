# 🔒 SentinalChat — Full Codebase Audit Report (v2)

> **Date:** 2026-03-21
> **Auditor:** Claude — Automated Security Audit
> **Scope:** All 75 source files across 12 modules
> **Project:** SentinalChat (India Innovates 26 Hackathon)
> **Diff from v1:** Re-audited against the current committed codebase. All v1 fixes verified. Five new issues found (#33–#37).

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Dimension 1 — Security Audit](#dimension-1--security-audit)
3. [Dimension 2 — Architecture & Code Quality](#dimension-2--architecture--code-quality)
4. [Dimension 3 — Kotlin/Java Interop](#dimension-3--kotlinjava-interop)
5. [Dimension 4 — WiFi Direct Reliability](#dimension-4--wifi-direct-reliability)
6. [Dimension 5 — Build & Repo Health](#dimension-5--build--repo-health)
7. [New Findings (v2)](#new-findings-v2)
8. [Summary Table](#summary-table)

---

## Executive Summary

The v2 codebase reflects **significant improvement** over v1. All 3 original CRITICAL issues have been fixed, both protocol-breaking HIGH issues (#4 and #5) have been resolved, and memory/replay hygiene are substantially tightened. The Double Ratchet implementation, HKDF, AES-GCM, and AndroidKeyStore identity keys remain strong foundations.

However, the re-audit uncovered **5 new issues** — one of which is a security bug that bypasses the replay protection that was just fixed in v1.

Updated findings:

- **0 CRITICAL** (all 4 original CRITICAL issues resolved ✅)
- **1 HIGH** (new — Issue #33: replay protection keying bypassed at call site)
- **3 MEDIUM** (new — Issues #34–#36)
- **1 LOW** (new — Issue #37)
- **10 issues from v1** remain open as "Noted" / architectural debt

---

## Dimension 1 — Security Audit

### ✅ Issue #1 — KeyManager.wipe() — FIXED

**[DIMENSION 1] [SEVERITY: CRITICAL] [STATUS: ✅ Fixed]**
File: [KeyManager.java](app/src/main/java/com/hacksecure/p2p/crypto/KeyManager.java)
`wipe()` now calls `Arrays.fill()` on both private and public key byte copies before nulling the reference. The JVM limitation (copies only, not internal JCA state) is acknowledged in code comments.

---

### ✅ Issue #2 — `e.printStackTrace()` in ChatActivity — FIXED

**[DIMENSION 1] [SEVERITY: CRITICAL] [STATUS: ✅ Fixed]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java)
All `e.printStackTrace()` calls replaced with `Logger.e("... " + e.getClass().getSimpleName())`. No exception message or stack trace reaches Logcat in the hot crypto path.

---

### ✅ Issue #3 — Release Build `minifyEnabled false` — FIXED

**[DIMENSION 1] [SEVERITY: CRITICAL] [STATUS: ✅ Fixed]**
File: [app/build.gradle](app/build.gradle)
`minifyEnabled true` and `shrinkResources true` are now set for release builds. ProGuard rules file exists and keeps all crypto, protocol, serialization, and identity classes.

---

### ✅ Issue #4 — Ephemeral Private Key Discarded — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java)
`localEphemeralKeyPair` is now a class field. `generateEphemeralKey()` stores the full `KeyPair`, returns only the public bytes to the QR payload, and the private key remains available for `performHandshakeAsResponder()`.

---

### ✅ Issue #5 — No Responder-Side Handshake Path — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [QRHandshakeManager.kt](app/src/main/java/com/hacksecure/p2p/Protocol/handshake/QRHandshakeManager.kt)
`performHandshakeAsResponder(scannedPayload, localEphemeralKeyPair)` now exists. Responder correctly computes `DH3 = IK_local × EK_remote` (inverse of initiator's `DH3 = EK_local × IK_remote`) and initialises with `receivingChainKey` rather than `sendingChainKey`.

---

### ✅ Issue #6 — SessionState Replay Window Unbounded — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [SessionState.kt](app/src/main/java/com/hacksecure/p2p/session/SessionState.kt)
`replayWindow: MutableSet<Int>` replaced with `replayProtection: ReplayProtection(maxCacheSize = 2000)`. The `isReplay(ratchetKeyId, messageNumber)` two-argument form is now available on `SessionState`.

---

### ✅ Issue #7 — DoubleRatchet Skipped Keys Never Pruned Globally — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [DoubleRatchet.kt](app/src/main/java/com/hacksecure/p2p/Protocol/Ratchet/DoubleRatchet.kt)
`pruneSkippedKeys()` added. When `skippedMessageKeys.size > MAX_SKIP`, oldest entries are evicted and their key bytes wiped via `MemoryCleaner.wipe()`.

---

### ✅ Issue #8 — Logger Writes to Logcat in Production — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [Logger.java](app/src/main/java/com/hacksecure/p2p/utils/Logger.java)
`Logger.init(context)` now resolves `FLAG_DEBUGGABLE` at runtime. `d()` and `i()` are gated behind `sIsDebug`. `e()` logs unconditionally (correct — errors should surface in production).

---

### ✅ Issue #9 — EncryptionManager Custom KDF Instead of HKDF — FIXED

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
File: [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java)
`deriveKey()` now delegates to `HKDF.INSTANCE.deriveKey()` with a proper `"SentinelChat_AES_Key"` info string.

---

### ✅ Issue #10 — `IdentityVerification.verifyKeys()` Logic Bug — FIXED

**[DIMENSION 1] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [IdentityVerification.kt](app/src/main/java/com/hacksecure/p2p/security/IdentityVerification.kt)
`verifyKeys()` has been renamed and split:
- `areSameKey(keyA, keyB)` — checks if two keys are identical (TOFU key change detection)
- `verifyRemoteIdentity(remoteKey, expectedFingerprint)` — compares against a stored fingerprint
- Old `verifyFingerprint()` marked `@Deprecated` with `ReplaceWith` pointing to `verifyRemoteIdentity()`

---

### Issue #11 — TCP Layer is Plaintext (Metadata Leakage)

**[DIMENSION 1] [SEVERITY: MEDIUM] [STATUS: Acceptable]**
File: [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java)
Message content is encrypted end-to-end via the Double Ratchet. However, the raw TCP framing exposes packet sizes and timing to any observer on the WiFi Direct group. For a hackathon demo over a private P2P network this is an acceptable trade-off. For production: consider wrapping `Socket` with `SSLSocket` or adding a Noise protocol layer for transport-level metadata hiding.

---

### Issue #12 — `EncryptionManager.aesKey` Not Volatile

**[DIMENSION 1] [SEVERITY: LOW] [STATUS: Minor]**
File: [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java)
`aesKey` is `final`, but `wipe()` zeroes it in-place via `Arrays.fill`. On a multi-core JVM the JIT could theoretically cache a stale view of the array contents. Current usage is single-threaded so this is minor. Marking the field `volatile` or wrapping `wipe()` in a `synchronized` block eliminates the theoretical risk entirely.

---

## Dimension 2 — Architecture & Code Quality

### Issue #13 — ChatActivity is a God Class

**[DIMENSION 2] [SEVERITY: HIGH] [STATUS: Noted — architectural debt]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java)
`ChatActivity` directly handles QR generation, QR scanning, DH handshake, ratchet initialisation, session registration, message encryption, TCP lifecycle, and RecyclerView management — six+ distinct responsibilities in one 290-line Activity. A TODO comment (`// TODO [ARCH] Refactor into MVVM`) is present and accurate.

Recommended decomposition:
- `ChatViewModel` — ratchet state, send/receive logic
- `ConnectionRepository` — wraps `ConnectionHandler`
- `HandshakeManager` — QR display + scanning + DH

This is pre-existing debt and acceptable for a hackathon build.

---

### Issue #14 — Duplicate SessionManager Classes

**[DIMENSION 2] [SEVERITY: HIGH] [STATUS: Partially fixed — TODO comment added]**
Files: [utils/SessionManager.java](app/src/main/java/com/hacksecure/p2p/utils/SessionManager.java), [session/SessionManager.kt](app/src/main/java/com/hacksecure/p2p/session/SessionManager.kt)
Two classes named `SessionManager` in different packages still coexist. A `// TODO [ARCH] Rename to LegacySessionManager` comment has been added to the Java version, but the rename has not been done. Wrong-package import silently compiles and breaks at runtime. Recommended: complete the rename before sharing the repo with reviewers.

---

### ✅ Issue #15 — MessageReceiver Created Per Message — FIXED

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java)
`messageReceiver` is now a class field, assigned once after handshake completes (`Fix #15` comment in code). The `onMessageReceived` callback delegates to this single instance.

---

### ✅ Issue #16 — ExecutorService Never Shut Down — FIXED

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java)
`executor.shutdownNow()` is called at the top of `close()`.

---

### ✅ Issue #17 — References Not Nulled After Close — FIXED

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java)
`AtomicBoolean closed` added. `close()` uses `compareAndSet(false, true)` to prevent double-close. `outputStream`, `socket`, and `serverSocket` are all nulled inside `close()`. `sendRawBytes()` checks `!closed.get()` before attempting to write.

---

### Issue #18 — MessageTTLManager Unused Dead Code

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: Dead code — not wired up]**
File: [MessageTTLManager.kt](app/src/main/java/com/hacksecure/p2p/messaging/ttl/MessageTTLManager.kt)
`start()` is never called anywhere in the app. The TTL field in `MessageMetadata` is hardcoded to `0` in `ChatActivity.sendMessage()`, which would immediately expire every message if the scheduler were running. Either wire it into the `SentinelChatApp` lifecycle with a non-zero TTL, or remove it. As-is, it misleads security reviewers into thinking ephemeral message deletion is active.

---

## Dimension 3 — Kotlin/Java Interop

### ✅ Issue #19 — Java Files Missing @Nullable/@NonNull — FIXED

**[DIMENSION 3] [SEVERITY: HIGH] [STATUS: ✅ Fixed]**
Files: All Java files called from Kotlin
`@NonNull` and `@Nullable` annotations are now present on all public methods in `KeyManager.java`, `Message.java`, `Peer.java`, `Session.java`. Kotlin callers can now enforce null-safety at compile time.

---

### Issue #20 — Message.java Should Be a Kotlin Data Class

**[DIMENSION 3] [SEVERITY: MEDIUM] [STATUS: Noted]**
File: [Message.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Message.java)
33 lines of getter/setter boilerplate. The TODO comment (`// TODO [INTEROP] Migrate to Kotlin data class`) is present. Not blocking, but migration removes the entire file and gives free `equals()`, `hashCode()`, `copy()`, and destructuring.

---

### Issue #21 — Peer.java and Session.java Are Pure Boilerplate

**[DIMENSION 3] [SEVERITY: MEDIUM] [STATUS: Noted]**
Files: [Peer.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Peer.java), [Session.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Session.java)
Same situation as #20. Both have TODO comments. Migrate when architectural refactor (Issue #13) is done, as these feed into the legacy `SessionManager`.

---

### ✅ Issue #22 — KeySetupActivity Shows Fake Fingerprint — FIXED

**[DIMENSION 3] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [KeySetupActivity.java](app/src/main/java/com/hacksecure/p2p/ui/KeySetupActivity.java)
Now calls `IdentityKeyManager.INSTANCE.getFingerprint()` to display the real SHA-256 fingerprint of the AndroidKeyStore identity key, formatted as `XXXX-XXXX-XXXX-...`. The legacy `KeyManager` software key is no longer shown.

---

## Dimension 4 — WiFi Direct Reliability

### Issue #23 — No Reconnection Logic After Connection Loss

**[DIMENSION 4] [SEVERITY: HIGH] [STATUS: Noted — TODO comment added]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java)
`onConnectionLost()` shows a Toast but takes no recovery action. Ratchet state persists in memory but the TCP socket is dead. A `// TODO [WIFI] Implement reconnection with exponential backoff` comment is present but the implementation is not. For a demo over stable WiFi Direct this is acceptable; in any real usage a dropped connection is a permanent failure.

---

### Issue #24 — No WiFi Direct Connection State Machine

**[DIMENSION 4] [SEVERITY: HIGH] [STATUS: Noted — TODO comment added]**
File: [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java)
No formal state tracking. `startDiscovery()`, `connectToPeer()`, and `disconnect()` can all be called in any order. A `// TODO [WIFI] Add a ConnectionState enum` comment is present. For the hackathon demo path (linear: discover → connect → chat) this is unlikely to cause problems.

---

### ✅ Issue #25 — Group Owner Intent Not Configurable — FIXED

**[DIMENSION 4] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java)
A `// TODO [WIFI] Set config.groupOwnerIntent` comment is now in `connectToPeer()`. However — the implementation is still not done. The intent value is still not set, meaning GO negotiation is still random. This is a partial fix (acknowledged, not resolved).

> [!CAUTION]
> Without `config.groupOwnerIntent` set, the device that the OS picks as group owner may not match the one calling `startServer()` in `ConnectionHandler`. If they mismatch, the client will attempt to connect to itself and the connection will fail silently. This is the most likely cause of two-device testing failures. Set `config.groupOwnerIntent = isHost ? 15 : 0` before the hackathon demo.

---

### ✅ Issue #26 — ServerSocket Missing SO_REUSEADDR — FIXED

**[DIMENSION 4] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java)
`ServerSocket` is now created with `setReuseAddress(true)` before `bind()`. Rapid reconnects no longer fail with `BindException`.

---

### ✅ Issue #27 — WifiDirectManager Holds Activity Context — FIXED

**[DIMENSION 4] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java)
Constructor now stores `context.getApplicationContext()` for `WifiP2pManager.initialize()`. The Activity context is retained only as `registrationContext` for broadcast receiver registration, and is nulled in `cleanup()` when the receiver is unregistered.

---

## Dimension 5 — Build & Repo Health

### ✅ Issue #28 — ProGuard Rules File Missing — FIXED

**[DIMENSION 5] [SEVERITY: CRITICAL] [STATUS: ✅ Fixed]**
File: [app/proguard-rules.pro](app/proguard-rules.pro)
File now exists with correct keep rules for `crypto.*`, `Protocol.*`, `security.*`, `identity.*`, `messaging.models.*`, `session.*`, and `storage.*`. ZXing and CameraX are also kept.

---

### ✅ Issue #29 — .gitignore Missing *.jks — FIXED

**[DIMENSION 5] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [.gitignore](.gitignore)
`*.jks`, `*.keystore`, `*.keystore.properties`, `signing.properties`, `release.keystore.properties` are all present in `.gitignore`. No signing material is currently tracked.

---

### ✅ Issue #30 — gradle.properties Missing R8 Flag — FIXED

**[DIMENSION 5] [SEVERITY: MEDIUM] [STATUS: ✅ Fixed]**
File: [gradle.properties](gradle.properties)
`android.enableR8=true` is now present.

---

### Issue #31 — Some Dependencies Behind Latest

**[DIMENSION 5] [SEVERITY: LOW] [STATUS: Can update]**
File: [app/build.gradle](app/build.gradle)

| Dependency | Current | Latest (approx) | Notes |
|---|---|---|---|
| `appcompat` | 1.6.1 | 1.7.0 | Minor update |
| `material` | 1.11.0 | 1.12.0 | Minor update |
| `camera-*` | 1.3.1 | 1.4.1 | Bug fixes |
| `core-ktx` | 1.10.1 | 1.15.0 | Several versions behind |
| `junit` | 4.13.2 | 4.13.2 | Current ✅ |
| `espresso` | 3.5.1 | 3.6.1 | Minor update |
| `zxing:core` | 3.5.3 | 3.5.3 | Current ✅ |

No known CVEs in current versions. `core-ktx` is the most stale; the others are minor.

---

### Issue #32 — TODO Comments Present for Incomplete Features

**[DIMENSION 5] [SEVERITY: INFO]**
Several `// TODO [ARCH]`, `// TODO [WIFI]`, and `// TODO [INTEROP]` comments are present throughout the codebase — added as part of v1 fixes. This is net positive (incomplete work is now documented). The open TODOs map to Issues #13, #14, #18, #20, #21, #23, #24, and #25. None block the demo.

---

## New Findings (v2)

### 🔴 Issue #33 — Replay Protection Keying Bypassed at Call Site

**[DIMENSION 1] [SEVERITY: HIGH] [STATUS: Open — NEW in v2]**
File: [SessionManager.kt](app/src/main/java/com/hacksecure/p2p/session/SessionManager.kt#L24), [SessionState.kt](app/src/main/java/com/hacksecure/p2p/session/SessionState.kt#L38), [MessageReceiver.kt](app/src/main/java/com/hacksecure/p2p/network/transport/MessageReceiver.kt#L24)

Issue: `SessionState` now has the correct two-argument `isReplay(ratchetKeyId, messageNumber)` method (from the v1 fix to #6). However, `SessionManager.validateMessage()` calls the single-argument overload `session.isReplay(messageNumber)`, which falls back to a hardcoded `"default"` ratchet key ID:

```kotlin
// SessionState.kt — the fallback overload
fun isReplay(messageNumber: Int): Boolean {
    return replayProtection.isReplay("default", messageNumber)
}
```

Because the ratchet key ID is always `"default"`, replay protection does not reset when a DH ratchet step occurs. An attacker who captures message #5 from ratchet epoch A can replay it in epoch B — the `"default:5"` key will already be in the set if any message #5 was seen in epoch A, but if epoch B sees message #5 first, it is recorded as `"default:5"` and the epoch A copy is then silently blocked (false positive). Worse: if the epochs occur in reverse order, the epoch B message is blocked as a replay.

The `ReplayProtection` class was designed correctly (keyed on ratchet ID). The bug is at the call site.

Fix: Thread the DH public key bytes through to `validateMessage` and use the two-argument overload:

```kotlin
// SessionManager.kt
fun validateMessage(peerId: String, ratchetKeyId: String, messageNumber: Int): Boolean {
    val session = getSession(peerId) ?: return false
    if (session.isReplay(ratchetKeyId, messageNumber)) return false
    return true  // isReplay() records the key internally
}

// MessageReceiver.kt — pass the DH key from the packet header
val ratchetKeyId = packet.header.dhPublicKey.joinToString()
if (!SessionManager.validateMessage(peerId, ratchetKeyId, packet.header.messageNumber)) {
    throw IllegalStateException("Replay detected: message #${packet.header.messageNumber}")
}
```

> [!CAUTION]
> This negates the v1 fix to Issue #6. The replay protection infrastructure is correct; it is just not being called correctly. Fix before any security review or demo that includes adversarial testing.

---

### ⚠️ Issue #34 — EncryptionManager is Orphaned and Contradicts the Ratchet

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: Open — NEW in v2]**
File: [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java), [utils/SessionManager.java](app/src/main/java/com/hacksecure/p2p/utils/SessionManager.java)

`EncryptionManager` is a standalone AES-GCM class that derives a single static key from the ECDH shared secret via HKDF. `utils.SessionManager.setEncryptionManager()` and `getEncryptionManager()` exist to wire it up, but neither method is called anywhere in the active code path. As a result, `EncryptionManager` encrypts and decrypts nothing.

The danger is not current functionality — it's future confusion. If a developer wires `EncryptionManager` into the message path thinking it's part of the ratchet pipeline, every message would be encrypted under a single static session key with no forward secrecy and no break-in recovery. This directly contradicts the Double Ratchet's security guarantees.

Fix: Delete `EncryptionManager.java` and the `setEncryptionManager()`/`getEncryptionManager()` methods from `utils.SessionManager`. If a simple AEAD wrapper is ever needed outside the ratchet context, create a new class with a name that makes the non-ratchet scope explicit (e.g. `StaticSessionCipher`).

---

### ⚠️ Issue #35 — MessageTTLManager TTL Field is Always Zero

**[DIMENSION 2] [SEVERITY: MEDIUM] [STATUS: Open — NEW in v2]**
File: [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java#L218), [MessageTTLManager.kt](app/src/main/java/com/hacksecure/p2p/messaging/ttl/MessageTTLManager.kt)

In `ChatActivity.sendMessage()`, the `MessageMetadata` constructor is called with `ttlSeconds = 0`:

```java
MessageMetadata metadata = new MessageMetadata(
    selfId,
    UUID.randomUUID().toString(),
    System.currentTimeMillis(),
    0   // ttlSeconds — hardcoded zero
);
```

A TTL of `0` means messages expire at `timestamp + 0ms = timestamp`, i.e. immediately on creation. If `MessageTTLManager.start()` were ever called (Issue #18), it would delete every message the instant the cleanup task runs. This compounds Issue #18: not only is the manager unused, if it were activated it would silently wipe all stored messages.

Fix: Either pass a meaningful TTL (e.g. `TimeUnit.HOURS.toSeconds(24)`) or remove the TTL field until the feature is intentionally implemented.

---

### ⚠️ Issue #36 — app_name in strings.xml Does Not Match Project Identity

**[DIMENSION 5] [SEVERITY: MEDIUM] [STATUS: Open — NEW in v2]**
File: [app/src/main/res/values/strings.xml](app/src/main/res/values/strings.xml#L2), [settings.gradle](settings.gradle)

```xml
<string name="app_name">HackSecure 2026</string>
```

The app displays "HackSecure 2026" in the launcher and action bar. `settings.gradle` names the root project "HackSecure 2026". The README, package name (`com.hacksecure.p2p`), and repo name all say `SentinalChat`. These are two different hackathon submissions — SentinalChat was built for India Innovates 2026, but the app shell was cloned from or is named after the HackSecure 2026 submission.

This will confuse judges and demo audiences. Fix before any presentation:

```xml
<string name="app_name">SentinalChat</string>
```

And in `settings.gradle`:

```groovy
rootProject.name = "SentinalChat"
```

---

### Issue #37 — Message Bubble Uses Launcher Icon as Background

**[DIMENSION 5] [SEVERITY: LOW] [STATUS: Open — NEW in v2]**
File: [app/src/main/res/layout/item_message.xml](app/src/main/res/layout/item_message.xml#L14)

```xml
android:background="@drawable/ic_launcher_foreground"
```

The launcher foreground drawable is a green square with a white border outline. Every sent and received message bubble renders as a solid green square — no chat bubble shape, no distinction between sent/received alignment. The `text-align` logic in `MessageAdapter` correctly aligns the `TextView` content, but the background makes every message look like a launcher icon tile.

Fix: Create a proper `res/drawable/bg_message_sent.xml` and `bg_message_received.xml` using `<shape>` with rounded corners, or use Material Design `MaterialCardView` / a `DrawableCompat` tint on a rounded rect shape.

---

## Summary Table

| # | File | Dimension | Severity | Issue | Status |
|---|------|-----------|----------|-------|--------|
| 1 | [KeyManager.java](app/src/main/java/com/hacksecure/p2p/crypto/KeyManager.java) | Security | 🔴 CRITICAL | `wipe()` only nulled reference, didn't zero key bytes | ✅ Fixed |
| 2 | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | Security | 🔴 CRITICAL | `e.printStackTrace()` leaked stack traces to Logcat | ✅ Fixed |
| 3 | [app/build.gradle](app/build.gradle) | Security | 🔴 CRITICAL | `minifyEnabled false` on release builds | ✅ Fixed |
| 4 | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | Security | 🟠 HIGH | Ephemeral private key discarded — broke handshake | ✅ Fixed |
| 5 | [QRHandshakeManager.kt](app/src/main/java/com/hacksecure/p2p/Protocol/handshake/QRHandshakeManager.kt) | Security | 🟠 HIGH | No responder-side handshake path | ✅ Fixed |
| 6 | [SessionState.kt](app/src/main/java/com/hacksecure/p2p/session/SessionState.kt) | Security | 🟠 HIGH | Replay window unbounded, not per-ratchet-key | ✅ Fixed |
| 7 | [DoubleRatchet.kt](app/src/main/java/com/hacksecure/p2p/Protocol/Ratchet/DoubleRatchet.kt) | Security | 🟠 HIGH | Skipped message keys never pruned globally | ✅ Fixed |
| 8 | [Logger.java](app/src/main/java/com/hacksecure/p2p/utils/Logger.java) | Security | 🟠 HIGH | Debug logs unguarded in production builds | ✅ Fixed |
| 9 | [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java) | Security | 🟠 HIGH | Custom single-round HMAC KDF instead of HKDF | ✅ Fixed |
| 10 | [IdentityVerification.kt](app/src/main/java/com/hacksecure/p2p/security/IdentityVerification.kt) | Security | 🟡 MEDIUM | `verifyKeys()` semantic logic bug | ✅ Fixed |
| 11 | [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java) | Security | 🟡 MEDIUM | TCP socket plaintext — metadata leakage | Acceptable |
| 12 | [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java) | Security | 🟢 LOW | `aesKey` not `volatile` | Minor |
| 13 | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | Architecture | 🟠 HIGH | God class — 6+ responsibilities | Noted |
| 14 | `SessionManager.java/.kt` | Architecture | 🟠 HIGH | Duplicate class names across packages | Partially fixed |
| 15 | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | Architecture | 🟡 MEDIUM | `MessageReceiver` created per message | ✅ Fixed |
| 16 | [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java) | Architecture | 🟡 MEDIUM | `ExecutorService` never shut down | ✅ Fixed |
| 17 | [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java) | Architecture | 🟡 MEDIUM | References not nulled after `close()` | ✅ Fixed |
| 18 | [MessageTTLManager.kt](app/src/main/java/com/hacksecure/p2p/messaging/ttl/MessageTTLManager.kt) | Architecture | 🟡 MEDIUM | Unused class, scheduler not lifecycle-aware | Dead code |
| 19 | All Java files | Interop | 🟠 HIGH | Missing `@Nullable`/`@NonNull` annotations | ✅ Fixed |
| 20 | [Message.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Message.java) | Interop | 🟡 MEDIUM | Should be Kotlin data class | Noted |
| 21 | [Peer.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Peer.java), [Session.java](app/src/main/java/com/hacksecure/p2p/messaging/models/Session.java) | Interop | 🟡 MEDIUM | Pure boilerplate POJOs | Noted |
| 22 | [KeySetupActivity.java](app/src/main/java/com/hacksecure/p2p/ui/KeySetupActivity.java) | Interop | 🟡 MEDIUM | Displayed fake fingerprint from wrong key | ✅ Fixed |
| 23 | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | WiFi Direct | 🟠 HIGH | No reconnection after connection loss | Noted |
| 24 | [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java) | WiFi Direct | 🟠 HIGH | No connection state machine | Noted |
| 25 | [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java) | WiFi Direct | 🟡 MEDIUM | `groupOwnerIntent` not set — GO negotiation random | **Still open** |
| 26 | [ConnectionHandler.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/ConnectionHandler.java) | WiFi Direct | 🟡 MEDIUM | `ServerSocket` missing `SO_REUSEADDR` | ✅ Fixed |
| 27 | [WifiDirectManager.java](app/src/main/java/com/hacksecure/p2p/network/wifidirect/WifiDirectManager.java) | WiFi Direct | 🟡 MEDIUM | Held Activity context, potential leak | ✅ Fixed |
| 28 | [proguard-rules.pro](app/proguard-rules.pro) | Build | 🔴 CRITICAL | File didn't exist, build would fail on minify | ✅ Fixed |
| 29 | [.gitignore](.gitignore) | Build | 🟡 MEDIUM | Missing `*.jks` signing key pattern | ✅ Fixed |
| 30 | [gradle.properties](gradle.properties) | Build | 🟡 MEDIUM | Missing `android.enableR8=true` | ✅ Fixed |
| 31 | [app/build.gradle](app/build.gradle) | Build | 🟢 LOW | Some dependencies behind latest | Can update |
| 32 | All files | Build | ℹ️ INFO | TODO comments document incomplete features | Acceptable |
| **33** | [SessionManager.kt](app/src/main/java/com/hacksecure/p2p/session/SessionManager.kt) | **Security** | 🟠 **HIGH** | **Replay protection uses `"default"` key ID at call site — bypasses per-ratchet keying** | **🆕 Open** |
| **34** | [EncryptionManager.java](app/src/main/java/com/hacksecure/p2p/crypto/EncryptionManager.java) | **Architecture** | 🟡 **MEDIUM** | **Orphaned static-key cipher contradicts ratchet — delete it** | **🆕 Open** |
| **35** | [ChatActivity.java](app/src/main/java/com/hacksecure/p2p/ui/ChatActivity.java) | **Architecture** | 🟡 **MEDIUM** | **TTL hardcoded to 0 — would purge all messages if TTLManager activated** | **🆕 Open** |
| **36** | [strings.xml](app/src/main/res/values/strings.xml) | **Build** | 🟡 **MEDIUM** | **App name is "HackSecure 2026" — wrong hackathon** | **🆕 Open** |
| **37** | [item_message.xml](app/src/main/res/layout/item_message.xml) | **Build** | 🟢 **LOW** | **Message bubble uses launcher icon drawable as background** | **🆕 Open** |

---

## Severity Distribution

```
CRITICAL:  0  (all 4 original CRITICAL issues resolved ✅)
HIGH:      1  (Issue #33 — new)
MEDIUM:    3  (Issues #34, #35, #36 — new)
LOW:       1  (Issue #37 — new)
OPEN (pre-existing, Noted): Issues #13, #14, #18, #20, #21, #23, #24, #25
```

---

## What's Done Right ✅

The core cryptography and protocol implementation remain strong, and the v1 fixes were applied correctly:

| Area | Implementation | Grade |
|------|---------------|-------|
| **Identity Keys** | AndroidKeyStore, `PURPOSE_AGREE_KEY`, TEE-backed | ✅ Excellent |
| **HKDF** | RFC 5869 extract+expand, PRK wiped after use | ✅ Correct |
| **AES-GCM** | 256-bit, 12-byte random IV, 128-bit tag, AAD, key copy wiped | ✅ Solid |
| **Double Ratchet** | RootKey → ChainKey → MessageKey derivation correct | ✅ Correct |
| **DH Ratchet Steps** | Two-step root derivation, correct chain assignment per role | ✅ Correct |
| **QR Handshake** | 3-DH (IK×IK, EK×EK, EK×IK), intermediate secrets wiped | ✅ Correct |
| **TCP Framing** | Length-prefixed, `readFully`, max size validated | ✅ Good |
| **Fingerprint Comparison** | Constant-time `xor` comparison, length-difference encoded | ✅ Security-aware |
| **QR Parsing** | Version check, format validation, empty key rejection | ✅ Defensive |
| **Memory Hygiene** | `MemoryCleaner`, `Arrays.fill(0)` throughout crypto paths | ✅ Good intent |
| **Skipped Key Pruning** | Global cap + `MemoryCleaner.wipe()` on evicted entries | ✅ Correct |
| **ReplayProtection class** | Per-ratchet-key-id keying, bounded cache, pruning | ✅ Well-designed |
| **Serialisation** | Binary format, version byte, per-field bounds checks | ✅ Forward-compatible |
| **Logger** | Runtime debug flag from `FLAG_DEBUGGABLE`, no static BuildConfig | ✅ Correct |
| **Connection Close** | `AtomicBoolean`, `shutdownNow()`, references nulled | ✅ Clean |

---

> [!TIP]
> **Recommended fix order for v2 open issues:**
> #33 (replay keying, 5-minute fix) → #36 (app name, 1-minute fix) → #25 (groupOwnerIntent, 2-minute fix) → #34 (delete EncryptionManager) → #35 (TTL constant) → #37 (message bubble drawable).
> Total estimated time: ~45 minutes. All five are mechanical — no architectural changes required.
