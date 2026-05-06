# NanR3

NanR3 is an Android Wi-Fi Aware (NAN) demo app for discovering nearby peers, automatically creating a Wi-Fi Aware Network Data Path (NDP), and transferring user-selected files between devices.

## Requirements

- Android 8.0+ for Wi-Fi Aware APIs.
- Android 10+ is recommended for the current NDP builder flow.
- Two devices with `android.hardware.wifi.aware` support.
- Location and nearby Wi-Fi permissions enabled when prompted.

Use the in-app `Capabilities` button to check whether the current phone supports Wi-Fi Aware, how many NDP sessions/data interfaces it reports, supported cipher suites, and pairing-related capability fields when available on the platform.

## App Identity

- App name: `NanR3`
- Application ID: `net.mobilewebprint.nanr3`
- Java namespace: `net.mobilewebprint.nan`

## Basic Flow

1. Install NanR3 on both phones.
2. On the sender phone, tap `Publish`.
3. On the receiver phone, tap `Subscribe`.
4. The app discovers the peer and starts NDP automatically.
5. Tap `Send File` on the sender, choose a file from the system picker, and wait for transfer completion.

Received files are saved under:

```text
Downloads/NanR3/
```

## Controls

- `Publish`: Starts Wi-Fi Aware publish mode and automatically prepares NDP when a peer is found.
- `Subscribe`: Starts Wi-Fi Aware subscribe mode and automatically prepares NDP when a peer is found.
- `Capabilities`: Shows the current device's Wi-Fi Aware capability report.
- `Send File`: Opens the Android file picker and sends the selected file to the peer.
- Floating message button: Sends the text in the message field over the active discovery session.

## Debugging

Use Logcat filters:

```text
myNanR3.File|myNanR3.NAN
```

Important file-transfer logs include:

- File picker launch and selected URI.
- Peer IPv6 and file port readiness.
- Sender socket connection.
- Sender file header: name, size, MIME type.
- Receiver server bind port.
- Receiver socket accept.
- Receiver file header and save URI/path.
- Sender and receiver progress.
- Exception stack traces for send/receive failures.

## NDP and File Transfer Flow

### Phase 1: Wi-Fi Aware Discovery & NDP Establishment

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Publisher (Sender)                                  │
│                                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌────────────┐  │
│  │   Publish    │───▶│ attach()     │───▶│ NAN interface│    │            │  │
│  │   Button     │    │ starts       │    │ brought up   │    │            │  │
│  └─────────────┘    └──────┬───────┘    └──────┬───────┘    │            │  │
│                            │                     │            │            │  │
│                            │ onAttached()        │            │            │  │
│                            ▼                     │            │            │  │
│                     ┌─────────────┐             │            │            │  │
│                     │ startServer()│             │            │            │  │
│                     │ (file recv)  │             │            │            │  │
│                     └──────┬───────┘             │            │            │  │
│                            │                     │            │            │  │
│                            │    ┌─────────────────────────────────┐       │  │
│                            │    │ onServiceDiscovered(peerHandle) │       │  │
│                            │    │   OR onMessageReceived()        │       │  │
│                            │    │   (subscriber found us)        │       │  │
│                            │    └─────────────────────────────────┘       │  │
│                            │                     │            │            │  │
│                            │ onIdentityChanged() │            │            │  │
│                            │◀────────────────────┘            │            │  │
│                            │                                  │            │  │
│                            │ sendMessage(MAC, port) ──────────┼───────────▶│  │
│                            │                                  │            │  │
│                            │◀─────────────────────────────── │            │  │
│                            │      sendMessage(other MAC,     │            │  │
│                            │             other IPv6, port)   │            │  │
│                            │                                  │            │  │
│                            │         ┌──────────────────────────────────┐  │  │
│                            │         │ startResponderNdpIfReady()      │  │  │
│                            │         │ - create WifiAwareNetworkSpecifier│  │  │
│                            │         │ - requestNetwork()                │  │  │
│                            │         └──────────┬───────────────────────┘  │  │
│                            │                    │                           │  │
│                            │◀───────────────────┘                           │  │
│                            │       onCapabilitiesChanged()                   │  │
│                            │       (provides peerIpv6, peerPort)            │  │
│                            │                                                 │  │
│                            │◀───────────────────────────────────────────────│  │
│                            │           onLinkPropertiesChanged()             │  │
│                            │           (provides local IPv6, interface)       │  │
│                            │                                                 │  │
│                            │ sendMessage(myIPv6) ──────────────────────────▶│  │
│                            │                                                 │  │
└────────────────────────────┼────────────────────────────────────────────────┼──┘
                             │                                                 │
                             │            Wi-Fi Aware (NAN Cluster)             │
                             │                                                 │
                             │                                                 │
                             ▼                                                 ▼
┌────────────────────────────┼────────────────────────────────────────────────┼──┐
│                            │                                                 │  │
│                            │◀───────────────────────────────────────────────│  │
│                            │           sendMessage(myIPv6)                   │  │
│                            │                                                 │  │
│                            │         ┌──────────────────────────────────┐  │  │
│                            │         │ startInitiatorNdpIfReady()       │  │  │
│                            │         │ - create WifiAwareNetworkSpecifier│  │  │
│                            │         │ - requestNetwork()                │  │  │
│                            │         └──────────┬───────────────────────┘  │  │
│                            │                    │                           │  │
│                            │◀───────────────────┘                           │  │
│                            │       onCapabilitiesChanged()                   │  │
│                            │                                                 │  │
│                            │                                                 │  │
│  ┌─────────────┐    ┌──────┴───────┐    ┌─────────────┐    ┌────────────┐  │
│  │  Subscribe   │───▶│ attach()     │───▶│ NAN interface│    │            │  │
│  │   Button     │    │ starts       │    │ brought up   │    │            │  │
│  └─────────────┘    └──────┬───────┘    └──────┬───────┘    │            │  │
│                            │                     │            │            │  │
│                            │ onAttached()        │            │            │  │
│                            ▼                     │            │            │  │
│                     ┌─────────────┐             │            │            │  │
│                     │ startServer()│             │            │            │  │
│                     │ (file recv)  │             │            │            │  │
│                     └──────┬───────┘             │            │            │  │
│                            │                     │            │            │  │
│                            │    ┌─────────────────────────────────┐       │  │
│                            │    │ onServiceDiscovered(peerHandle) │       │  │
│                            │    │   (found publisher)            │       │  │
│                            │    └─────────────────────────────────┘       │  │
│                            │                     │            │            │  │
│                            │ onIdentityChanged() │            │            │  │
│                            │◀────────────────────┘            │            │  │
│                            │                                  │            │  │
│                            │ sendMessage(MAC, port) ──────────┼───────────▶│  │
│                            │                                  │            │  │
│                            │◀─────────────────────────────── │            │  │
│                            │      sendMessage(other MAC,     │            │  │
│                            │             other IPv6, port)   │            │  │
│                            │                                                 │  │
└────────────────────────────┼────────────────────────────────────────────────┼──┘
                             │
                             │
                    ┌────────▼────────┐
                    │   NDP Ready!    │
                    │   Both sides    │
                    │   can send files │
                    └─────────────────┘

```

### Phase 2: File Transfer

```
┌────────────────────────────────────────────────────────────────────────────────┐
│                        Publisher (Sender) Side                                 │
│                                                                                │
│   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐                   │
│   │  Send File    │───▶│   chooseFile  │───▶│ ACTION_OPEN  │                   │
│   │   Button      │    │   ForSend()  │    │ _DOCUMENT    │                   │
│   └──────────────┘    └──────────────┘    └──────┬───────┘                   │
│                                                   │                           │
│                                                   │ onActivityResult()        │
│                                                   ▼                           │
│                                            ┌──────────────┐                   │
│                                            │ getContent   │                   │
│                                            │ Resolver     │                   │
│                                            │ .openInput   │                   │
│                                            │ Stream()     │                   │
│                                            └──────┬───────┘                   │
│                                                   │                           │
│   ┌──────────────────────────────────────────────────────────────────────┐   │
│   │                     clientSendFile() - New Thread                     │   │
│   │                                                                       │   │
│   │   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐          │   │
│   │   │ new Socket() │───▶│ connect to    │───▶│ DataOutput   │          │   │
│   │   │ (serverIPv6, │    │ peerIpv6:    │    │ Stream       │          │   │
│   │   │  serverPort) │    │ peerPort     │    │              │          │   │
│   │   └──────────────┘    └──────────────┘    └──────┬───────┘          │   │
│   │                                                  │                    │   │
│   │   ┌────────────────────────────────────────────────────────────────┐ │   │
│   │   │ writeUTF(fileName)  ── file name                              │ │   │
│   │   │ writeLong(fileSize) ── file size in bytes                     │ │   │
│   │   │ writeUTF(mimeType)  ── MIME type                               │ │   │
│   │   └────────────────────────────────────────────────────────────────┘ │   │
│   │                                                  │                    │   │
│   │   ┌────────────────────────────────────────────────────────────────┐ │   │
│   │   │                    while ((count = in.read(buffer)) > 0)       │ │   │
│   │   │                        dos.write(buffer, 0, count)             │ │   │
│   │   │                    Progress updated every 1MB                   │ │   │
│   │   └────────────────────────────────────────────────────────────────┘ │   │
│   │                                                  │                    │   │
│   └──────────────────────────────────────────────────┼────────────────────┘   │
│                                                      │                        │
│                                                      │ TCP connection        │
└──────────────────────────────────────────────────────┼────────────────────────┘
                                                       │
                          Wi-Fi Aware NDP (IPv6)       │
                                                       │
┌──────────────────────────────────────────────────────┼────────────────────────┐
│                                                      │                        │
│   ┌──────────────────────────────────────────────────┼────────────────────┐   │
│   │                     receiver server             │                    │   │
│   │                     (startServer thread)        │                    │   │
│   │                                              ┌───▼────────┐          │   │
│   │                                              │ server     │          │   │
│   │   ┌──────────────────────────────────────────│ Socket()   │          │   │
│   │   │                                          │ accept()   │          │   │
│   │   │                                          └────┬───────┘          │   │
│   │   │                                               │                   │   │
│   │   │   ┌──────────────────────────────────────────▼───────────────┐  │   │
│   │   │   │               DataInputStream                       │  │   │
│   │   │   │  readUTF()  ──▶ fileName                           │  │   │
│   │   │   │  readLong()  ──▶ fileSizeInBytes                  │  │   │
│   │   │   │  readUTF()  ──▶ mimeType                           │  │   │
│   │   │   └──────────────────────────────────────────────────────┘  │   │
│   │   │                                                               │   │
│   │   │   ┌───────────────────────────────────────────────────────┐ │   │
│   │   │   │  MediaStore.Downloads.insert()                        │ │   │
│   │   │   │  → Uri for output stream                              │ │   │
│   │   │   └───────────────────────────────────────────────────────┘ │   │
│   │   │                                                               │   │
│   │   │   ┌───────────────────────────────────────────────────────┐ │   │
│   │   │   │          while ((read = in.read(buffer)) > 0)       │ │   │
│   │   │   │              fos.write(buffer, 0, read)              │ │   │
│   │   │   │          Progress updated every 1MB                   │ │   │
│   │   │   └───────────────────────────────────────────────────────┘ │   │
│   │   │                                                               │   │
│   └───┼───────────────────────────────────────────────────────────────┘   │
│       │                                                                   │
│   ┌───▼────────┐                                                         │
│   │ File saved  │                                                         │
│   │ to Downloads│                                                         │
│   │ /NanR3/    │                                                         │
│   └────────────┘                                                         │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │   File Saved!       │
                    │ Downloads/NanR3/    │
                    │    filename         │
                    └─────────────────────┘
```

### Message Type Summary

| Message Length | Type | Content |
|----------------|------|---------|
| 2 bytes | Port | File receiver server port |
| 6 bytes | MAC | Device MAC address |
| 16 bytes | IPv6 | Wi-Fi Aware IPv6 address |
| > 16 bytes | Message | Application message text |

### Key Code Points

- **Discovery**: `onServiceDiscovered()` callback provides `PeerHandle`
- **NDP Trigger**: `startResponderNdpIfReady()` / `startInitiatorNdpIfReady()` called at multiple points to handle async timing
- **Async Safety**: `initiatorNdpRequested` / `responderNdpRequested` flags prevent duplicate NDP requests
- **File Selection**: Uses `ACTION_OPEN_DOCUMENT` with `FLAG_GRANT_READ_URI_PERMISSION` - no storage permission needed
- **File Save**: Uses `MediaStore.Downloads` API on Android 10+ (API 29+)

## Build

This project uses Gradle and the Android Gradle Plugin. On this machine, Java 17 from Android Studio's JBR is used:

```powershell
$env:JAVA_HOME='D:\ProgramData\Android-Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Notes

- The app keeps the Wi-Fi Aware session alive while the Android file picker is open, so choosing a file does not tear down the NDP.
- Both publish and subscribe roles start a file receiver server and exchange receiver ports. This allows either side to receive files once NDP is ready.
- Legacy manual `Initiator` and `Responder` controls are hidden from the main UX; NDP startup is automatic.
