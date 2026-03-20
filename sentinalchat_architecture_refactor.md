# SentinalChat — Architecture Refactor & UI/UX Redesign

> **Document type:** Engineering + Design Specification
> **Scope:** Full MVVM migration, dead feature activation, new Activity definitions, navigation graph
> **Prerequisite:** Audit fixes #1–#33 applied. This document assumes the crypto core is correct and untouched.

---

## Table of Contents

1. [What is Currently Broken or Unused](#1-what-is-currently-broken-or-unused)
2. [Design Philosophy](#2-design-philosophy)
3. [New Navigation Architecture](#3-new-navigation-architecture)
4. [Screen-by-Screen Specification](#4-screen-by-screen-specification)
   - [4.1 SplashActivity (new)](#41-splashactivity-new)
   - [4.2 IdentityActivity (replaces StartSessionActivity + KeySetupActivity)](#42-identityactivity-replaces-startsessionactivity--keysetupactivity)
   - [4.3 DeviceDiscoveryActivity (existing — extended)](#43-devicediscoveryactivity-existing--extended)
   - [4.4 HandshakeActivity (new — splits ChatActivity)](#44-handshakeactivity-new--splits-chatactivity)
   - [4.5 ChatActivity (existing — gutted and rebuilt)](#45-chatactivity-existing--gutted-and-rebuilt)
   - [4.6 SessionDetailActivity (new)](#46-sessiondetailactivity-new)
5. [MVVM Layer Definitions](#5-mvvm-layer-definitions)
   - [5.1 ChatViewModel](#51-chatviewmodel)
   - [5.2 ConnectionRepository](#52-connectionrepository)
   - [5.3 HandshakeManager](#53-handshakemanager)
   - [5.4 SessionRepository](#54-sessionrepository)
6. [Activating Every Unused Feature](#6-activating-every-unused-feature)
   - [6.1 MessageTTLManager — ephemeral messages](#61-messagettlmanager--ephemeral-messages)
   - [6.2 IdentityVerification — TOFU key change detection](#62-identityverification--tofu-key-change-detection)
   - [6.3 KeyFingerprint — safety number screen](#63-keyfingerprint--safety-number-screen)
   - [6.4 ReplayProtection — visible security indicator](#64-replayprotection--visible-security-indicator)
   - [6.5 SessionDatabase — persistent session resumption](#65-sessiondatabase--persistent-session-resumption)
   - [6.6 MessageMetadata.ttlSeconds — per-message expiry UI](#66-messagemetadatattlseconds--per-message-expiry-ui)
   - [6.7 PeerDiscovery — peer list with status](#67-peerdiscovery--peer-list-with-status)
   - [6.8 ConnectionState enum — connection status bar](#68-connectionstate-enum--connection-status-bar)
7. [AndroidManifest Changes](#7-androidmanifest-changes)
8. [Integration Contract — How Every Piece Connects](#8-integration-contract--how-every-piece-connects)
9. [Master Prompt — Implement This Refactor](#9-master-prompt--implement-this-refactor)

---

## 1. What is Currently Broken or Unused

Before designing anything, here is the full inventory of built-but-dead code. Every item below has a working implementation that is simply never called or never surfaced in the UI.

| Feature | Where it lives | Current status | Why it's dead |
|---|---|---|---|
| `MessageTTLManager` | `messaging/ttl/MessageTTLManager.kt` | `start()` never called | Not wired into app lifecycle |
| `MessageMetadata.ttlSeconds` | `messaging/models/MessageMetadata.kt` | Hardcoded to `0` in `ChatActivity` | No UI to set TTL |
| `SessionDatabase` | `storage/SessionDatabase.kt` | Never written to or read from | No persistence code calls it |
| `MessageDatabase` | `storage/MessageDatabase.kt` | Never written to or read from | No code calls `storeMessage()` |
| `IdentityVerification` | `security/IdentityVerification.kt` | Never called | No UI to show or compare fingerprints |
| `KeyFingerprint` | `identity/KeyFingerprint.kt` | Called only in `KeySetupActivity` to display a truncated fake string | Real formatted fingerprint never shown to user |
| `PeerDiscovery` | `network/wifidirect/PeerDiscovery.kt` | Instantiated nowhere | `WifiDirectManager` manages its own peer list independently |
| `ReplayProtection` | `security/ReplayProtection.kt` | Class is correct but call site uses wrong key ID (Issue #33) | Functional but muted |
| `EphemeralSessionStore` | `session/EphemeralSessionStore.kt` | Used by `SessionManager` but session state is never persisted or shown | No UI surfaces session info |
| `MessageMetadata.messageId` | `messaging/models/MessageMetadata.kt` | Generated via `UUID.randomUUID()` but never displayed or used for deduplication | No dedup logic |
| `NonceGenerator` | `security/NonceGenerator.kt` | Generates GCM nonces but `AESGCMCipher` uses `SecureRandomProvider` directly | Effectively dead utility |
| `RatchetState` serialization | `Protocol/Ratchet/RatchetState.kt` | Full encode/decode implementation exists | Never called — ratchet state is never saved |
| `ConnectionState` enum | Mentioned in TODO comment in `WifiDirectManager` | Not implemented | State machine not built |
| `MessageTTLManager.cleanupSessions()` | `messaging/ttl/MessageTTLManager.kt` | References `SessionDatabase.getAllSessions()` | Both sides unused |
| `utils.SessionManager.endSession()` | `utils/SessionManager.java` | Calls `keyManager.wipe()` and `encryptionManager.wipe()` | Never called — no session teardown |

Additionally, two entire Activities exist with minimal implementation:

- **`StartSessionActivity`** — two buttons, creates a `Session` object via the legacy `utils.SessionManager` that is immediately discarded, then navigates forward. The `Session` it creates is never referenced again.
- **`KeySetupActivity`** — shows a fingerprint and a Continue button. Before the v1 fix it showed a fake fingerprint from the wrong key. Now it shows the real one, but there is no verification step, no copy-to-clipboard, no share, and the user has no way to compare their fingerprint with their peer's.

---

## 2. Design Philosophy

SentinalChat is an **offline-first, zero-server, ephemeral** messaging app. The UX should communicate these properties at every step. The three principles that guide every screen decision:

**1. Security is visible, not hidden.** The Double Ratchet, fingerprint verification, and replay protection are real security properties. The UI should surface them — not bury them. A padlock icon in a corner communicates nothing. A safety number the user can read aloud to their peer communicates everything.

**2. Every step has exactly one job.** The current `ChatActivity` tries to do the entire app's work in one screen. Each new Activity handles one phase of the protocol: identity → discovery → handshake → conversation. A user who understands the flow is less likely to make operational security mistakes.

**3. Ephemerality is the feature.** Every message can have a TTL. Sessions can be torn down. The fact that nothing leaves a server is the product's core value. Surface it: show TTL timers on messages, show session age, let the user nuke a session from the session detail screen.

---

## 3. New Navigation Architecture

```
App Launch
    │
    ▼
SplashActivity          (new)
    │  IdentityKeyManager.initialize()
    │  Load persisted sessions from SessionDatabase
    │
    ├─── First launch ──────────────────────────► IdentityActivity
    │                                               Show fingerprint
    │                                               Confirm identity
    │                                                   │
    │                                                   ▼
    └─── Returning user ────────────────────────► DeviceDiscoveryActivity
                                                    Discover peers
                                                    OR resume persisted session
                                                        │
                                                        ▼
                                                   HandshakeActivity     (new)
                                                    Show own QR
                                                    Scan peer QR
                                                    Confirm fingerprints
                                                        │
                                                        ▼
                                                   ChatActivity
                                                    Message list
                                                    TTL selector
                                                    Connection status bar
                                                        │
                                                   (from toolbar)
                                                        ▼
                                                   SessionDetailActivity  (new)
                                                    Peer fingerprint
                                                    Session stats
                                                    Destroy session
```

**Intent flow between Activities:**

```
SplashActivity
  → IdentityActivity         (first launch flag)
  → DeviceDiscoveryActivity  (returning user, or after IdentityActivity confirms)

IdentityActivity
  → DeviceDiscoveryActivity  (user taps "Start Secure Session")

DeviceDiscoveryActivity
  → HandshakeActivity        (IS_HOST: Boolean, GROUP_OWNER_ADDRESS: String)

HandshakeActivity
  → ChatActivity             (PEER_ID: String, IS_HOST: Boolean, GROUP_OWNER_ADDRESS: String)
  ← back (handshake failed or user cancels)

ChatActivity
  → SessionDetailActivity    (PEER_ID: String, SESSION_START_TIME: Long)
  ← finish (session destroyed)

SessionDetailActivity
  ← finish (session info viewed)
  → finish ChatActivity too  (if user destroys session from detail screen)
```

---

## 4. Screen-by-Screen Specification

---

### 4.1 SplashActivity (new)

**File:** `ui/SplashActivity.java`
**Layout:** `activity_splash.xml`
**Purpose:** App entry point. Initialises `IdentityKeyManager`, loads any persisted sessions from `SessionDatabase`, then routes to the correct destination. Replaces the current implicit entry via `StartSessionActivity`.

**What it does:**

1. Calls `IdentityKeyManager.INSTANCE.initialize(this)` — this is currently done in `SentinelChatApp.onCreate()` and can remain there, but the splash screen waits for it before proceeding.
2. Reads `SessionDatabase` to check for any unexpired persisted sessions.
3. Checks a `SharedPreferences` boolean `"identity_confirmed"`. If false (first launch), routes to `IdentityActivity`. If true, routes to `DeviceDiscoveryActivity`.
4. Displays the app name and a brief tagline for 1–2 seconds while initialisation runs.

**Layout spec (`activity_splash.xml`):**

```xml
<!-- Centered vertically and horizontally -->
<!-- App name in large bold text -->
<!-- Tagline: "End-to-end encrypted · No servers · No traces" -->
<!-- Animated progress indicator (indeterminate) while initializing -->
<!-- No buttons — this is a pure routing screen -->
```

**Integration:**

- Set as `android:exported="true"` with `MAIN`/`LAUNCHER` intent filter in `AndroidManifest.xml`.
- Remove the `MAIN`/`LAUNCHER` intent filter from `StartSessionActivity` (it stays in the manifest for now as a utility entry point but is no longer the launcher).
- `StartSessionActivity` can be fully deleted once `SplashActivity` + `IdentityActivity` cover its two buttons ("Create Session" / "Join Session"). The role distinction (host vs guest) moves to `DeviceDiscoveryActivity`.

**Class skeleton:**

```java
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        boolean identityConfirmed = getPreferences(MODE_PRIVATE)
            .getBoolean("identity_confirmed", false);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            Intent intent = new Intent(this,
                identityConfirmed
                    ? DeviceDiscoveryActivity.class
                    : IdentityActivity.class);
            startActivity(intent);
            finish();
        }, 1200);
    }
}
```

---

### 4.2 IdentityActivity (replaces StartSessionActivity + KeySetupActivity)

**File:** `ui/IdentityActivity.java`
**Layout:** `activity_identity.xml`
**Purpose:** First-launch identity screen. Shows the user their real SHA-256 fingerprint from `IdentityKeyManager`, lets them copy it, and confirms they have noted it. Also introduces the concept: "This fingerprint is your identity on this device. Share it with trusted peers to verify each other."

**Replaces:** `StartSessionActivity` (deleted) and `KeySetupActivity` (deleted or repurposed as a settings deep-link).

**What it shows:**

1. A header explaining what the fingerprint is in plain language.
2. The formatted 64-character hex fingerprint from `IdentityKeyManager.INSTANCE.getFingerprint()` rendered in monospace, chunked in groups of 4 separated by dashes. This is the output of `KeyFingerprint.generate()` — finally surfaced.
3. A "Copy fingerprint" button that puts the fingerprint on the clipboard.
4. A "Share fingerprint" button that fires an `ACTION_SEND` intent so the user can send their fingerprint to a peer over any channel (Signal, email, in person) before the WiFi Direct session.
5. A "I've noted my fingerprint — Continue" button that sets `"identity_confirmed" = true` in `SharedPreferences` and navigates to `DeviceDiscoveryActivity`.

**Why this activates dead code:**

- `KeyFingerprint.generate(publicKey)` — currently called only in the broken v1 `KeySetupActivity` path. Now the primary content of this screen.
- `IdentityKeyManager.getFingerprint()` — now the source of truth for the displayed value.
- The fingerprint format (`XXXX-XXXX-XXXX-...`) is what users will read aloud to each other during the handshake confirmation step in `HandshakeActivity`.

**Layout spec (`activity_identity.xml`):**

```xml
<!-- ScrollView wrapping a vertical LinearLayout -->
<!-- [Icon/logo at top] -->
<!-- [Heading] "Your secure identity" -->
<!-- [Body text] "SentinalChat identifies you by a cryptographic fingerprint stored
     in your device's secure hardware. No server knows it. Share it with your peer
     before connecting so you can verify each other." -->
<!-- [Card with monospace TextView] — the formatted fingerprint, large text, selectable -->
<!-- [Button] "Copy fingerprint" -->
<!-- [Button] "Share fingerprint" (outlined, secondary) -->
<!-- [Divider] -->
<!-- [Button] "I've noted my fingerprint — Start" (filled, primary) -->
```

**Class skeleton:**

```java
public class IdentityActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_identity);

        String fingerprint = IdentityKeyManager.INSTANCE.getFingerprint();
        TextView tvFingerprint = findViewById(R.id.tvFingerprint);
        tvFingerprint.setText(fingerprint);

        findViewById(R.id.btnCopy).setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("SentinalChat fingerprint", fingerprint));
            Toast.makeText(this, "Fingerprint copied", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnShare).setOnClickListener(v -> {
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/plain");
            share.putExtra(Intent.EXTRA_TEXT,
                "My SentinalChat fingerprint:\n" + fingerprint);
            startActivity(Intent.createChooser(share, "Share fingerprint"));
        });

        findViewById(R.id.btnContinue).setOnClickListener(v -> {
            getPreferences(MODE_PRIVATE).edit()
                .putBoolean("identity_confirmed", true)
                .apply();
            startActivity(new Intent(this, DeviceDiscoveryActivity.class));
            finish();
        });
    }
}
```

---

### 4.3 DeviceDiscoveryActivity (existing — extended)

**File:** `ui/connection/DeviceDiscoveryActivity.java` — extend, do not replace.
**Purpose:** Discover nearby WiFi Direct peers and initiate connection. Currently functional but missing the host/guest role selector and the `groupOwnerIntent` fix.

**Changes required:**

**1. Add role selector UI.** The current flow requires the calling Activity to pass `IS_HOST` as an Intent extra. Since `StartSessionActivity` is being deleted, the role choice moves here. Add two tabs or a toggle at the top:

```
[ Host a session ]   [ Join a session ]
```

- **Host** — calls `wifiDirectManager.createGroup()`, calls `connectionHandler.startServer()`.
- **Join** — calls `wifiDirectManager.startDiscovery()`, shows peer list, calls `connectToPeer()` when user taps a peer.

This replaces the two-button screen of `StartSessionActivity` entirely.

**2. Apply the `groupOwnerIntent` fix.** In `connectToPeer()`, set:
```java
config.groupOwnerIntent = isHost ? 15 : 0;
```

**3. Wire `PeerDiscovery.kt`.** Currently `WifiDirectManager` maintains its own peer list inside the broadcast receiver. `PeerDiscovery.java` exists as a standalone class with `updatePeers()`, `getPeers()`, `findPeerByAddress()`, and `clearPeers()` — but nothing instantiates it. Replace the raw `List<WifiP2pDevice>` field in `DeviceDiscoveryActivity` with a `PeerDiscovery` instance:

```java
// Currently unused — activate it
private final PeerDiscovery peerDiscovery = new PeerDiscovery();

@Override
public void onPeersAvailable(List<WifiP2pDevice> peers) {
    peerDiscovery.updatePeers(new WifiP2pDeviceList(...));
    runOnUiThread(() -> adapter.updatePeers(peerDiscovery.getPeers()));
}
```

**4. Add connection status.** Show a `ConnectionState`-aware status line in the toolbar subtitle:
- `IDLE` → "Ready"
- `DISCOVERING` → "Scanning for peers..."
- `CONNECTING` → "Connecting..."
- `CONNECTED` → "Connected — proceed to verify"

**Layout additions (`activity_device_discovery.xml`):**

```xml
<!-- Add at top: TabLayout or ToggleButton for Host / Join role selection -->
<!-- Existing RecyclerView for peer list stays unchanged -->
<!-- Add at bottom: status TextView showing current ConnectionState -->
<!-- Add: empty state message when peer list is empty ("No peers found. Make sure
     the other device has SentinalChat open.") -->
```

**Intent out:** Navigates to `HandshakeActivity` (not `ChatActivity` directly) on successful connection:

```java
@Override
public void onConnected(WifiP2pInfo info) {
    runOnUiThread(() -> {
        Intent intent = new Intent(this, HandshakeActivity.class);
        intent.putExtra("IS_HOST", isHost);
        intent.putExtra("GROUP_OWNER_ADDRESS", info.groupOwnerAddress.getHostAddress());
        startActivity(intent);
        finish();
    });
}
```

---

### 4.4 HandshakeActivity (new)

**File:** `ui/HandshakeActivity.java`
**Layout:** `activity_handshake.xml`
**Purpose:** Dedicated screen for the QR-based key exchange and fingerprint verification. Extracted entirely from `ChatActivity`. This is the most important new screen — it makes the handshake a deliberate, visible act rather than a background operation.

**This screen is the home of the entire `QRHandshake` flow.**

**Two-phase layout:**

**Phase 1 — QR exchange (both sides run simultaneously)**

The screen is split into two vertical sections:

```
┌─────────────────────────────────┐
│  YOUR CODE                      │
│  ┌─────────────────────────┐    │
│  │  [QR bitmap of own      │    │
│  │   identity payload]     │    │
│  └─────────────────────────┘    │
│  "Show this to your peer"       │
├─────────────────────────────────┤
│  SCAN PEER'S CODE               │
│  ┌─────────────────────────┐    │
│  │  [CameraX PreviewView]  │    │
│  │  scanning...            │    │
│  └─────────────────────────┘    │
│  "Point at your peer's screen"  │
└─────────────────────────────────┘
```

The top half shows the local QR code (own identity + ephemeral key) from `QRCodeGenerator`. The bottom half runs `QRCodeScanner` with the `CameraX` preview. When a QR code is detected, the scanner fires `QRHandshake.performHandshake()` or `performHandshakeAsResponder()` depending on the `IS_HOST` flag.

**Phase 2 — Fingerprint verification (after handshake completes)**

The layout transitions to a verification screen:

```
┌─────────────────────────────────┐
│  ✓ Encrypted channel established│
│                                 │
│  YOUR FINGERPRINT               │
│  [XXXX-XXXX-XXXX-XXXX-...]     │  ← from IdentityKeyManager
│                                 │
│  PEER'S FINGERPRINT             │
│  [XXXX-XXXX-XXXX-XXXX-...]     │  ← derived from parsed QR identity key
│                                 │
│  "Read these aloud to each      │
│   other and confirm they match" │
│                                 │
│  [ ✓ Fingerprints match ]       │  ← primary button
│  [ Something looks wrong ]      │  ← secondary/destructive button
└─────────────────────────────────┘
```

The peer fingerprint is derived from the identity key bytes parsed out of their QR payload via `KeyFingerprint.generate(remoteIdentityKey)`. This is the first time `IdentityVerification` and `KeyFingerprint` are used in the actual conversation flow, not just as utilities.

**What this activates:**
- `QRCodeGenerator` — previously crammed into `ChatActivity.generateAndDisplayQR()`, now owns its screen.
- `QRCodeScanner` — previously crammed into `ChatActivity.startQRScanning()`, now owns its half of the screen.
- `IdentityVerification.computeFingerprint()` — now called to display the peer fingerprint for manual verification.
- `KeyFingerprint.generate()` — called to produce the formatted peer fingerprint string.
- `localEphemeralKeyPair` management — cleaned up and owned here, not in `ChatActivity`.

**TOFU check:** Before transitioning to Phase 2, `HandshakeActivity` checks whether this peer has been seen before via `SessionDatabase.getSession(peerId)`. If a previous session exists, it compares the stored identity key bytes with the current ones using `IdentityVerification.verifyRemoteIdentity()`. If they differ, it shows a warning:

```
⚠️ Warning: This peer's identity has changed since your last session.
   Previous fingerprint: XXXX-XXXX-...
   Current fingerprint:  YYYY-YYYY-...
   Only continue if you trust this change.
```

This activates `IdentityVerification.verifyRemoteIdentity()` — currently dead code — for its intended purpose.

**On confirmation:** Navigates to `ChatActivity` with the established `DoubleRatchet` instance and peer identity data:

```java
private void onHandshakeVerified() {
    // Persist the session to SessionDatabase for future TOFU checks
    SessionDatabase sessionDb = new SessionDatabase();
    sessionDb.saveSession(
        peerId,
        remoteIdentityKeyBytes,
        new byte[0],  // ratchet state serialization — see §6.5
        System.currentTimeMillis()
    );

    Intent intent = new Intent(this, ChatActivity.class);
    intent.putExtra("PEER_ID", peerId);
    intent.putExtra("IS_HOST", isHost);
    intent.putExtra("GROUP_OWNER_ADDRESS", groupOwnerAddress);
    startActivity(intent);
    finish();
}

private void onHandshakeMismatch() {
    SessionManager.INSTANCE.destroySession(peerId);
    connectionHandler.close();
    Toast.makeText(this, "Session aborted — fingerprint mismatch", Toast.LENGTH_LONG).show();
    finish();
}
```

**Class fields:**

```java
public class HandshakeActivity extends AppCompatActivity {

    private ConnectionHandler connectionHandler;
    private QRCodeScanner qrCodeScanner;
    private KeyPair localEphemeralKeyPair;
    private DoubleRatchet ratchet;

    private String peerId;
    private byte[] remoteIdentityKeyBytes;
    private String groupOwnerAddress;
    private boolean isHost;
    private boolean handshakeDone = false;

    // Phase enum
    private enum Phase { QR_EXCHANGE, FINGERPRINT_VERIFY }
    private Phase currentPhase = Phase.QR_EXCHANGE;

    // ...
}
```

**AndroidManifest entry:**

```xml
<activity android:name=".ui.HandshakeActivity" />
```

---

### 4.5 ChatActivity (existing — gutted and rebuilt)

**File:** `ui/ChatActivity.java` — keep the file, rewrite the body.
**Purpose:** Pure conversation screen. No crypto, no handshake, no QR. Receives an established `DoubleRatchet` via `ChatViewModel`, which gets it from `SessionManager`.

**What stays:**
- `RecyclerView` with `MessageAdapter`
- `EditText` + Send button
- `onDestroy()` teardown

**What is removed:**
- All QR generation code (moves to `HandshakeActivity`)
- All QR scanning code (moves to `HandshakeActivity`)
- All handshake code (moves to `HandshakeActivity`)
- All direct `ConnectionHandler` management (moves to `ConnectionRepository`)
- `PreviewView` from the layout (no longer needed)
- `ivQr` ImageView from the layout (no longer needed)

**What is added:**

**1. Connection status bar** — a thin bar at the top of the screen that reflects the live connection state (from `ConnectionRepository.connectionState: LiveData<ConnectionState>`):

```
┌─────────────────────────────────────────┐
│ 🔒 Connected · Double Ratchet · E2E     │  ← green, normal state
│ ⚠ Reconnecting... (attempt 2/5)         │  ← amber, reconnecting
│ ✕ Connection lost                       │  ← red, failed
└─────────────────────────────────────────┘
```

**2. TTL selector on the message input bar.** A small clock icon next to the send button that opens a bottom sheet with TTL options:
- No expiry (default)
- 5 minutes
- 1 hour
- 24 hours
- On read (auto-delete after first decryption)

This feeds the selected TTL value into `MessageMetadata.ttlSeconds` — activating the field for the first time.

**3. Per-message TTL indicator.** Messages with a non-zero TTL show a small countdown or expiry label below the message bubble:

```
"Hey, meet at the usual place"
⏱ Expires in 4m 32s
```

**4. Toolbar with peer info.** The toolbar shows the peer's truncated fingerprint and a chevron that opens `SessionDetailActivity`:

```
← [peer fingerprint: XXXX-XXXX] ›
```

**5. ChatViewModel integration.** `ChatActivity` observes `chatViewModel.messages: LiveData<List<ChatMessage>>` and `chatViewModel.connectionState: LiveData<ConnectionState>`. It only calls:
- `chatViewModel.send(text, ttlSeconds)`
- `chatViewModel.observeMessages()`
- `chatViewModel.destroySession()`

It touches no crypto directly.

**Layout spec (`activity_chat.xml` — rewritten):**

```xml
<androidx.constraintlayout.widget.ConstraintLayout>

    <!-- Connection status bar (thin, colored) -->
    <TextView android:id="@+id/tvConnectionStatus" ... />

    <!-- Message list -->
    <androidx.recyclerview.widget.RecyclerView android:id="@+id/rvMessages" ... />

    <!-- Input bar with TTL selector -->
    <LinearLayout android:id="@+id/layoutInput" android:orientation="horizontal">
        <ImageButton android:id="@+id/btnTtl" ... />   <!-- clock icon -->
        <EditText android:id="@+id/etMessage" ... />
        <Button android:id="@+id/btnSend" ... />
    </LinearLayout>

</androidx.constraintlayout.widget.ConstraintLayout>
```

The `PreviewView` and `ImageView ivQr` are removed entirely.

---

### 4.6 SessionDetailActivity (new)

**File:** `ui/SessionDetailActivity.java`
**Layout:** `activity_session_detail.xml`
**Purpose:** Shows live session metadata, peer fingerprint, ratchet statistics, and provides a "Destroy session" action. Reached from the `ChatActivity` toolbar.

**What it shows:**

```
┌─────────────────────────────────────┐
│  ← Session Details                  │
├─────────────────────────────────────┤
│  PEER IDENTITY                      │
│  [Full formatted fingerprint]       │  ← KeyFingerprint output, full 64 chars
│  [Compare with peer button]         │
├─────────────────────────────────────┤
│  SESSION                            │
│  Started: 14 minutes ago            │  ← from SessionState.sessionStartTime
│  Messages sent:     12              │  ← from DoubleRatchet.getSendMessageNumber()
│  Messages received: 8               │  ← from DoubleRatchet.getReceiveMessageNumber()
│  Ratchet steps:     3               │  ← tracked in ChatViewModel
├─────────────────────────────────────┤
│  YOUR IDENTITY                      │
│  [Your fingerprint]                 │  ← IdentityKeyManager.getFingerprint()
│  [Copy] [Share]                     │
├─────────────────────────────────────┤
│  [    Destroy session    ]          │  ← red destructive button
│  "This will delete all keys and     │
│   message history for this session" │
└─────────────────────────────────────┘
```

**What this activates:**
- `DoubleRatchet.getSendMessageNumber()` / `getReceiveMessageNumber()` — currently have public getters that are never called from UI.
- `SessionState.sessionStartTime` — recorded but never shown anywhere.
- `IdentityVerification.computeFingerprint()` — called to show the full peer fingerprint on this screen.
- `SessionDatabase` — on "Destroy session", calls `sessionDb.deleteSession(peerId)` and `SessionManager.destroySession(peerId)`.
- `utils.SessionManager.endSession()` — the `endSession()` method calls `keyManager.wipe()` and `encryptionManager.wipe()`. While the legacy `EncryptionManager` will be deleted (Issue #34), this method's intent is right. Wire it to call `SessionManager.INSTANCE.destroySession(peerId)` and clear any persisted session data.

**Destroy session flow:**

```java
private void destroySession() {
    new AlertDialog.Builder(this)
        .setTitle("Destroy session?")
        .setMessage("All keys and message history will be deleted. This cannot be undone.")
        .setPositiveButton("Destroy", (d, w) -> {
            SessionManager.INSTANCE.destroySession(peerId);
            sessionDatabase.deleteSession(peerId);
            messageDatabase.deleteConversation(peerId);
            // Signal ChatActivity to finish
            setResult(RESULT_OK);
            finish();
        })
        .setNegativeButton("Cancel", null)
        .show();
}
```

`ChatActivity` starts `SessionDetailActivity` with `startActivityForResult()` and calls `finish()` on itself if result is `RESULT_OK`.

**Intent in:**

```java
// From ChatActivity toolbar click
Intent intent = new Intent(this, SessionDetailActivity.class);
intent.putExtra("PEER_ID", peerId);
intent.putExtra("SESSION_START_TIME", session.getSessionStartTime());
startActivityForResult(intent, REQUEST_SESSION_DETAIL);
```

**AndroidManifest entry:**

```xml
<activity android:name=".ui.SessionDetailActivity" />
```

---

## 5. MVVM Layer Definitions

---

### 5.1 ChatViewModel

**File:** `ui/ChatViewModel.kt`
**Extends:** `androidx.lifecycle.ViewModel`

This is the single most impactful new class. It owns everything that `ChatActivity` currently does wrong — ratchet access, message serialisation, connection state observation, and TTL-aware send.

```kotlin
class ChatViewModel(
    private val connectionRepository: ConnectionRepository,
    private val sessionRepository: SessionRepository,
    private val peerId: String
) : ViewModel() {

    // Observed by ChatActivity
    val messages: MutableLiveData<List<ChatMessage>> = MutableLiveData(emptyList())
    val connectionState: LiveData<ConnectionState> get() = connectionRepository.connectionState

    private val session: SessionState
        get() = SessionManager.getSession(peerId)
            ?: error("No session for peer $peerId")

    // Called by ChatActivity send button
    fun send(text: String, ttlSeconds: Long = 0L) {
        val plaintext = text.toByteArray(Charsets.UTF_8)
        val ratchet = session.ratchet
        val encrypted = ratchet.encrypt(plaintext)

        val header = RatchetHeader(
            encrypted.header.dhPublicKey,
            encrypted.header.messageNumber,
            encrypted.header.previousChainLength
        )
        val metadata = MessageMetadata(
            senderId = selfId,
            messageId = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            ttlSeconds = ttlSeconds   // ← TTL is now set from UI selection
        )
        val packet = MessagePacket(header, encrypted.iv, encrypted.ciphertext, metadata)
        connectionRepository.send(packet)

        // Persist to MessageDatabase
        sessionRepository.storeMessage(
            messageId = metadata.messageId,
            peerId = peerId,
            ciphertext = encrypted.ciphertext,
            timestamp = metadata.timestamp,
            ttlSeconds = ttlSeconds
        )

        addMessageToUI(ChatMessage(text = text, isSelf = true, ttlSeconds = ttlSeconds,
            timestamp = metadata.timestamp))
    }

    // Called by ConnectionRepository when raw bytes arrive
    fun onRawMessageReceived(rawData: ByteArray) {
        val ratchet = session.ratchet
        val packet = SerializationUtils.deserialize(rawData)

        val ratchetKeyId = packet.header.dhPublicKey.joinToString(":")
        if (!SessionManager.validateMessage(peerId, ratchetKeyId, packet.header.messageNumber)) {
            Logger.e("Replay detected, dropping message")
            return
        }

        val encrypted = DoubleRatchet.EncryptedMessage(
            header = DoubleRatchet.Header(
                packet.header.dhPublicKey,
                packet.header.messageNumber,
                packet.header.previousChainLength
            ),
            iv = packet.iv,
            ciphertext = packet.ciphertext
        )

        val plaintext = ratchet.decrypt(encrypted)
        val text = String(plaintext, Charsets.UTF_8)

        addMessageToUI(ChatMessage(text = text, isSelf = false,
            ttlSeconds = packet.metadata.ttlSeconds, timestamp = packet.metadata.timestamp))
    }

    fun destroySession() {
        SessionManager.destroySession(peerId)
        sessionRepository.deleteSession(peerId)
        connectionRepository.disconnect()
    }

    private fun addMessageToUI(msg: ChatMessage) {
        val current = messages.value.orEmpty().toMutableList()
        current.add(msg)
        messages.postValue(current)
    }

    data class ChatMessage(
        val text: String,
        val isSelf: Boolean,
        val ttlSeconds: Long,
        val timestamp: Long
    )
}
```

**Factory** (required because ViewModel has constructor args):

```kotlin
class ChatViewModelFactory(
    private val connectionRepository: ConnectionRepository,
    private val sessionRepository: SessionRepository,
    private val peerId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return ChatViewModel(connectionRepository, sessionRepository, peerId) as T
    }
}
```

---

### 5.2 ConnectionRepository

**File:** `network/ConnectionRepository.kt`

Wraps `ConnectionHandler` and exposes `LiveData<ConnectionState>` to the ViewModel. Owns the reconnection logic (Issue #23).

```kotlin
class ConnectionRepository(
    private val connectionHandler: ConnectionHandler
) {
    val connectionState: MutableLiveData<ConnectionState> =
        MutableLiveData(ConnectionState.IDLE)

    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = longArrayOf(1000, 2000, 4000, 8000, 16000)

    var onRawMessageReceived: ((ByteArray) -> Unit)? = null

    fun startServer() {
        connectionState.postValue(ConnectionState.CONNECTING)
        connectionHandler.setListener(listener)
        connectionHandler.startServer()
    }

    fun connectToHost(hostIp: String) {
        connectionState.postValue(ConnectionState.CONNECTING)
        connectionHandler.setListener(listener)
        connectionHandler.connectToHost(hostIp)
    }

    fun send(packet: MessagePacket) {
        MessageSender(connectionHandler).send(packet)
    }

    fun disconnect() {
        connectionHandler.close()
        connectionState.postValue(ConnectionState.DISCONNECTED)
    }

    private val listener = object : ConnectionHandler.MessageReceiveListener {
        override fun onConnected() {
            reconnectAttempts = 0
            connectionState.postValue(ConnectionState.CONNECTED)
        }

        override fun onMessageReceived(rawData: ByteArray) {
            onRawMessageReceived?.invoke(rawData)
        }

        override fun onConnectionLost() {
            connectionState.postValue(ConnectionState.DISCONNECTED)
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            connectionState.postValue(ConnectionState.FAILED)
            return
        }
        val delay = reconnectDelayMs[reconnectAttempts]
        reconnectAttempts++
        connectionState.postValue(ConnectionState.RECONNECTING)
        Handler(Looper.getMainLooper()).postDelayed({
            // Re-use same handler — ConnectionHandler will retry same host
            connectionHandler.connectToHost(lastHostIp)
        }, delay)
    }
}

enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, FAILED
}
```

---

### 5.3 HandshakeManager

**File:** `Protocol/handshake/HandshakeManager.kt`

Consolidates the QR payload generation and handshake execution that currently lives in `ChatActivity`. Called from `HandshakeActivity`.

```kotlin
object HandshakeManager {

    data class HandshakeSetup(
        val qrPayload: String,
        val qrBitmap: Bitmap,
        val localEphemeralKeyPair: KeyPair
    )

    data class HandshakeResult(
        val ratchet: DoubleRatchet,
        val peerId: String,
        val peerIdentityKeyBytes: ByteArray,
        val peerFingerprint: String
    )

    fun prepareQR(selfId: String): HandshakeSetup {
        val identityKey = IdentityKeyManager.getPublicKeyBytes()
        val ephemeralKeyPair = DiffieHellmanHandshake.generateEphemeralKeyPair()
        val ephemeralKey = ephemeralKeyPair.public.encoded
        val payload = QRCodeGenerator.createIdentityPayload(selfId, identityKey, ephemeralKey)
        val bitmap = QRCodeGenerator.generateQRCode(payload)
        return HandshakeSetup(payload, bitmap, ephemeralKeyPair)
    }

    fun executeAsInitiator(scannedPayload: String): HandshakeResult {
        val result = QRHandshake.performHandshake(scannedPayload)
        val parsed = QRCodeParser.parse(scannedPayload)
        val fingerprint = KeyFingerprint.generate(parsed.identityKey)
        return HandshakeResult(
            ratchet = result.ratchet,
            peerId = result.remoteUserId,
            peerIdentityKeyBytes = parsed.identityKey,
            peerFingerprint = fingerprint
        )
    }

    fun executeAsResponder(
        scannedPayload: String,
        localEphemeralKeyPair: KeyPair
    ): HandshakeResult {
        val result = QRHandshake.performHandshakeAsResponder(scannedPayload, localEphemeralKeyPair)
        val parsed = QRCodeParser.parse(scannedPayload)
        val fingerprint = KeyFingerprint.generate(parsed.identityKey)
        return HandshakeResult(
            ratchet = result.ratchet,
            peerId = result.remoteUserId,
            peerIdentityKeyBytes = parsed.identityKey,
            peerFingerprint = fingerprint
        )
    }
}
```

---

### 5.4 SessionRepository

**File:** `session/SessionRepository.kt`

Wraps `SessionDatabase` and `MessageDatabase` — giving them their first real callers. Everything that currently never writes to or reads from storage now goes through here.

```kotlin
class SessionRepository(
    private val sessionDatabase: SessionDatabase,
    private val messageDatabase: MessageDatabase
) {

    fun saveSession(peerId: String, identityKey: ByteArray, startTime: Long) {
        val ratchetState = SessionManager.getSession(peerId)
            ?.let { RatchetState.fromRatchet(it.ratchet).encode() }
            ?.let { SerializationUtils.serializeMap(it) }
            ?: ByteArray(0)

        sessionDatabase.saveSession(peerId, identityKey, ratchetState, startTime)
    }

    fun loadSession(peerId: String): SessionDatabase.StoredSession? {
        return sessionDatabase.getSession(peerId)
    }

    fun deleteSession(peerId: String) {
        sessionDatabase.deleteSession(peerId)
        messageDatabase.deleteConversation(peerId)
    }

    fun storeMessage(
        messageId: String,
        peerId: String,
        ciphertext: ByteArray,
        timestamp: Long,
        ttlSeconds: Long
    ) {
        messageDatabase.storeMessage(messageId, peerId, ciphertext, timestamp, ttlSeconds)
    }

    fun getMessagesForPeer(peerId: String): List<MessageDatabase.StoredMessage> {
        return messageDatabase.getMessagesForPeer(peerId)
    }
}
```

---

## 6. Activating Every Unused Feature

This section maps each dead-code item from §1 to exactly where and how it gets activated.

---

### 6.1 MessageTTLManager — ephemeral messages

**Where activated:** `SentinelChatApp.java`

```java
@Override
public void onCreate() {
    super.onCreate();
    Logger.init(this);
    IdentityKeyManager.INSTANCE.initialize(this);

    // Activate the TTL manager — previously dead code
    MessageTTLManager ttlManager = new MessageTTLManager(
        new MessageDatabase(),
        new SessionDatabase()
    );
    ttlManager.start();   // ← start() was never called before this
}
```

The `MessageTTLManager` runs a cleanup task every 30 seconds. Messages whose `timestamp + ttlSeconds * 1000 < now` are deleted from `MessageDatabase`. Sessions older than 24 hours are deleted from `SessionDatabase`.

**Required companion fix:** The TTL passed to `MessageMetadata` must be non-zero. It comes from the TTL selector in `ChatActivity` (§4.5). `ChatViewModel.send(text, ttlSeconds)` passes the selected value. Default is `0` (no expiry) which the cleanup task ignores.

---

### 6.2 IdentityVerification — TOFU key change detection

**Where activated:** `HandshakeActivity`, Phase 2 (§4.4)

```java
// In HandshakeActivity, after handshake completes:
SessionDatabase.StoredSession previousSession =
    sessionRepository.loadSession(peerId);

if (previousSession != null) {
    boolean keyUnchanged = IdentityVerification.INSTANCE
        .verifyRemoteIdentity(
            DiffieHellmanHandshake.INSTANCE.decodePublicKey(remoteIdentityKeyBytes),
            KeyFingerprint.INSTANCE.generate(previousSession.getIdentityKey())
        );

    if (!keyUnchanged) {
        showKeyChangedWarning(
            KeyFingerprint.INSTANCE.generate(previousSession.getIdentityKey()),
            KeyFingerprint.INSTANCE.generate(remoteIdentityKeyBytes)
        );
        return;  // Don't proceed until user acknowledges
    }
}
```

`IdentityVerification.verifyRemoteIdentity()` and `areSameKey()` — both currently never called — are now the gatekeepers for session continuity.

---

### 6.3 KeyFingerprint — safety number screen

**Where activated:** Three places

1. `IdentityActivity` — local fingerprint display (§4.2)
2. `HandshakeActivity` Phase 2 — peer fingerprint display side-by-side for voice verification (§4.4)
3. `SessionDetailActivity` — full fingerprint for both parties (§4.6)

`KeyFingerprint.generate(publicKey)` and `KeyFingerprint.matches()` are the core of this feature. The `formatFingerprint()` output (hex chunked in groups of 4 with dashes) is what users read aloud to each other. This is the Safety Numbers feature — well understood in the industry (Signal uses it) and now finally surfaced.

---

### 6.4 ReplayProtection — visible security indicator

**Where activated:** `ChatActivity` connection status bar (§4.5) and `SessionDetailActivity` (§4.6)

The replay protection does its job silently. To make it visible, add a small indicator in the `ChatActivity` status bar:

```
🔒 Connected · 24 msgs · 3 ratchet steps · replay protection active
```

When a replay is detected (the `Logger.e("Replay detected")` path in `ChatViewModel.onRawMessageReceived()`), the status bar briefly shows:

```
⚠ Replay attempt blocked
```

This makes the security property observable without being alarmist.

---

### 6.5 SessionDatabase — persistent session resumption

**Where activated:** `SessionRepository.saveSession()` / `loadSession()`

Called from:
- `HandshakeActivity.onHandshakeVerified()` — saves the new session (identity key + start time + serialised ratchet state via `RatchetState.fromRatchet()`)
- `HandshakeActivity` TOFU check — loads previous session identity key for comparison
- `SessionDetailActivity.destroySession()` — deletes the session record
- `SplashActivity` — reads all sessions to check if any persisted sessions exist (future: "Resume session" flow)

`RatchetState.encode()` and `RatchetState.decode()` — the full serialisation implementation that currently has no callers — are called here to persist and restore ratchet state across app restarts.

> **Security note:** `RatchetState` stores ephemeral private key bytes. The `SessionDatabase` is currently in-memory (`ConcurrentHashMap`). If persistence to disk is added later, the stored bytes must be encrypted with a key derived from the AndroidKeyStore identity key before writing to `SharedPreferences` or a file. Mark this as a TODO in the implementation.

---

### 6.6 MessageMetadata.ttlSeconds — per-message expiry UI

**Where activated:** `ChatActivity` TTL selector → `ChatViewModel.send(text, ttlSeconds)` → `MessageMetadata` constructor

The `ttlSeconds` field in `MessageMetadata` was always defined — just always set to `0`. The TTL selector in `ChatActivity` (§4.5) is the UI that produces a non-zero value. The `MessageAdapter` reads the field from each `ChatMessage` and conditionally renders the expiry countdown.

The receiver also reads `packet.metadata.ttlSeconds` from the deserialized packet. If non-zero, the `MessageTTLManager` will expire the stored ciphertext at `timestamp + ttlSeconds * 1000`. The plaintext is only held in the `ChatViewModel.messages` LiveData in memory — it is not re-decrypted from storage after expiry.

---

### 6.7 PeerDiscovery — peer list with status

**Where activated:** `DeviceDiscoveryActivity` (§4.3)

`PeerDiscovery.updatePeers()`, `getPeers()`, `findPeerByAddress()`, and `clearPeers()` are called from `DeviceDiscoveryActivity`:

- `onPeersAvailable()` → `peerDiscovery.updatePeers(deviceList)`
- `adapter.updatePeers(peerDiscovery.getPeers())`
- `onPeerClicked(device)` → calls `peerDiscovery.findPeerByAddress(device.deviceAddress)` to verify before connecting

The `PeerAdapter` can show each peer's connection status (from `WifiP2pDevice.status`) as a subtitle: `AVAILABLE`, `CONNECTED`, `INVITED`, `FAILED`, `UNAVAILABLE`.

---

### 6.8 ConnectionState enum — connection status bar

**Where activated:** `ConnectionRepository` (§5.2) produces it. `ChatActivity` status bar (§4.5) and `DeviceDiscoveryActivity` (§4.3) consume it.

The `ConnectionState` enum that only exists in a TODO comment in `WifiDirectManager` becomes a real class in `ConnectionRepository`:

```kotlin
enum class ConnectionState {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    FAILED
}
```

`ConnectionRepository.connectionState: LiveData<ConnectionState>` is observed by `ChatViewModel`, which exposes it to `ChatActivity`. The status bar (§4.5) maps each state to a color and message string.

---

## 7. AndroidManifest Changes

```xml
<!-- NEW: SplashActivity becomes the launcher entry point -->
<activity
    android:name=".ui.SplashActivity"
    android:exported="true"
    android:theme="@style/Theme.MaterialComponents.DayNight.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- NEW: IdentityActivity — first-launch only -->
<activity android:name=".ui.IdentityActivity" />

<!-- EXISTING: Remove MAIN/LAUNCHER from StartSessionActivity -->
<!-- StartSessionActivity can be deleted once IdentityActivity and
     DeviceDiscoveryActivity cover both its buttons -->
<activity android:name=".ui.StartSessionActivity" />

<activity android:name=".ui.KeySetupActivity" />

<activity android:name=".ui.connection.DeviceDiscoveryActivity" />

<!-- NEW: HandshakeActivity — QR exchange + fingerprint verify -->
<activity android:name=".ui.HandshakeActivity" />

<activity android:name=".ui.ChatActivity" />

<!-- NEW: SessionDetailActivity — session info + destroy -->
<activity android:name=".ui.SessionDetailActivity" />
```

---

## 8. Integration Contract — How Every Piece Connects

This section is the single reference for what calls what. If a class is not listed here as a caller or callee, it is either dead code being deleted or a utility that is called transitively.

```
SentinelChatApp.onCreate()
    └── IdentityKeyManager.initialize()
    └── MessageTTLManager.start()              ← ACTIVATED (was dead)
            └── MessageDatabase.getAllMessages()   ← ACTIVATED (was dead)
            └── SessionDatabase.getAllSessions()   ← ACTIVATED (was dead)

SplashActivity
    └── reads SharedPreferences "identity_confirmed"
    └── reads SessionDatabase (for future resume-session feature)
    └── routes to IdentityActivity OR DeviceDiscoveryActivity

IdentityActivity
    └── IdentityKeyManager.getFingerprint()
    └── KeyFingerprint.generate()              ← ACTIVATED (was dead)
    └── writes SharedPreferences "identity_confirmed"
    └── navigates to DeviceDiscoveryActivity

DeviceDiscoveryActivity
    └── WifiDirectManager (existing)
    └── PeerDiscovery.updatePeers()            ← ACTIVATED (was dead)
    └── PeerDiscovery.getPeers()               ← ACTIVATED (was dead)
    └── navigates to HandshakeActivity (was ChatActivity)

HandshakeActivity
    └── HandshakeManager.prepareQR()
            └── IdentityKeyManager.getPublicKeyBytes()
            └── DiffieHellmanHandshake.generateEphemeralKeyPair()
            └── QRCodeGenerator.createIdentityPayload()
            └── QRCodeGenerator.generateQRCode()
    └── QRCodeScanner (CameraX)
    └── HandshakeManager.executeAsInitiator() OR executeAsResponder()
            └── QRHandshake.performHandshake() OR performHandshakeAsResponder()
            └── QRCodeParser.parse()
            └── KeyFingerprint.generate(peerKey)   ← ACTIVATED (was dead)
    └── IdentityVerification.verifyRemoteIdentity() ← ACTIVATED (was dead)
    └── SessionRepository.loadSession()            ← ACTIVATED (was dead)
    └── SessionManager.createSession()
    └── SessionRepository.saveSession()            ← ACTIVATED (was dead)
            └── RatchetState.fromRatchet().encode() ← ACTIVATED (was dead)
            └── SessionDatabase.saveSession()       ← ACTIVATED (was dead)
    └── navigates to ChatActivity

ChatActivity
    └── ChatViewModel (observe messages, connectionState)
    └── TTL selector → ChatViewModel.send(text, ttlSeconds)
    └── toolbar → navigates to SessionDetailActivity

ChatViewModel
    └── SessionManager.getSession()
    └── DoubleRatchet.encrypt()
    └── SessionManager.validateMessage(peerId, ratchetKeyId, msgNum) ← FIX #33
            └── SessionState.isReplay(ratchetKeyId, msgNum)
                    └── ReplayProtection.isReplay()  ← NOW CORRECTLY KEYED
    └── DoubleRatchet.decrypt()
    └── MessageSender → ConnectionHandler
    └── MessageDatabase.storeMessage()            ← ACTIVATED (was dead)
    └── MessageMetadata with real ttlSeconds       ← ACTIVATED (was zero)
    └── ConnectionRepository.connectionState (LiveData)

ConnectionRepository
    └── ConnectionHandler (existing)
    └── ConnectionState enum                       ← ACTIVATED (was a TODO)
    └── reconnect with exponential backoff         ← ACTIVATED (was a TODO)

SessionDetailActivity
    └── IdentityVerification.computeFingerprint()  ← ACTIVATED (was dead)
    └── DoubleRatchet.getSendMessageNumber()        ← ACTIVATED (was dead)
    └── DoubleRatchet.getReceiveMessageNumber()     ← ACTIVATED (was dead)
    └── SessionState.sessionStartTime               ← ACTIVATED (was dead)
    └── SessionRepository.deleteSession()           ← ACTIVATED (was dead)
            └── SessionDatabase.deleteSession()     ← ACTIVATED (was dead)
            └── MessageDatabase.deleteConversation() ← ACTIVATED (was dead)
```

**Classes to delete after refactor:**
- `ui/StartSessionActivity.java` — replaced by `SplashActivity` + role selector in `DeviceDiscoveryActivity`
- `ui/KeySetupActivity.java` — replaced by `IdentityActivity`
- `crypto/EncryptionManager.java` — orphaned static-key cipher (Issue #34)
- `utils/SessionManager.java` — legacy duplicate, replaced by `session/SessionManager.kt`

**Classes to migrate to Kotlin:**
- `messaging/models/Message.java` → `data class Message(...)` (Issue #20)
- `messaging/models/Peer.java` → `data class Peer(...)` (Issue #21)
- `messaging/models/Session.java` → `data class Session(...)` (Issue #21)

---

## 9. Master Prompt — Implement This Refactor

Use this prompt verbatim with the codebase in context.

---

You are an expert Android engineer performing a full architecture refactor of SentinalChat, a WiFi Direct P2P encrypted messaging app. The crypto core is correct and must not be touched. You are restructuring the application layer only.

**Read this entire document before writing any code.** The specification is complete — do not invent features, do not add dependencies not already in `build.gradle`, do not modify any file under `crypto/`, `Protocol/`, or `security/` except to call them from new locations.

---

**Phase 0 — Create the ConnectionState enum**

Create `network/ConnectionState.kt`:

```kotlin
enum class ConnectionState {
    IDLE, CONNECTING, CONNECTED, RECONNECTING, DISCONNECTED, FAILED
}
```

---

**Phase 1 — Create ConnectionRepository**

Create `network/ConnectionRepository.kt` exactly as specified in §5.2. It wraps `ConnectionHandler`, exposes `MutableLiveData<ConnectionState>`, owns the reconnect-with-backoff logic (max 5 attempts, delays: 1s/2s/4s/8s/16s), and provides `onRawMessageReceived: ((ByteArray) -> Unit)?` as a callback hook for the ViewModel.

---

**Phase 2 — Create SessionRepository**

Create `session/SessionRepository.kt` exactly as specified in §5.4. It wraps `SessionDatabase` and `MessageDatabase`. It calls `RatchetState.fromRatchet(ratchet).encode()` when saving a session and `RatchetState.decode(map)` when restoring one. Add a TODO comment on the `saveSession()` method noting that ratchet state bytes must be encrypted before any disk persistence is added.

---

**Phase 3 — Create HandshakeManager**

Create `Protocol/handshake/HandshakeManager.kt` exactly as specified in §5.3. It exposes `prepareQR()`, `executeAsInitiator()`, and `executeAsResponder()`. It calls `QRHandshake.performHandshake()` and `QRHandshake.performHandshakeAsResponder()` respectively. It calls `KeyFingerprint.generate()` on the parsed remote identity key to produce the peer fingerprint string.

---

**Phase 4 — Create ChatViewModel and ChatViewModelFactory**

Create `ui/ChatViewModel.kt` and `ui/ChatViewModelFactory.kt` exactly as specified in §5.1. The ViewModel must:
- Hold no reference to any Activity or Context
- Observe `ConnectionRepository.connectionState` passively (do not re-expose via a wrapper — return the `LiveData` directly)
- Call `SessionManager.validateMessage(peerId, ratchetKeyId, messageNumber)` using the two-arg ratchet key ID form (Issue #33 fix)
- Accept `ttlSeconds: Long = 0L` in `send()` and pass it to `MessageMetadata`
- Call `MessageDatabase.storeMessage()` on every outgoing message
- Post decrypted incoming text to `messages: MutableLiveData<List<ChatMessage>>`

---

**Phase 5 — Create SplashActivity**

Create `ui/SplashActivity.java` exactly as specified in §4.1. It reads `"identity_confirmed"` from `getPreferences(MODE_PRIVATE)`. It routes to `IdentityActivity` (false) or `DeviceDiscoveryActivity` (true) after a 1200ms delay. Add it to `AndroidManifest.xml` as the MAIN/LAUNCHER entry point, removing that designation from `StartSessionActivity`.

---

**Phase 6 — Create IdentityActivity**

Create `ui/IdentityActivity.java` exactly as specified in §4.2. It calls `IdentityKeyManager.INSTANCE.getFingerprint()` and displays the result. It provides Copy (ClipboardManager) and Share (ACTION_SEND intent) buttons. On Continue it sets `"identity_confirmed" = true` and navigates to `DeviceDiscoveryActivity`. Create `activity_identity.xml` with the layout described.

---

**Phase 7 — Create HandshakeActivity**

Create `ui/HandshakeActivity.java` exactly as specified in §4.4. It has two phases: QR_EXCHANGE and FINGERPRINT_VERIFY. In QR_EXCHANGE it displays the local QR (from `HandshakeManager.prepareQR()`) in the top half and runs `QRCodeScanner` in the bottom half. When a QR is detected it calls `HandshakeManager.executeAsInitiator()` or `executeAsResponder()` based on `IS_HOST`. In FINGERPRINT_VERIFY it shows both fingerprints side by side and waits for user confirmation or rejection. On confirmation it calls `SessionRepository.saveSession()` and `SessionManager.createSession()` before navigating to `ChatActivity`. On rejection it calls `SessionManager.destroySession()` and finishes. It also performs the TOFU check via `IdentityVerification.verifyRemoteIdentity()` before entering FINGERPRINT_VERIFY. Create `activity_handshake.xml`.

---

**Phase 8 — Rewrite ChatActivity**

Rewrite `ui/ChatActivity.java` as specified in §4.5. Remove all QR, handshake, and direct crypto code. Wire to `ChatViewModel` via `ViewModelProvider`. Observe `chatViewModel.messages` and `chatViewModel.connectionState`. Implement the TTL selector (bottom sheet with 5 options: 0/5min/1hr/24hr/on-read mapped to `0/300/3600/86400/-1` seconds). Add the connection status bar at the top. Add the toolbar with peer fingerprint (first 9 chars of fingerprint + "...") that navigates to `SessionDetailActivity` via `startActivityForResult()`. In `onActivityResult()` call `finish()` if result is `RESULT_OK` (session destroyed). Remove `PreviewView` and `ivQr` from the layout. Create the new `activity_chat.xml`.

---

**Phase 9 — Create SessionDetailActivity**

Create `ui/SessionDetailActivity.java` exactly as specified in §4.6. It shows: peer full fingerprint (from `KeyFingerprint.generate(peerIdentityKeyBytes)`), session age (computed from `SESSION_START_TIME` Intent extra), messages sent/received (from `SessionManager.getSession(peerId).ratchet.getSendMessageNumber()` and `getReceiveMessageNumber()`), local fingerprint with Copy/Share, and a Destroy button. The Destroy button shows a confirmation dialog then calls `SessionManager.destroySession(peerId)`, `sessionRepository.deleteSession(peerId)`, sets `RESULT_OK`, and finishes. Create `activity_session_detail.xml`.

---

**Phase 10 — Extend DeviceDiscoveryActivity**

Modify `ui/connection/DeviceDiscoveryActivity.java` as specified in §4.3:
- Add a `ToggleButton` or `TabLayout` at the top for Host/Join role selection (removing the `IS_HOST` Intent extra dependency — role is now chosen here)
- Instantiate `PeerDiscovery peerDiscovery = new PeerDiscovery()` as a field and route all peer list updates through it
- Set `config.groupOwnerIntent = isHost ? 15 : 0` in `connectToPeer()`
- Change `onConnected()` to navigate to `HandshakeActivity` instead of `ChatActivity`
- Add an empty-state TextView when the peer list is empty

---

**Phase 11 — Wire MessageTTLManager**

In `SentinelChatApp.java`, after `IdentityKeyManager.INSTANCE.initialize(this)`, add:

```java
MessageTTLManager ttlManager = new MessageTTLManager(
    new MessageDatabase(),
    new SessionDatabase()
);
ttlManager.start();
```

---

**Phase 12 — Delete dead code**

Delete the following files:
- `ui/StartSessionActivity.java` and `activity_start_session.xml`
- `ui/KeySetupActivity.java` and `activity_key_setup.xml`
- `crypto/EncryptionManager.java`
- Remove `setEncryptionManager()` and `getEncryptionManager()` from `utils/SessionManager.java`
- Remove `utils/SessionManager.java` itself once no other file imports it (verify before deleting)

---

**Constraints — do not violate these:**

- Do not modify any file under `crypto/`, `Protocol/Ratchet/`, `security/`, or `identity/` except to call their public APIs from new locations.
- Do not add any new Gradle dependencies.
- Every new Activity must be registered in `AndroidManifest.xml`.
- Every new ViewModel must be instantiated via a `ViewModelProvider.Factory`.
- `ChatActivity` must not hold a direct reference to `DoubleRatchet`, `ConnectionHandler`, or `SessionManager` — all access goes through `ChatViewModel`.
- `HandshakeActivity` may hold direct references to these classes because it is performing the protocol setup, not the ongoing conversation.
- The `ttlSeconds = -1` sentinel for "on read" expiry is a UI convenience — the `MessageTTLManager` treats it as `ttlSeconds = 0` (no scheduled expiry). The actual delete-on-read behaviour requires a `MessageReceiver` hook that is out of scope for this refactor; add a `// TODO` comment.

---

**Verification checklist — confirm before finishing:**

1. `ChatActivity` contains zero imports from `Protocol/`, `crypto/`, or `security/` packages.
2. `HandshakeActivity` is the only Activity that calls `QRHandshake` or `HandshakeManager`.
3. `MessageTTLManager.start()` is called exactly once, from `SentinelChatApp.onCreate()`.
4. `PeerDiscovery.updatePeers()` is called in `DeviceDiscoveryActivity.onPeersAvailable()`.
5. `SessionDatabase.saveSession()` is called in `HandshakeActivity.onHandshakeVerified()`.
6. `MessageDatabase.storeMessage()` is called in `ChatViewModel.send()`.
7. `KeyFingerprint.generate()` is called in at least three places: `IdentityActivity`, `HandshakeActivity` Phase 2, and `SessionDetailActivity`.
8. `IdentityVerification.verifyRemoteIdentity()` is called in `HandshakeActivity` for the TOFU check.
9. `SessionManager.validateMessage()` takes three arguments including `ratchetKeyId`.
10. `SplashActivity` is the `MAIN`/`LAUNCHER` entry point in `AndroidManifest.xml`.
11. `EncryptionManager.java` does not exist in the final codebase.
12. `utils/SessionManager.java` does not exist in the final codebase (or is renamed `LegacySessionManager` if still referenced).
13. All six new/modified Activities are registered in `AndroidManifest.xml`.
14. The project compiles with no new `@SuppressWarnings` annotations added to pass compilation.
