import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * EDMS Group 27: single-file Java 17 teaching prototype.
 *
 * Original team modules remain visible as named nested classes. This is a
 * two-peer localhost demo, not a production identity or key-distribution system.
 */
public final class EdmsPrototype {
    private static final SecureRandom RANDOM = new SecureRandom();
    static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_WIRE_LINE = 16384;

    private EdmsPrototype() {}

    // Member 1: Aaron Majova - authentication and user accounts.
    public static final class User {
        public final int userId;
        public final String username;
        private final byte[] passwordSalt;
        private final byte[] passwordHash;
        private final byte[] historySalt;

        private User(int id, String username, byte[] passwordSalt,
                     byte[] passwordHash, byte[] historySalt) {
            this.userId = id;
            this.username = username;
            this.passwordSalt = passwordSalt.clone();
            this.passwordHash = passwordHash.clone();
            this.historySalt = historySalt.clone();
        }

        private String serialize() {
            return userId + "\t" + username + "\t" + b64(passwordSalt)
                + "\t" + b64(passwordHash) + "\t" + b64(historySalt);
        }

        private static User parse(String line) {
            String[] parts = line.split("\t", -1);
            if (parts.length != 5 || !validUsername(parts[1])) {
                throw new IllegalArgumentException("Invalid account record");
            }
            return new User(Integer.parseInt(parts[0]), parts[1],
                unb64(parts[2]), unb64(parts[3]), unb64(parts[4]));
        }
    }

    public static final class AuthManager {
        private final Path accountFile;

        public AuthManager(Path dataDirectory) throws IOException {
            Files.createDirectories(dataDirectory);
            this.accountFile = dataDirectory.resolve("users.tsv");
        }

        public static boolean validUsername(String username) {
            return EdmsPrototype.validUsername(username);
        }

        public static boolean validPassword(String password) {
            return password != null && password.length() >= 8 && password.length() <= 128;
        }

        public synchronized boolean registerUser(String username, String password)
                throws IOException, GeneralSecurityException {
            if (!validUsername(username) || !validPassword(password)) {
                throw new IllegalArgumentException(
                    "Username: 3-20 lowercase letters/digits/underscores, beginning with a letter; "
                    + "password: 8-128 characters.");
            }
            List<User> users = loadUsers();
            for (User user : users) {
                if (user.username.equals(username)) return false;
            }
            byte[] salt = randomBytes(16);
            byte[] historySalt = randomBytes(16);
            byte[] hash = EncryptionService.deriveKey(password, salt);
            int nextId = users.stream().mapToInt(u -> u.userId).max().orElse(0) + 1;
            users.add(new User(nextId, username, salt, hash, historySalt));
            saveUsers(users);
            return true;
        }

        public synchronized Optional<Session> authenticate(String username, String password)
                throws IOException, GeneralSecurityException {
            if (username == null || password == null) return Optional.empty();
            for (User user : loadUsers()) {
                if (user.username.equals(username)) {
                    byte[] candidate = EncryptionService.deriveKey(password, user.passwordSalt);
                    if (MessageDigest.isEqual(candidate, user.passwordHash)) {
                        return Optional.of(new Session(user,
                            new EncryptionService(password, user.historySalt)));
                    }
                    return Optional.empty();
                }
            }
            return Optional.empty();
        }

        private List<User> loadUsers() throws IOException {
            List<User> users = new ArrayList<>();
            if (!Files.exists(accountFile)) return users;
            for (String line : Files.readAllLines(accountFile, StandardCharsets.UTF_8)) {
                if (!line.isBlank()) users.add(User.parse(line));
            }
            return users;
        }

        private void saveUsers(List<User> users) throws IOException {
            // Write a complete new snapshot; a failed write leaves the original intact.
            Path temp = Files.createTempFile(accountFile.getParent(), "users-", ".tmp");
            try {
                List<String> lines = new ArrayList<>();
                for (User user : users) lines.add(user.serialize());
                Files.write(temp, lines, StandardCharsets.UTF_8);
                try {
                    Files.move(temp, accountFile, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(temp, accountFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static final class Session {
        public final User user;
        public final EncryptionService historyEncryption;

        private Session(User user, EncryptionService historyEncryption) {
            this.user = user;
            this.historyEncryption = historyEncryption;
        }
    }

    // Member 3: Aphiwe Nokuphiwa Mchunu (encryption).
    // Member 4: Londeka Mthengwana (decryption and key derivation).
    public static final class EncryptionService {
        private static final int PBKDF2_ITERATIONS = 120000;
        private static final int IV_BYTES = 12;
        private final SecretKeySpec key;

        public EncryptionService(String passphrase, byte[] salt) throws GeneralSecurityException {
            if (passphrase == null || passphrase.length() < 8 || salt.length != 16) {
                throw new IllegalArgumentException("Key passphrase must be at least 8 characters; salt 16 bytes.");
            }
            this.key = new SecretKeySpec(deriveKey(passphrase, salt), "AES");
        }

        static byte[] deriveKey(String password, byte[] salt) throws GeneralSecurityException {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt,
                PBKDF2_ITERATIONS, 256);
            try {
                return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
            } finally {
                spec.clearPassword();
            }
        }

        public String encrypt(String plaintext) throws GeneralSecurityException {
            byte[] iv = randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(ciphertext, 0, packed, iv.length, ciphertext.length);
            return b64(packed);
        }

        public String decrypt(String payload) throws GeneralSecurityException {
            final byte[] packed;
            try {
                packed = unb64(payload);
            } catch (IllegalArgumentException e) {
                throw new GeneralSecurityException("Malformed encrypted payload", e);
            }
            if (packed.length < IV_BYTES + 16) {
                throw new GeneralSecurityException("Encrypted payload too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                new GCMParameterSpec(128, packed, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(packed, IV_BYTES, packed.length - IV_BYTES);
            return new String(plaintext, StandardCharsets.UTF_8);
        }
    }

    // UML Message entity: metadata and content are encrypted together in transit.
    public static final class Message {
        public final UUID messageId;
        public final Instant timestamp;
        public final String sender;
        public final String receiver;
        public final String content;

        public Message(UUID messageId, Instant timestamp, String sender,
                       String receiver, String content) {
            if (messageId == null || timestamp == null || !validUsername(sender)
                    || !validUsername(receiver) || content == null || content.isBlank()
                    || content.codePointCount(0, content.length()) > MAX_MESSAGE_LENGTH
                    || content.indexOf('\0') >= 0) {
                throw new IllegalArgumentException("Invalid message, participants or length (1-2000).");
            }
            this.messageId = messageId;
            this.timestamp = timestamp;
            this.sender = sender;
            this.receiver = receiver;
            this.content = content;
        }

        public static Message create(String sender, String receiver, String content) {
            return new Message(UUID.randomUUID(), Instant.now(), sender, receiver, content);
        }

        public String serialize() {
            return messageId + "\t" + timestamp + "\t" + sender + "\t" + receiver
                + "\t" + b64(content.getBytes(StandardCharsets.UTF_8));
        }

        public static Message parse(String wire) {
            try {
                String[] parts = wire.split("\t", -1);
                if (parts.length != 5) throw new IllegalArgumentException("Invalid message fields");
                return new Message(UUID.fromString(parts[0]), Instant.parse(parts[1]),
                    parts[2], parts[3], new String(unb64(parts[4]), StandardCharsets.UTF_8));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid message timestamp", e);
            }
        }
    }

    // Member 6: Nomusa Ndumo - encrypted local message storage.
    public static final class FileStorage {
        private final Path historyDirectory;

        public FileStorage(Path dataDirectory) throws IOException {
            historyDirectory = dataDirectory.resolve("history");
            Files.createDirectories(historyDirectory);
        }

        private Path pathFor(String username) {
            if (!validUsername(username)) throw new IllegalArgumentException("Invalid username");
            return historyDirectory.resolve(username + ".history");
        }

        public synchronized void saveMessage(String owner, String direction,
                                             Message message, EncryptionService crypto)
                throws IOException, GeneralSecurityException {
            if (!direction.equals("SENT") && !direction.equals("RECEIVED")) {
                throw new IllegalArgumentException("Invalid direction");
            }
            String payload = crypto.encrypt(direction + "\t" + message.serialize());
            Files.writeString(pathFor(owner), payload + System.lineSeparator(),
                StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        public synchronized HistoryResult loadMessages(String owner, EncryptionService crypto)
                throws IOException {
            List<HistoryEntry> entries = new ArrayList<>();
            int unreadable = 0;
            Path file = pathFor(owner);
            if (!Files.exists(file)) return new HistoryResult(entries, 0);
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                try {
                    String clear = crypto.decrypt(line);
                    String[] parts = clear.split("\t", 2);
                    if (parts.length != 2 || (!parts[0].equals("SENT")
                            && !parts[0].equals("RECEIVED"))) {
                        throw new IllegalArgumentException("Invalid history entry");
                    }
                    entries.add(new HistoryEntry(parts[0], Message.parse(parts[1])));
                } catch (GeneralSecurityException | IllegalArgumentException e) {
                    unreadable++;
                }
            }
            return new HistoryResult(entries, unreadable);
        }

        public Path historyPath(String owner) { return pathFor(owner); }
    }

    public static final class HistoryEntry {
        public final String direction;
        public final Message message;
        private HistoryEntry(String direction, Message message) {
            this.direction = direction;
            this.message = message;
        }
    }

    public static final class HistoryResult {
        public final List<HistoryEntry> entries;
        public final int unreadableCount;
        private HistoryResult(List<HistoryEntry> entries, int unreadableCount) {
            this.entries = List.copyOf(entries);
            this.unreadableCount = unreadableCount;
        }
    }

    // UML ChatHistory facade delegates persistence to FileStorage.
    public static final class ChatHistory {
        private final FileStorage storage;
        private final Session session;

        public ChatHistory(FileStorage storage, Session session) {
            this.storage = storage;
            this.session = session;
        }

        public void saveMessage(String direction, Message message)
                throws IOException, GeneralSecurityException {
            storage.saveMessage(session.user.username, direction, message,
                session.historyEncryption);
        }

        public HistoryResult getMessages() throws IOException {
            return storage.loadMessages(session.user.username, session.historyEncryption);
        }
    }

    // Member 5: Amahle Mthethwa - socket setup and bounded network frames.
    public static final class NetworkManager implements AutoCloseable {
        private ServerSocket server;
        private Socket socket;
        private BufferedReader in;
        private BufferedWriter out;

        public void listen(int port) throws IOException {
            if (port < 0 || port > 65535) throw new IllegalArgumentException("Invalid port");
            server = new ServerSocket();
            server.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
        }

        public int boundPort() {
            if (server == null) throw new IllegalStateException("Server not started");
            return server.getLocalPort();
        }

        public void accept() throws IOException {
            if (server == null) throw new IllegalStateException("Call listen first");
            socket = server.accept();
            initializeStreams();
        }

        public void connect(String host, int port) throws IOException {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), 5000);
            initializeStreams();
        }

        private void initializeStreams() throws IOException {
            socket.setSoTimeout(10000); // Handshake timeout; disabled after verification.
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        }

        public void finishHandshake() throws IOException { socket.setSoTimeout(0); }

        public synchronized void send(String payload) throws IOException {
            if (out == null) throw new IOException("Not connected");
            if (payload == null || payload.length() > MAX_WIRE_LINE || payload.indexOf('\n') >= 0) {
                throw new IllegalArgumentException("Invalid network frame");
            }
            out.write(payload);
            out.newLine();
            out.flush();
        }

        public String receive() throws IOException {
            if (in == null) throw new IOException("Not connected");
            StringBuilder frame = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') return frame.toString();
                if (c != '\r') frame.append((char) c);
                if (frame.length() > MAX_WIRE_LINE) throw new IOException("Frame too long");
            }
            if (frame.length() > 0) throw new IOException("Truncated network frame");
            return null;
        }

        @Override public void close() {
            try { if (socket != null) socket.close(); } catch (IOException ignored) {}
            try { if (server != null) server.close(); } catch (IOException ignored) {}
        }
    }

    /** Common authenticated-encryption handshake used by the CLI and Swing GUI. */
    public static final class PeerConnection {
        public final String peerName;
        public final EncryptionService transport;

        private PeerConnection(String peerName, EncryptionService transport) {
            this.peerName = peerName;
            this.transport = transport;
        }
    }

    public static PeerConnection secureHandshake(NetworkManager network, boolean serverMode,
                                                 String myName, String sharedPassphrase)
            throws IOException, GeneralSecurityException {
        if (!validUsername(myName) || sharedPassphrase == null
                || sharedPassphrase.length() < 12) {
            throw new IllegalArgumentException("Valid local user and 12+ character passphrase required");
        }
        byte[] salt;
        if (serverMode) {
            salt = randomBytes(16);
            network.send("EDMS1|" + b64(salt));
        } else {
            String greeting = network.receive();
            if (greeting == null || !greeting.startsWith("EDMS1|")) {
                throw new GeneralSecurityException("Invalid peer protocol");
            }
            try { salt = unb64(greeting.substring(6)); }
            catch (IllegalArgumentException e) {
                throw new GeneralSecurityException("Invalid handshake salt", e);
            }
        }
        EncryptionService transport = new EncryptionService(sharedPassphrase, salt);
        network.send("ENC|" + transport.encrypt("HELLO|" + myName));
        String peerGreeting = network.receive();
        if (peerGreeting == null || !peerGreeting.startsWith("ENC|")) {
            throw new GeneralSecurityException("Peer identity handshake failed");
        }
        String peerHello = transport.decrypt(peerGreeting.substring(4));
        if (!peerHello.startsWith("HELLO|")
                || !validUsername(peerHello.substring(6))
                || peerHello.substring(6).equals(myName)) {
            throw new GeneralSecurityException("Invalid or duplicate peer identity");
        }
        network.finishHandshake();
        return new PeerConnection(peerHello.substring(6), transport);
    }

    // Member 6: operational audit metadata (no plaintext messages or passwords).
    public static final class AuditLogger implements AutoCloseable {
        private final Logger logger;
        private final FileHandler handler;

        public AuditLogger(Path dataDirectory) throws IOException {
            Files.createDirectories(dataDirectory.resolve("logs"));
            logger = Logger.getLogger("edms-" + UUID.randomUUID());
            logger.setUseParentHandlers(false);
            handler = new FileHandler(dataDirectory.resolve("logs/app.log").toString(), true);
            handler.setFormatter(new SimpleFormatter());
            logger.addHandler(handler);
        }

        public void info(String event) { logger.info(event); }
        public void warning(String event) { logger.warning(event); }

        @Override public void close() {
            handler.flush();
            handler.close();
        }
    }

    // Member 7: Tshegofatso Seleko - automated security tests are in test/EdmsTests.java.
    // Member 2: Bonolo Mashiane - console UI and validation.
    // Member 8: Angel Nokulunga Skhosana - integration/entry point.
    public static void main(String[] args) {
        try {
            Config config = Config.parse(args);
            if (config.help) {
                printUsage();
                return;
            }
            try (Scanner scanner = new Scanner(System.in);
                 AuditLogger audit = new AuditLogger(config.dataDirectory)) {
                System.out.println("=======================================================");
                System.out.println(" EDMS | Encrypted Direct Messaging | Group 27");
                System.out.println(" Java 17 Console Demonstration");
                System.out.println("=======================================================");
                AuthManager auth = new AuthManager(config.dataDirectory);
                Session session = authenticateInteractively(scanner, auth, audit);
                if (session == null) {
                    System.out.println("Goodbye.");
                    return;
                }
                System.out.println("Signed in as " + session.user.username + ".");
                FileStorage storage = new FileStorage(config.dataDirectory);
                ChatHistory history = new ChatHistory(storage, session);
                String sharedPassphrase = readPassword(scanner,
                    "Shared chat passphrase (12+ characters, same on both terminals): ");
                if (sharedPassphrase.length() < 12) {
                    System.out.println("Error: shared chat passphrase is too short.");
                    return;
                }
                try (NetworkManager network = new NetworkManager()) {
                    if (config.serverMode) {
                        network.listen(config.port);
                        System.out.println("Waiting for peer on localhost:" + network.boundPort() + " ...");
                        network.accept();
                    } else {
                        System.out.println("Connecting to " + config.host + ":" + config.port + " ...");
                        network.connect(config.host, config.port);
                    }
                    String myName = session.user.username;
                    PeerConnection peer = secureHandshake(network, config.serverMode,
                        myName, sharedPassphrase);
                    String peerName = peer.peerName;
                    audit.info("Peer connection established; local user " + myName);
                    System.out.println("Encrypted connection ready: " + myName + " <-> " + peerName);
                    System.out.println("Type a message, /history, /help or /quit.");
                    runChat(scanner, network, peer.transport, history, myName, peerName, audit);
                }
                audit.info("Session ended for local user " + session.user.username);
                System.out.println("Logged out. EDMS shutdown complete.");
            }
        } catch (IllegalArgumentException e) {
            System.err.println("Input error: " + e.getMessage());
            printUsage();
        } catch (AEADBadTagException e) {
            System.err.println("Secure handshake failed: chat passphrases do not match or data was altered.");
        } catch (GeneralSecurityException e) {
            System.err.println("Security error: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
        }
    }

    private static Session authenticateInteractively(Scanner scanner, AuthManager auth,
                                                     AuditLogger audit)
            throws IOException, GeneralSecurityException {
        while (true) {
            System.out.println("\n1) Register   2) Login   3) Exit");
            String choice = readLine(scanner, "Choice: ");
            if (choice == null || choice.equals("3")) return null;
            if (!choice.equals("1") && !choice.equals("2")) {
                System.out.println("Select 1, 2 or 3.");
                continue;
            }
            String name = readLine(scanner, "Username: ");
            if (name == null) return null;
            String password = readPassword(scanner, "Password: ");
            if (choice.equals("1")) {
                String confirmation = readPassword(scanner, "Confirm password: ");
                if (!password.equals(confirmation)) {
                    System.out.println("Passwords do not match.");
                    continue;
                }
                try {
                    if (!auth.registerUser(name, password)) {
                        System.out.println("Username already registered.");
                        continue;
                    }
                    audit.info("Account registered: " + name);
                    System.out.println("Registration successful.");
                } catch (IllegalArgumentException e) {
                    System.out.println("Registration rejected: " + e.getMessage());
                    continue;
                }
            }
            Optional<Session> session = auth.authenticate(name, password);
            if (session.isPresent()) {
                audit.info("Local login succeeded: " + name);
                return session.get();
            }
            audit.warning("Local login rejected");
            System.out.println("Invalid username or password.");
        }
    }

    private static void runChat(Scanner scanner, NetworkManager network,
                                EncryptionService transport, ChatHistory history,
                                String local, String peer, AuditLogger audit) {
        AtomicBoolean active = new AtomicBoolean(true);
        Thread receiver = new Thread(() -> {
            try {
                while (active.get()) {
                    String line = network.receive();
                    if (line == null) break;
                    if (!line.startsWith("ENC|")) {
                        audit.warning("Rejected non-encrypted network frame");
                        System.out.println("\n[Warning] Rejected invalid network frame.");
                        continue;
                    }
                    String clear = transport.decrypt(line.substring(4));
                    if (!clear.startsWith("MSG|")) {
                        audit.warning("Rejected unexpected encrypted frame");
                        continue;
                    }
                    Message message = Message.parse(clear.substring(4));
                    if (!message.sender.equals(peer) || !message.receiver.equals(local)) {
                        audit.warning("Rejected message with unexpected participants");
                        continue;
                    }
                    history.saveMessage("RECEIVED", message);
                    audit.info("Incoming message stored");
                    System.out.println("\n[" + message.timestamp + "] " + peer + ": " + message.content);
                    System.out.print("> ");
                }
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                audit.warning("Rejected corrupted/authentication-failed encrypted frame");
                System.out.println("\n[Security] Incoming encrypted message failed verification.");
            } catch (IOException e) {
                if (active.get()) System.out.println("\n[Network] Connection closed or interrupted.");
            } finally {
                active.set(false);
                System.out.println("\n[Network] Peer disconnected. Use /quit to finish.");
            }
        }, "edms-receiver");
        receiver.setDaemon(true);
        receiver.start();

        while (active.get()) {
            String line = readLine(scanner, "> ");
            if (line == null || line.equalsIgnoreCase("/quit")
                    || line.equalsIgnoreCase("/logout")) break;
            if (line.equalsIgnoreCase("/help")) {
                System.out.println("Commands: /history = local messages; /help = help; /quit = disconnect/logout.");
                continue;
            }
            if (line.equalsIgnoreCase("/history")) {
                try {
                    HistoryResult result = history.getMessages();
                    System.out.println("--- Encrypted local chat history (decrypted for signed-in user) ---");
                    if (result.entries.isEmpty()) System.out.println("No messages yet.");
                    for (HistoryEntry entry : result.entries) {
                        Message m = entry.message;
                        System.out.println("[" + m.timestamp + "] " + entry.direction + " "
                            + m.sender + " -> " + m.receiver + ": " + m.content);
                    }
                    if (result.unreadableCount > 0) {
                        System.out.println("Warning: " + result.unreadableCount
                            + " unreadable/corrupt history record(s) skipped.");
                    }
                } catch (IOException e) {
                    System.out.println("Could not read history: " + e.getMessage());
                }
                continue;
            }
            if (line.startsWith("/")) {
                System.out.println("Unknown command. Type /help.");
                continue;
            }
            try {
                Message message = Message.create(local, peer, line);
                network.send("ENC|" + transport.encrypt("MSG|" + message.serialize()));
                history.saveMessage("SENT", message);
                audit.info("Outgoing message transmitted and stored");
                System.out.println("[Sent securely to " + peer + "]");
            } catch (IllegalArgumentException e) {
                System.out.println("Message rejected: " + e.getMessage());
            } catch (IOException | GeneralSecurityException e) {
                System.out.println("Send failed: " + e.getMessage());
                break;
            }
        }
        active.set(false);
        network.close();
        try { receiver.join(1000); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String readLine(Scanner scanner, String prompt) {
        Console console = System.console();
        if (console != null) return console.readLine("%s", prompt);
        System.out.print(prompt);
        return scanner.hasNextLine() ? scanner.nextLine() : null;
    }

    private static String readPassword(Scanner scanner, String prompt) {
        Console console = System.console();
        if (console == null) {
            String line = readLine(scanner, prompt);
            return line == null ? "" : line;
        }
        char[] chars = console.readPassword("%s", prompt);
        if (chars == null) return "";
        try { return new String(chars); }
        finally { Arrays.fill(chars, '\0'); }
    }

    private static boolean validUsername(String username) {
        return username != null && username.matches("[a-z][a-z0-9_]{2,19}");
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        RANDOM.nextBytes(bytes);
        return bytes;
    }

    private static String b64(byte[] bytes) { return Base64.getEncoder().encodeToString(bytes); }
    private static byte[] unb64(String data) { return Base64.getDecoder().decode(data); }

    static final class Config {
        final boolean help;
        final boolean serverMode;
        final String host;
        final int port;
        final Path dataDirectory;

        private Config(boolean help, boolean serverMode, String host, int port, Path dataDirectory) {
            this.help = help;
            this.serverMode = serverMode;
            this.host = host;
            this.port = port;
            this.dataDirectory = dataDirectory;
        }

        static Config parse(String[] args) {
            if (args.length == 1 && args[0].equals("--help")) {
                return new Config(true, true, "localhost", 0, Path.of("data"));
            }
            if (args.length < 2) throw new IllegalArgumentException("Specify --server or --client.");
            boolean server = args[0].equals("--server");
            boolean client = args[0].equals("--client");
            if (!server && !client) throw new IllegalArgumentException("Unknown mode");
            int portIndex = server ? 1 : 2;
            if (args.length <= portIndex) throw new IllegalArgumentException("Missing port");
            String host = server ? "localhost" : args[1];
            int port;
            try { port = Integer.parseInt(args[portIndex]); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("Port must be a number"); }
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be 1-65535");
            Path directory = Path.of("data");
            if (args.length > portIndex + 1) {
                if (args.length != portIndex + 3 || !args[portIndex + 1].equals("--data")) {
                    throw new IllegalArgumentException("Expected optional --data DIRECTORY");
                }
                directory = Path.of(args[portIndex + 2]);
            }
            return new Config(false, server, host, port, directory);
        }
    }

    private static void printUsage() {
        System.out.println("Usage:");
        System.out.println("  java -cp out EdmsPrototype --server 8888 [--data data]");
        System.out.println("  java -cp out EdmsPrototype --client localhost 8888 [--data data]");
        System.out.println("  java -cp out EdmsPrototype --help");
        System.out.println("Server binds to localhost only. Use two terminal windows.");
    }
}
