# Updated EDMS UML and Implementation Mapping

The supplied **Secure Chat Application — UML Class Diagram** describes five core concepts: `User`, `Message`, `ChatHistory`, `EncryptionService` and `FileStorage`. This update retains those concepts and adds the prototype's actual authentication, networking and logging responsibilities.

## Implementation-oriented class diagram

```mermaid
classDiagram
    direction TB
    class User {
        +int userId
        +String username
        -byte[] passwordSalt
        -byte[] passwordHash
        -byte[] historySalt
    }
    class AuthManager {
        +registerUser(username, password) boolean
        +authenticate(username, password) Session
    }
    class Session {
        +User user
        +EncryptionService historyEncryption
    }
    class Message {
        +UUID messageId
        +Instant timestamp
        +String sender
        +String receiver
        +String content
        +serialize() String
        +parse(wire) Message
    }
    class EncryptionService {
        +encrypt(plaintext) String
        +decrypt(ciphertext) String
        +deriveKey(password, salt) byte[]
    }
    class ChatHistory {
        +saveMessage(direction, message)
        +getMessages() HistoryResult
    }
    class FileStorage {
        +saveMessage(owner, direction, message, crypto)
        +loadMessages(owner, crypto) HistoryResult
    }
    class NetworkManager {
        +listen(port)
        +accept()
        +connect(host, port)
        +send(payload)
        +receive() String
        +close()
    }
    class AuditLogger {
        +info(event)
        +warning(event)
    }
    class EdmsGui {
        -Session currentUser
        -NetworkManager network
        +submitAuth()
        +startConnection()
        +sendMessage()
        +showHistory()
        +disconnect()
    }
    AuthManager --> User : manages
    AuthManager --> Session : authenticates
    Session --> User : signed in
    Session --> EncryptionService : local history key
    User "1" --> "0..*" Message : sends
    User "1" --> "0..*" ChatHistory : owns
    ChatHistory --> FileStorage : persists
    ChatHistory --> Message : contains
    FileStorage ..> EncryptionService : encrypts records
    NetworkManager ..> EncryptionService : encrypted frames
    NetworkManager ..> Message : transmits
    EdmsGui --> AuthManager : login / register
    EdmsGui --> NetworkManager : connects
    EdmsGui --> ChatHistory : history viewer
    EdmsGui --> EncryptionService : secure message frames
```

## Traceability from original UML

| Supplied UML concept | Actual class in `src/EdmsPrototype.java` | Notes |
|---|---|---|
| `User` | `User` and `AuthManager` | Registration/login deliberately delegated to a manager instead of a record model. Password is never stored directly; salted hash replaces `password : String`. |
| `Message` | `Message` | Unique identifier is `UUID`, time is `Instant`, participants are usernames for two-peer portability. AES work is delegated to `EncryptionService`. |
| `ChatHistory` | `ChatHistory` | One history facade belongs to each signed-in session. |
| `FileStorage` | `FileStorage` | Saves/loads per-user encrypted files rather than plaintext `List<String>` records. |
| `EncryptionService` | `EncryptionService` | Uses AES-256-GCM and PBKDF2-derived keys, replacing the original AES-ECB construction. |
| New support services | `NetworkManager`, `AuditLogger`, `Session` | Required by the prototype's socket workflow, audit events and login state. |
| New desktop UI | `EdmsGui` | Java Swing view/controller that reuses the original classes, messaging format and shared handshake. |

The Mermaid class diagram reflects the *implemented* Java structure. Method parameter types are abbreviated for readability; refer to source for exact Java signatures and exceptions.

## Desktop interface screen flow

```mermaid
flowchart TD
    A["Register or log in"] --> B["Start server or join peer"]
    B --> C{"Encrypted handshake valid?"}
    C -->|Yes| D["Chat, history and send"]
    C -->|No| B
    D -->|Disconnect| B
    D -->|Sign out| A
```

![Java Swing graphical chat preview](GUI_PREVIEW.png)

This preview uses the same `ChatPanel` component as the running GUI. The pictured messages are illustrative only.

## Message exchange sequence

```mermaid
sequenceDiagram
    participant A as Alice server
    participant B as Bob client
    A->>B: EDMS1 and random public salt
    Note over A,B: Both derive transport AES key from shared passphrase + salt
    A->>B: Encrypted HELLO alice
    B->>A: Encrypted HELLO bob
    A->>B: Encrypted MSG metadata + content
    Note over B: Verify GCM tag and recipient, then save encrypted history
    B->>A: Encrypted reply
    Note over A: Verify and save encrypted history
```

**Design boundary:** A user having the shared passphrase can claim a peer name in the encrypted hello; this is **not independent remote identity verification**. The key-salt header is public, passwords are local, and session keys are not a formal forward-secret key exchange.

## Supplied group diagram

![Original group UML class diagram](ORIGINAL_UML.jpeg)

Keep both the supplied image and this implementation diagram with the report. Explain intentional type/method changes if asked during the presentation.
