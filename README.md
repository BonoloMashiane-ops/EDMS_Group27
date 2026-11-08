# EDMS — Encrypted Direct Messaging System

**SDN260S | Group 27 | Java 17 Swing desktop + console | GUI trial/demo candidate**

EDMS is a two-person encrypted chat application. **This edition adds a graphical desktop chat window** to the original console version. Two application windows connect over a TCP localhost socket, exchange AES-GCM encrypted messages, and preserve separate encrypted chat histories for signed-in users. Both interfaces use the same service classes and encrypted communication protocol.

## What works

- Account registration and login, with input validation and persistent, individually salted PBKDF2-SHA256 password hashes.
- Localhost server/client socket messaging; two-way encrypted chat after a shared-passphrase handshake.
- AES-256-GCM (unique 96-bit IV per message, integrity tag); wrong passphrase or tampering is detected.
- Encrypted, per-user chat history, including sender, recipient, timestamp and message ID.
- `/history`, `/help`, `/quit` and `/logout`; graceful disconnect and input-error messages.
- Swing graphical login/registration, server/client setup, chat bubbles, send controls, history viewer and disconnect/sign out.
- Separate diagnostic logs that do not contain message bodies or passwords.
- **23 automated checks** (including encrypted handshake and headless GUI-rendering tests); a live two-process console demonstration was also performed.

## Build (Java 17 or newer)

In the `EDMS_Group27_Submission` folder:

```bash
bash scripts/build.sh
bash scripts/test.sh
```

Alternatively, using standard Java commands:

```bash
mkdir -p out
javac -encoding UTF-8 -d out src/EdmsPrototype.java src/EdmsGui.java test/EdmsTests.java
java -cp out EdmsTests
```

The script also supports minimal Linux JDK installations that have the `jdk.compiler` module but no `javac` executable. No external Java libraries, database, Maven or Gradle are needed.

On Windows, you can run `scripts\build-windows.bat` once and then open `scripts\run-gui-windows.bat` **twice** to get two graphical app windows. If Java isn't on your system `PATH`, launch through NetBeans using `EdmsGui` as the main class.

## Launch the new desktop GUI (recommended)

In **two separate terminals**, start the same Java program:

```bash
java -cp out EdmsGui --data demo-data
```

- **Window A:** Register/log in as `alice`. Select **Start server**, port `8888`, enter a 12+ character shared chat passphrase, and click **Start listening**.
- **Window B:** Register/log in as `bob`. Select **Join as client**, host `localhost`, port `8888`, and enter **the same** shared chat passphrase. Click **Connect to peer**.
- The chat screen opens in both windows. Send messages, use **View history**, then **Disconnect** or **Sign out**.

The included [GUI preview](docs/GUI_PREVIEW.png) is rendered using the actual Swing chat-screen components and example messages. It is a layout preview, not a screenshot of a real connected conversation.

## Run the original-style console demo (optional)

Open **two terminals** in the project directory.

**Terminal A — Alice/server**

```bash
java -cp out EdmsPrototype --server 8888 --data demo-data
```

Choose `1) Register`, create username `alice` with an 8+ character password, then enter a shared chat passphrase of 12+ characters. Wait for the other peer.

**Terminal B — Bob/client**

```bash
java -cp out EdmsPrototype --client localhost 8888 --data demo-data
```

Register `bob` with a different account password, then enter **the same shared chat passphrase** as Alice. Both terminals will report an encrypted connection. Type messages and try `/history` in each window. Finish with `/quit`.

**Important:** User login passwords are distinct from the chat passphrase. Each user needs their own account password; both peers need the same chat passphrase. Choose strong, private values for real tests. Do not put passwords/passphrases on command lines.

## Folder structure

```text
EDMS_Group27_Submission/
├── src/EdmsPrototype.java             Java source (eight team responsibilities)
├── src/EdmsGui.java                   Swing desktop interface
├── test/EdmsTests.java                23 automated tests
├── scripts/build.sh, test.sh           Build and test helpers
├── scripts/build-windows.bat            Windows JDK build helper
├── scripts/run-gui-windows.bat          Windows graphical launcher
├── docs/USER_GUIDE.md                  Instructions and troubleshooting
├── docs/SOFTWARE_DESIGN_REPORT.md      Requirements, architecture and evaluation
├── docs/UML_UPDATED.md                Updated class diagram and traceability
├── docs/TEST_EVIDENCE.md              Cases and observed output
├── docs/DEMO_SCRIPT.md                Presentation walkthrough
├── docs/GUI_PREVIEW.png               Preview of actual Swing chat layout
├── docs/GA2_GA7_REFLECTION.md         Graduate attribute reflection draft
├── docs/ORIGINAL_UML.jpeg             Copy of supplied group UML
└── SUBMISSION_CHECKLIST.md            Final verification and hand-in notes
```

Generated `out/` class files, `data/` user files and `demo-data/` are **not** part of the source hand-in. `demo-data/` is used only for the example; remove only your own throwaway demo account data if you want a fresh registration.

## Security and scope statement

This is an **educational localhost prototype**, not a production-ready secure messenger. The shared passphrase is manually communicated outside the application. It does not provide publicly verifiable remote identity, certified end-to-end key exchange, forward secrecy, anti-replay protection, multi-client routing, backups or account recovery. The server accepts one peer at a time. The Swing GUI is a new enhancement while the original console interface remains available.

For marks, demonstrate the real system and tests; do not claim enterprise-grade security. See the design report's limitations and next steps.

## Documentation and submission

Start with [User Guide](docs/USER_GUIDE.md), [Software Design Report](docs/SOFTWARE_DESIGN_REPORT.md), [Test Evidence](docs/TEST_EVIDENCE.md), and [GA2/GA7 Reflection](docs/GA2_GA7_REFLECTION.md). The reflection and contribution descriptions are **drafts to review personally as a group**, not a substitute for each student's genuine experience or your lecturer's precise rubric.
