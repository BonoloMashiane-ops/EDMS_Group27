# EDMS Software Design and Evaluation Report — Draft

**Module:** SDN260S — Software Design II  
**Project:** Encrypted Direct Messaging System (EDMS)  
**Group:** 27  
**Version/date:** Java 17 Swing GUI trial, 26 October 2026  
**Status:** Group review required before academic submission

## 1. Problem definition

Ordinary chat interfaces may expose sensitive information if messages travel or are stored without suitable protections. The group's problem was to design a small direct-messaging application that authenticates local users, exchanges information between two computers/processes through a network interface, encrypts messages, and keeps an accessible chat history. The project also exercises software design principles: object-oriented classes, modularity, file handling, networking, error handling and testing.

The original console prototype attempted these tasks but contained malformed Java source, fixed demonstration users, a hard-coded AES key, AES-ECB encryption and plaintext logging. It therefore required both functional repair and security improvement before a credible demonstration. After the console version passed its tests, the group requested a graphical desktop version modeled around a chat window.

## 2. Objectives and requirements

| ID | Requirement | Implemented evidence |
|---|---|---|
| F01 | Register and log in as a user | `AuthManager`; tests T01–T08 |
| F02 | Reject invalid/duplicate credentials | Input validation, constant-time hash comparison; T02–T06 |
| F03 | Send/receive direct messages | TCP `NetworkManager`; T19 and two-terminal run |
| F04 | Protect message confidentiality/integrity | `EncryptionService` AES-GCM; T09–T13 |
| F05 | Store and retrieve history | `ChatHistory` + `FileStorage`; T17–T18 |
| F06 | Provide user commands and orderly exit | `/history`, `/help`, `/quit`, `/logout`; live run |
| F07 | Log operational events without exposing message bodies | `AuditLogger`; event-only logging |
| F08 | Test normal, boundary, malformed and disconnected states | T01–T20, manual interaction |
| F09 | Operate through a graphical interface | `EdmsGui`: account screens, connection setup, chat bubbles, history viewer, logout; preview test T23 |
| NF01 | Readable modular OOP code | Named responsibility classes and updated UML |
| NF02 | Run without third-party dependencies | Java standard library; build script |
| NF03 | Limit malformed input and oversized messages | Message and socket frame bounds |
| NF04 | Keep graphical interface responsive during I/O | Swing event dispatch thread, background workers and dedicated receive thread |

**Scope:** A localhost, two-user, one-peer-per-session educational desktop application. The original console version remains available; the new Swing GUI offers screens for authentication, connection and chatting, with a history viewer and session controls.

## 3. Architecture and class responsibilities

The updated UML and sequence model are in [UML_UPDATED.md](UML_UPDATED.md). The `EdmsPrototype` entry point integrates the following:

1. **User and AuthManager:** persistent account identity, unique usernames, individually salted PBKDF2-SHA256 password verification, and signed-in `Session`.
2. **Message:** immutable message value with UUID, timestamp, sender, receiver and text. Serialization uses Base64 for content to avoid delimiter ambiguity.
3. **EncryptionService:** derives a 256-bit AES key using PBKDF2-HMAC-SHA256 and provides authenticated AES-GCM encryption/decryption. A new random 12-byte IV is used for every encrypted record.
4. **NetworkManager:** loopback server/client socket, bounded newline-delimited frame transport, connection timeout and close behaviour.
5. **ChatHistory and FileStorage:** per-user encrypted files, decrypting only after a successful local login. Corrupted lines are counted and skipped while valid lines remain readable.
6. **AuditLogger:** timestamps account, connection and message-handling events using Java logging; never writes message content, account passwords or chat passphrases.
7. **Application driver:** displays menus, performs encrypted peer hello, checks sender/receiver values, starts a receiver thread and dispatches console commands.
8. **Swing desktop GUI (`EdmsGui`):** presentation layer for account registration, login, server/client selection, encrypted chat, message history, disconnect and logout. It reuses the original services and the shared `secureHandshake` method instead of introducing a second message protocol.

### Graphical interface design

The GUI uses the Java Swing toolkit with a sidebar and three main states: **Account → Connection → Conversation**. The account screen handles registration/login; the connection screen lets a user start a localhost listener or join as a client, and the conversation screen provides message bubbles, timestamps, a text composer, a history dialog, and session controls. The `SwingWorker` mechanism keeps password verification, connection establishment and message sending off the graphical event dispatch thread. Incoming TCP frames are read on a background daemon thread and safely displayed using `SwingUtilities.invokeLater`. A headlessly rendered layout image is included as `docs/GUI_PREVIEW.png`. This proves that the Swing chat layout renders, but not that all mouse interactions have been exercised on a desktop machine.

### Data flow

1. A user registers/logs in. A random password salt and hash live in `users.tsv`; the original password is not persisted.
2. Alice starts a localhost server and Bob connects with a client. Both enter the same shared chat passphrase **out of band**.
3. Alice sends a public 16-byte random session salt. Both derive matching transport keys using PBKDF2. They exchange AES-GCM encrypted `HELLO` messages before chat begins.
4. Alice creates a `Message`, serializes it, encrypts its complete body and metadata, then sends an `ENC|` Base64 frame.
5. Bob checks the GCM authentication tag, validates sender/receiver and reconstructs the message. The chat is displayed and a separately encrypted history entry is saved under Bob's account.
6. Both can inspect local history with `/history` or end the session with `/quit`.

## 4. Technical choices and algorithms

**Authentication:** Passwords are checked using PBKDF2-HMAC-SHA256 (120,000 iterations) and random per-account 16-byte salts. Hashes are compared with `MessageDigest.isEqual`. Different salts produce different stored hashes even for identical passwords. Registration enforces a 3–20-character username policy and an 8–128-character password length.

**Transport encryption:** AES-256 in Galois/Counter Mode (`AES/GCM/NoPadding`) provides confidentiality plus a 128-bit authentication tag. Nonces are random per encryption operation. Corrupt payloads or wrong shared keys are rejected, unlike the prototype's unauthenticated ECB mode. The session salt is public; the passphrase must be kept private.

**History encryption:** A distinct key is derived from the signed-in user's account password and a separate persistent history salt. The application saves one Base64-encoded AES-GCM value per file line. This avoids plaintext chat logs. It also means lost passwords cannot recover old history in this prototype.

**Storage:** `users.tsv` is replaced as a whole using a temporary file and atomic move where supported. Message history is appended; invalid records are skipped on reading instead of stopping the session. Standard Java I/O handles resource cleanup.

**Network:** Java `ServerSocket` and `Socket` transport bounded lines over localhost. The initial socket read has a 10-second timeout and connection uses a 5-second timeout; no socket read timeout remains after the handshake so users may pause while typing. A dedicated receiver thread allows incoming and outgoing chat concurrently.

## 5. Error handling and usability

The app reports invalid input, duplicate accounts, failed login, weak passphrase, inaccessible port, mismatched key, corrupt history, malformed cipher and network disconnect. It accepts Unicode text, rejects empty/oversized messages and supports `/help`. Operational logs are kept separately from encrypted conversations. The console UI avoids dependencies and is practical for two-terminal demonstration, though asynchronous incoming text may visually interrupt typing.

## 6. Testing and evaluation

The automated runner covers **23 cases**, including normal registration/login, duplicate and bad inputs, persistence, salted hashes, Unicode round trips, random IVs, tampering, wrong key, message size boundaries, history corruption, socket disconnect, peer identity handshake, wrong shared passphrases and headless Swing layout rendering. The verified result in an environment permitting localhost sockets was **23 passed, 0 failed**. A separate live Alice/Bob console run verified registration, encrypted connection, two-way messages, history on both sides and exit without crashing. Detailed observed outputs are in [TEST_EVIDENCE.md](TEST_EVIDENCE.md). A physical GUI click-through rehearsal remains a pre-submission task because the automated build environment has no desktop display.

The initial restricted test runner blocked loopback port creation with `Operation not permitted`; the tests were rerun in an authorized environment allowing localhost sockets. This was a sandbox restriction, not a code failure.

## 7. Changes from the group prototype

| Original | Revised |
|---|---|
| Escaped/malformed Java source | Builds cleanly on Java 17 |
| Built-in Alice/Bob accounts | Interactive registration and persistent accounts |
| Unsalted SHA-256 password digest | Random-salt PBKDF2 password hashing |
| Hardcoded AES key + ECB mode | Prompted shared passphrase, AES-GCM authentication |
| Plaintext `chat_history.log` | Per-user encrypted `.history` records |
| One-off crypto self-checks | Twenty-three repeatable automated tests |
| Unbounded incoming lines | 16,384-character frame limit |
| No implementation-matched report | Updated UML, user guide, demo script and design report |
| NetBeans console only | Optional Swing desktop registration, connect and chat screens; console retained |

## 8. Security, societal impact and limitations

The design demonstrates more responsible handling of confidential information, but it **must not be described as production-secure**. The application has no trusted remote identity registry, certificate validation, ratcheting/forward secrecy, independent key exchange, replay prevention, safe password recovery, rate limiting, encrypted backups, central server routing or support for many simultaneous peers. A weak shared passphrase may be guessed offline from captured authenticated ciphertext. The registration file is not designed for concurrent updates across machines. Only localhost connections are deliberately allowed.

Security and usability must be balanced: protecting history raises the risk of losing data when a password is forgotten; asking users to exchange a shared passphrase introduces a social-engineering risk; diagnostics should never collect private message bodies. Future development should use an established authenticated key agreement protocol, stronger account lifecycle controls, file permissions/locking, abuse limits, accessibility improvements and independent security review.

## 9. Team responsibilities — verify before submission

Names/roles here are taken from the group's supplied source comments, **not proof of who completed every revised line**. Each member should confirm and accurately describe their own contribution and the work done collaboratively.

| Member | Responsibility listed in original prototype | Implemented area to explain |
|---|---|---|
| Aaron Majova | User accounts & authentication | `User`, `AuthManager`, login/register |
| Bonolo Mashiane | Console interface | Original console input plus GUI presentation to review with the team |
| Aphiwe Nokuphiwa Mchunu | Encryption | AES-GCM encryption path |
| Londeka Mthengwana | Decryption and key management | PBKDF2 keys, authenticated decryption |
| Amahle Mthethwa | Networking | TCP sockets, framed messages and disconnects |
| Nomusa Ndumo | File handling & message storage | `ChatHistory`, `FileStorage`, audit logs |
| Tshegofatso Seleko | Testing and security validation | `EdmsTests`, normal and edge tests |
| Angel Nokulunga Skhosana | Integration and driver | Main application, end-to-end behaviour |

## 10. Conclusion and recommendations

The improved EDMS meets the identified **core console demonstration** requirements and adds a functioning compiled/rendered Swing front end: local account management, encrypted two-way messaging, protected history, validation, logging, a UML-linked modular design and repeatable test evidence. It is ready for **GUI rehearsal** on the group's actual presentation laptop; the full desktop interaction has not yet been manually exercised in this headless build environment. A deployed multi-user messaging product would require the security and scalability work identified above.

## References

- Oracle, *Java SE 17 Cryptography Architecture*, `Cipher`, `GCMParameterSpec`, `SecretKeyFactory`, `ServerSocket` and `Socket` API documentation: https://docs.oracle.com/en/java/javase/17/security/java-cryptography-architecture-jca-reference-guide.html
- Engineering Council of South Africa (ECSA), *Qualification Standard for Diploma in Engineering (NQF Level 6), E-02-PN*, GA2 and GA7. https://www.ecsa.co.za/download/483/new-engineering-technology-qualification-standards/1007434/e-02-pn-qualification-standard-for-diploma-in-engineering-nqf-level-6-2.pdf
- South African Qualifications Authority, registered qualification description with graduate-attribute statements: https://regqs.saqa.org.za/viewQualification.php?id=118411
