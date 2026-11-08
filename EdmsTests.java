import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone no-dependency unit and loopback-integration test runner. */
public final class EdmsTests {
    private static int passed = 0;
    private static int failed = 0;

    @FunctionalInterface interface TestCase { void run() throws Exception; }

    private static void test(String id, String name, TestCase action) {
        try {
            action.run();
            passed++;
            System.out.println("PASS " + id + " | " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL " + id + " | " + name + " | " + e);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void reject(TestCase action) throws Exception {
        try {
            action.run();
        } catch (IllegalArgumentException | GeneralSecurityException expected) {
            return;
        }
        throw new AssertionError("Expected input/security rejection");
    }

    private static byte[] salt(int value) {
        byte[] salt = new byte[16];
        Arrays.fill(salt, (byte) value);
        return salt;
    }

    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("edms-test-");
        EdmsPrototype.AuthManager auth = new EdmsPrototype.AuthManager(temp);
        EdmsPrototype.EncryptionService crypto =
            new EdmsPrototype.EncryptionService("shared passphrase example", salt(1));

        test("T01", "normal registration succeeds", () ->
            check(auth.registerUser("alice", "password123"), "Expected registration"));
        test("T02", "duplicate registration safely rejected", () ->
            check(!auth.registerUser("alice", "password123"), "Duplicate accepted"));
        test("T03", "invalid username rejected", () ->
            reject(() -> auth.registerUser("A!", "password123")));
        test("T04", "short password rejected", () ->
            reject(() -> auth.registerUser("carol", "123")));
        test("T05", "correct login succeeds", () ->
            check(auth.authenticate("alice", "password123").isPresent(), "Login failed"));
        test("T06", "incorrect and absent logins rejected", () -> {
            check(auth.authenticate("alice", "wrongpassword").isEmpty(), "Wrong password accepted");
            check(auth.authenticate("nobody", "password123").isEmpty(), "Unknown user accepted");
        });
        test("T07", "user accounts survive restart", () ->
            check(new EdmsPrototype.AuthManager(temp)
                .authenticate("alice", "password123").isPresent(), "Account not persisted"));
        test("T08", "credential storage has salt, not plaintext", () -> {
            check(auth.registerUser("bob", "password123"), "Bob registration failed");
            String users = Files.readString(temp.resolve("users.tsv"));
            check(!users.contains("password123"), "Password leaked to file");
            String[] lines = users.split("\\R");
            check(!lines[0].split("\t")[3].equals(lines[1].split("\t")[3]),
                "Equal passwords produced equal hashes");
        });
        test("T09", "AES-GCM round trip incl. Unicode", () -> {
            String text = "Hello, Bob! 🔐 Sawubona 🇿🇦";
            check(text.equals(crypto.decrypt(crypto.encrypt(text))), "Round trip mismatch");
        });
        test("T10", "random nonce changes ciphertext", () ->
            check(!crypto.encrypt("same").equals(crypto.encrypt("same")), "Nonce reused"));
        test("T11", "malformed ciphertext rejected", () ->
            reject(() -> crypto.decrypt("Not-base64!!")));
        test("T12", "tampered authenticated ciphertext rejected", () -> {
            byte[] encrypted = java.util.Base64.getDecoder().decode(crypto.encrypt("secret"));
            encrypted[encrypted.length - 1] ^= 1;
            reject(() -> crypto.decrypt(java.util.Base64.getEncoder().encodeToString(encrypted)));
        });
        test("T13", "wrong shared passphrase rejected", () -> {
            EdmsPrototype.EncryptionService wrong =
                new EdmsPrototype.EncryptionService("different passphrase", salt(1));
            reject(() -> wrong.decrypt(crypto.encrypt("hello")));
        });
        test("T14", "message serialization survives punctuation and Unicode", () -> {
            EdmsPrototype.Message original = EdmsPrototype.Message.create(
                "alice", "bob", "a|b\t✔ and emoji 😀");
            EdmsPrototype.Message parsed = EdmsPrototype.Message.parse(original.serialize());
            check(original.messageId.equals(parsed.messageId)
                    && original.timestamp.equals(parsed.timestamp)
                    && original.content.equals(parsed.content), "Bad message serialization");
        });
        test("T15", "blank/oversize messages rejected; exact limit accepted", () -> {
            reject(() -> EdmsPrototype.Message.create("alice", "bob", "   "));
            reject(() -> EdmsPrototype.Message.create("alice", "bob", "x".repeat(2001)));
            check(EdmsPrototype.Message.create("alice", "bob", "x".repeat(2000))
                .content.length() == 2000, "Boundary rejected");
        });
        test("T16", "malformed message fields rejected", () -> {
            reject(() -> EdmsPrototype.Message.parse("bad\tmessage"));
            reject(() -> new EdmsPrototype.Message(UUID.randomUUID(), Instant.now(),
                "bad user", "bob", "hello"));
        });
        EdmsPrototype.FileStorage storage = new EdmsPrototype.FileStorage(temp);
        EdmsPrototype.Session alice = auth.authenticate("alice", "password123").orElseThrow();
        EdmsPrototype.ChatHistory history = new EdmsPrototype.ChatHistory(storage, alice);
        test("T17", "encrypted history save and reload", () -> {
            EdmsPrototype.Message message = EdmsPrototype.Message.create(
                "alice", "bob", "private sample content 123");
            history.saveMessage("SENT", message);
            EdmsPrototype.HistoryResult result = history.getMessages();
            check(result.entries.size() == 1 && result.unreadableCount == 0, "Missing history");
            check(result.entries.get(0).message.content.equals(message.content), "Wrong history content");
            String onDisk = Files.readString(storage.historyPath("alice"));
            check(!onDisk.contains("private sample"), "Plaintext stored on disk");
        });
        test("T18", "wrong history key and corrupt entry handled", () -> {
            Files.writeString(storage.historyPath("alice"), "%%%badCipher%%\n",
                StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            EdmsPrototype.HistoryResult result = history.getMessages();
            check(result.entries.size() == 1 && result.unreadableCount == 1,
                "Expected one valid plus one unreadable entry");
            EdmsPrototype.EncryptionService wrong =
                new EdmsPrototype.EncryptionService("incorrect history key", salt(9));
            check(storage.loadMessages("alice", wrong).unreadableCount == 2,
                "Wrong key could read history");
        });
        test("T19", "network loopback transfer and disconnect", () -> {
            try (EdmsPrototype.NetworkManager server = new EdmsPrototype.NetworkManager();
                 EdmsPrototype.NetworkManager client = new EdmsPrototype.NetworkManager()) {
                server.listen(0);
                AtomicReference<Throwable> serverError = new AtomicReference<>();
                Thread thread = new Thread(() -> {
                    try {
                        server.accept();
                        check("PING".equals(server.receive()), "Server received wrong frame");
                        server.send("PONG");
                        check(server.receive() == null, "Disconnect not detected");
                    } catch (Throwable e) { serverError.set(e); }
                });
                thread.start();
                client.connect("127.0.0.1", server.boundPort());
                client.send("PING");
                check("PONG".equals(client.receive()), "Client received wrong frame");
                client.close();
                thread.join(4000);
                check(!thread.isAlive(), "Server thread hung");
                if (serverError.get() != null) throw new AssertionError(serverError.get());
            }
        });
        test("T20", "invalid CLI ports and incomplete args rejected", () -> {
            reject(() -> EdmsPrototype.Config.parse(new String[]{"--server", "70000"}));
            reject(() -> EdmsPrototype.Config.parse(new String[]{"--client", "localhost"}));
            check(EdmsPrototype.Config.parse(new String[]{"--server", "8888"}).serverMode,
                "Server flags failed");
        });

        test("T21", "encrypted two-peer handshake and chat frame", () -> {
            try (EdmsPrototype.NetworkManager server = new EdmsPrototype.NetworkManager();
                 EdmsPrototype.NetworkManager client = new EdmsPrototype.NetworkManager()) {
                server.listen(0);
                AtomicReference<Throwable> serverError = new AtomicReference<>();
                Thread thread = new Thread(() -> {
                    try {
                        server.accept();
                        EdmsPrototype.PeerConnection established = EdmsPrototype.secureHandshake(
                            server, true, "alice", "shared class demo passphrase");
                        check(established.peerName.equals("bob"), "Wrong client name");
                        String frame = server.receive();
                        check(frame.startsWith("ENC|"), "Unencrypted message frame");
                        String decrypted = established.transport.decrypt(frame.substring(4));
                        check(decrypted.startsWith("MSG|"), "Wrong message type");
                        EdmsPrototype.Message msg = EdmsPrototype.Message.parse(decrypted.substring(4));
                        check(msg.sender.equals("bob") && msg.receiver.equals("alice")
                            && msg.content.equals("Testing Swing/CLI compatibility"),
                            "Wrong decrypted message");
                        server.send("ENC|" + established.transport.encrypt("ACK"));
                    } catch (Throwable e) { serverError.set(e); }
                });
                thread.start();
                client.connect("localhost", server.boundPort());
                EdmsPrototype.PeerConnection other = EdmsPrototype.secureHandshake(
                    client, false, "bob", "shared class demo passphrase");
                check(other.peerName.equals("alice"), "Wrong server name");
                EdmsPrototype.Message msg = EdmsPrototype.Message.create("bob", "alice",
                    "Testing Swing/CLI compatibility");
                client.send("ENC|" + other.transport.encrypt("MSG|" + msg.serialize()));
                check(other.transport.decrypt(client.receive().substring(4)).equals("ACK"),
                    "Acknowledgement could not be decrypted");
                thread.join(4000);
                check(!thread.isAlive(), "Encrypted socket test hung");
                if (serverError.get() != null) throw new AssertionError(serverError.get());
            }
        });
        test("T22", "mismatched peer passphrases abort handshake", () -> {
            try (EdmsPrototype.NetworkManager server = new EdmsPrototype.NetworkManager();
                 EdmsPrototype.NetworkManager client = new EdmsPrototype.NetworkManager()) {
                server.listen(0);
                AtomicReference<Boolean> rejectedServer = new AtomicReference<>(false);
                Thread thread = new Thread(() -> {
                    try {
                        server.accept();
                        reject(() -> EdmsPrototype.secureHandshake(
                            server, true, "alice", "correct shared passphrase"));
                        rejectedServer.set(true);
                    } catch (Throwable ignored) {}
                });
                thread.start();
                client.connect("localhost", server.boundPort());
                reject(() -> EdmsPrototype.secureHandshake(
                    client, false, "bob", "wrong shared passphrase"));
                thread.join(4000);
                check(!thread.isAlive() && rejectedServer.get(), "Mismatch was not rejected");
            }
        });
        test("T23", "Swing chat view renders a GUI preview headlessly", () -> {
            Path png = temp.resolve("gui-preview.png");
            EdmsGui.main(new String[]{"--preview", png.toString()});
            java.awt.image.BufferedImage img = javax.imageio.ImageIO.read(png.toFile());
            check(img != null && img.getWidth() == 1100 && img.getHeight() == 740,
                "GUI preview did not render");
            check(img.getRGB(30, 30) != img.getRGB(700, 400),
                "GUI preview layout has no visible panels");
        });

        System.out.println("----------------------------------------");
        System.out.println("RESULT: " + passed + " passed, " + failed + " failed (23 cases)");
        if (failed > 0) System.exit(1);
    }
}
