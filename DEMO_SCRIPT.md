# EDMS Desktop GUI Demonstration Script — 4–6 minutes

This plan aims to show a complete working system without rushing the explanation.

## Before the presentation

1. Install Java 17+ and run `bash scripts/test.sh`; verify `23 passed, 0 failed`.
2. Prepare two terminal windows inside the project folder. They will launch **two graphical EDMS windows**.
3. Use a **fresh** demo folder, for example `class-demo-2026`, so registration can be shown. Do not delete any existing data by accident.
4. Choose a local port such as `8888` and a strong shared demonstration passphrase known to both presenters.
5. Keep the original UML image, updated class diagram and `docs/GUI_PREVIEW.png` ready on screen.

## Presentation sequence

| Time | Presenter/action | What to say or demonstrate |
|---|---|---|
| 0:00–0:40 | Introduce problem and UML | "EDMS protects a two-person chat with local account authentication, authenticated encryption, and encrypted history." Name `User`, `Message`, `ChatHistory`, `FileStorage` and `EncryptionService`. |
| 0:40–1:20 | Window A | Run `java -cp out EdmsGui --data class-demo-2026`; create account `alice`; select **Start server**, port `8888`, and press **Start listening**. Explain salted password hashes. |
| 1:20–2:00 | Window B | Run the same GUI command in another terminal; register `bob`; choose **Join as client** with `localhost:8888` and the same shared chat passphrase. Show the encrypted connection status in both windows. |
| 2:00–3:00 | Both GUI windows | Send `Hello Bob` and reply `Hello Alice`. Point out readable chat bubbles and timestamps, while explaining that the actual network frames are AES-GCM encrypted. |
| 3:00–3:40 | Both GUI windows | Click **View history**; show independent encrypted local chat histories. Mention that the `.history` file itself is not plaintext. |
| 3:40–4:30 | Testing terminal | Run `java -cp out EdmsTests`; point out wrong passwords, malformed ciphertext, Unicode, the 2,000-character boundary, corrupt history, wrong shared passphrases and Swing rendering. |
| 4:30–5:20 | Evaluation | Name one strength (working authenticated encryption) and one honest limitation (manually shared key, no independently verified remote identity; not production messenger). |
| 5:20–6:00 | Close | Click **Disconnect** and **Sign out**. Highlight GA2 knowledge application and GA7 privacy/societal impact. |

If NetBeans is the only environment permitted during the assessment, set `EdmsGui` as the main class. The original `EdmsPrototype` console entry point remains available as a fallback.

## Example examiner questions

**Why replace AES/ECB?** ECB exposes equal plaintext-block patterns and does not detect changes; AES-GCM encrypts with a new nonce and authenticates the result.

**Is the password sent to the peer?** No. The shared chat passphrase is entered locally in both terminals; a public salt is transmitted, and each peer derives the same AES key.

**Is chat history plaintext?** No. A separate key derived from the local account password encrypts each saved history record.

**How do you handle failures?** Invalid usernames, short passwords, too-long messages, bad ciphertext, corrupt history and closed sockets are validated, rejected or reported without uncontrolled program failure.

**Does this prove complete messaging security?** No. It is a local educational demonstration. Remote identity verification, anti-replay, forward secrecy, key exchange and production hardening are future improvements.
