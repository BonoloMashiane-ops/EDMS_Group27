# Graduate Attribute 2 and 7 Reflection — Group Draft

**Important:** This is a **starting draft** written around the software design and verified tests, not a claim that every member personally did every task. Adjust the first-person statements to reflect the group's actual activities, disagreements, challenges, individual contributions and university rubric before submitting.

## GA2 — Application of scientific and engineering knowledge

For our Encrypted Direct Messaging System, we applied computer science and software engineering concepts to a practical communication problem. We decomposed the application into account management, messages, encryption, networking, history storage and integration. The UML class design helped us identify what information and behaviour each component should own. This separation made the program easier to understand and test: for example, `ChatHistory` manages the user's view of saved conversations while `FileStorage` handles the actual file operations.

The strongest example of applied knowledge was encryption. Our first prototype used AES in ECB mode with a fixed key. In reviewing it, we recognized that making a program "encrypt text" is different from providing meaningful protection. We replaced that mechanism with AES-GCM, a mode that combines encryption and integrity verification. We also used random nonces so encrypting the same input twice produces different ciphertexts. For account authentication, we applied PBKDF2-HMAC-SHA256 with randomly generated salts instead of directly hashing passwords with SHA-256. These choices required knowledge of cryptography, randomness, secure key derivation, byte encodings and Java's cryptographic APIs.

We also applied networking knowledge through TCP socket programming. We considered how a server waits for a peer, how both ends exchange encrypted frames, and what happens when a connection closes unexpectedly. We used basic data-structure and algorithm design skills to give messages unique identifiers and timestamps, serialize the fields safely, and reject malformed or oversized inputs. Separating a receiving thread from the sending interface demonstrates understanding of concurrent operations.

Our testing approach connected theory to evidence. We did not only test a typical successful message: we checked duplicate account creation, wrong credentials, Unicode text, an exactly 2,000-character message, an oversized message, altered ciphertext, the wrong key, corrupt history and socket disconnection. The final automated result was **20/20 passing tests** under permitted localhost execution, followed by a successful two-terminal Alice/Bob demonstration. The first socket test failed solely because the restricted execution environment prevented local port binding; rerunning under an environment that allowed sockets helped us distinguish an environmental restriction from an implementation defect.

We extended the console application with a Java Swing interface and learned to separate presentation work from the secure messaging services. Graphical controls initiate registration, background socket connections and message sending, while long-running operations are moved away from the event dispatch thread so the interface remains responsive. We generated a preview of the actual chat layout and added a rendering test, although real mouse/keyboard usability still needs to be checked on our laboratory computers.

One learning point was that modular code and an attractive demo alone are insufficient: algorithms, boundaries, error handling and repeatable verification must support the design. For further improvement, we would research authenticated key exchange, formal threat modelling, synchronized file writes and usability testing, and ask others to review our cryptographic assumptions.

## GA7 — Sustainability and impact of engineering activity

Our project deals with private communication, so its engineering decisions may affect personal safety, trust and dignity. A chat system that accidentally exposes passwords or sensitive messages could harm students, families or organizations. We therefore considered the impact of software not only on whether it functions, but on how it treats personal data. In the improved design, account passwords are represented by salted password-derived hashes, message content is encrypted over the network, and saved history is encrypted rather than written into plain diagnostic logs.

We recognized that security features introduce responsibilities and trade-offs. Strong encryption can protect a vulnerable user against casual interception, but a system using a shared passphrase may still be unsafe if the passphrase is weak or disclosed. Encrypting local chat records protects privacy, yet the absence of password recovery also risks permanent data loss. A user could falsely trust a peer identity because the application does not independently verify who knows the shared passphrase. It would be unethical to market this academic prototype as a production-secure messenger without confronting those limitations.

Accessibility and practical usability also matter. The original console mode runs on ordinary machines without commercial software or network services, which lowers cost and infrastructure needs for a teaching demonstration. However, it may not suit users unfamiliar with command lines. The new Swing version introduces visible forms, connection-mode choices, readable conversation bubbles and buttons for history, disconnect and sign out. A future version should test keyboard-only accessibility, screen-reader announcements, high-contrast modes and localization, along with better identity checking, secure backups and abuse controls.

There are economic and environmental implications. A small Java-standard-library prototype needs no paid hosting, cloud database or third-party messaging service for a local demonstration, reducing costs and resource use. That does not mean it has zero environmental impact: development computers consume electricity, and large-scale deployment would require evaluating energy use, storage retention and the cost of maintaining encryption and security updates.

The key GA7 lesson is to consider the people affected by design choices. Protecting confidentiality, limiting logs, documenting risks and testing failure cases are part of responsible engineering. If we extended EDMS beyond an academic demonstration, we would consult users, study privacy and accessibility requirements, review relevant laws and standards, and arrange an independent security assessment before allowing real sensitive communications.

## Evidence mapping for the assessor

| Graduate attribute | Evidence in deliverable |
|---|---|
| GA2: use of engineering/computing concepts | Updated UML, modular Java classes, PBKDF2/AES-GCM, TCP sockets and serialization |
| GA2: analysis and testing | T01–T23 cases, boundary tests, two-terminal integration run, GUI rendering |
| GA2: reasoned design improvement | Original vs revised comparison in report Section 7 |
| GA7: privacy and safety | Encrypted transport/history, no plaintext audit logging |
| GA7: risks, ethics and societal effects | Threat/limitations discussion in report Section 8; second reflection section |
| GA7: sustainability and inclusivity | Dependency-light localhost prototype; cost, accessibility and energy discussion |

**Definition note:** The commonly published ECSA/SAQA language identifies GA2 with applying scientific and engineering knowledge, and GA7 with the sustainability and impact of engineering activity. Check which version and assessment indicators your course applies. Useful authoritative references: [ECSA E-02-PN](https://www.ecsa.co.za/download/483/new-engineering-technology-qualification-standards/1007434/e-02-pn-qualification-standard-for-diploma-in-engineering-nqf-level-6-2.pdf) and [SAQA qualification information](https://regqs.saqa.org.za/viewQualification.php?id=118411).
