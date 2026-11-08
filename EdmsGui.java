import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.AEADBadTagException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;

/**
 * Group 27 Swing desktop user interface for the same EDMS services/protocol.
 * Run with: java -cp out EdmsGui [--data demo-data]
 */
public final class EdmsGui {
    private static final Color NAVY = new Color(18, 35, 56);
    private static final Color NAVY_LIGHT = new Color(31, 55, 80);
    private static final Color TEAL = new Color(17, 126, 132);
    private static final Color TEAL_DARK = new Color(11, 99, 105);
    private static final Color SKY = new Color(236, 248, 247);
    private static final Color BG = new Color(246, 248, 252);
    private static final Color INK = new Color(30, 46, 61);
    private static final Color MUTED = new Color(104, 124, 139);
    private static final Color BORDER = new Color(219, 229, 234);
    private static final Font FONT = new Font("SansSerif", Font.PLAIN, 14);
    private static final DateTimeFormatter TIME = DateTimeFormatter
        .ofPattern("HH:mm").withZone(ZoneId.systemDefault());

    private final Path dataDirectory;
    private final JFrame frame;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel(cardLayout);
    private final JLabel sideStatus = new JLabel("Not signed in");
    private final EdmsPrototype.AuthManager auth;
    private final EdmsPrototype.FileStorage storage;
    private final EdmsPrototype.AuditLogger audit;

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JPasswordField confirmField;
    private JPanel confirmRow;
    private JButton authButton;
    private JLabel authHint;
    private boolean registering;

    private JRadioButton serverChoice;
    private JTextField hostField;
    private JTextField portField;
    private JPasswordField sharedField;
    private JButton connectButton;
    private JButton cancelButton;
    private JLabel connectStatus;
    private EdmsPrototype.Session currentUser;
    private EdmsPrototype.ChatHistory history;
    private volatile EdmsPrototype.NetworkManager network;
    private volatile EdmsPrototype.NetworkManager pendingNetwork;
    private EdmsPrototype.EncryptionService transport;
    private volatile String peerName;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private SwingWorker<EdmsPrototype.PeerConnection, String> connectWorker;
    private ChatPanel chat;

    private EdmsGui(Path dataDirectory) throws IOException {
        this.dataDirectory = dataDirectory;
        this.auth = new EdmsPrototype.AuthManager(dataDirectory);
        this.storage = new EdmsPrototype.FileStorage(dataDirectory);
        this.audit = new EdmsPrototype.AuditLogger(dataDirectory);

        frame = new JFrame("EDMS | Encrypted Direct Messaging - Group 27");
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setMinimumSize(new Dimension(950, 640));
        frame.setSize(1100, 740);
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { shutdown(); }
        });

        JPanel shell = new JPanel(new BorderLayout());
        shell.setBackground(BG);
        shell.add(sidebar(sideStatus), BorderLayout.WEST);
        pages.setOpaque(false);
        pages.add(scrollPage(buildAuthPage()), "auth");
        pages.add(scrollPage(buildConnectPage()), "connect");
        shell.add(pages, BorderLayout.CENTER);
        frame.setContentPane(shell);
        showPage("auth");
    }

    private void showPage(String name) { cardLayout.show(pages, name); }

    private static JPanel sidebar(JLabel status) {
        JPanel side = new JPanel();
        side.setBackground(NAVY);
        side.setPreferredSize(new Dimension(258, 700));
        side.setLayout(new BorderLayout());
        side.setBorder(new EmptyBorder(34, 25, 26, 25));

        JPanel upper = new JPanel();
        upper.setBackground(NAVY);
        upper.setLayout(new BoxLayout(upper, BoxLayout.Y_AXIS));
        JLabel logo = label("◈ EDMS", new Font("SansSerif", Font.BOLD, 26), Color.WHITE);
        JLabel subtitle = label("SECURE MESSAGING", new Font("SansSerif", Font.BOLD, 10),
            new Color(120, 211, 208));
        upper.add(logo);
        upper.add(Box.createVerticalStrut(2));
        upper.add(subtitle);
        upper.add(Box.createVerticalStrut(42));
        upper.add(label("YOUR WORKSPACE", new Font("SansSerif", Font.BOLD, 10),
            new Color(143, 165, 183)));
        upper.add(Box.createVerticalStrut(20));
        upper.add(sideLine("01", "Your account"));
        upper.add(Box.createVerticalStrut(19));
        upper.add(sideLine("02", "Secure connection"));
        upper.add(Box.createVerticalStrut(19));
        upper.add(sideLine("03", "Private conversation"));
        upper.add(Box.createVerticalStrut(36));

        RoundedPanel statusCard = new RoundedPanel(NAVY_LIGHT, 16);
        statusCard.setLayout(new BoxLayout(statusCard, BoxLayout.Y_AXIS));
        statusCard.setBorder(new EmptyBorder(17, 15, 17, 15));
        statusCard.setPreferredSize(new Dimension(208, 80));
        statusCard.setMinimumSize(new Dimension(208, 80));
        statusCard.setMaximumSize(new Dimension(208, 80));
        statusCard.setAlignmentX(Component.LEFT_ALIGNMENT);
        statusCard.add(label("●  SESSION", new Font("SansSerif", Font.BOLD, 11),
            new Color(125, 217, 202)));
        statusCard.add(Box.createVerticalStrut(7));
        status.setFont(new Font("SansSerif", Font.PLAIN, 12));
        status.setForeground(Color.WHITE);
        statusCard.add(status);
        upper.add(statusCard);
        side.add(upper, BorderLayout.NORTH);

        JPanel lower = new JPanel();
        lower.setBackground(NAVY);
        lower.setLayout(new BoxLayout(lower, BoxLayout.Y_AXIS));
        lower.add(label("GROUP 27  •  SOFTWARE DESIGN II",
            new Font("SansSerif", Font.BOLD, 10), new Color(159, 178, 194)));
        lower.add(Box.createVerticalStrut(7));
        lower.add(label("Java 17 desktop edition", FONT.deriveFont(11f),
            new Color(159, 178, 194)));
        side.add(lower, BorderLayout.SOUTH);
        return side;
    }

    private static JPanel sideLine(String number, String text) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setBackground(NAVY);
        row.add(label(number, new Font("SansSerif", Font.BOLD, 12),
            new Color(120, 211, 208)));
        row.add(label(text, FONT.deriveFont(13f), new Color(230, 237, 244)));
        return row;
    }

    private JPanel buildAuthPage() {
        JPanel screen = pageWithCenter();
        JPanel card = formCard();
        card.add(label("WELCOME TO EDMS", new Font("SansSerif", Font.BOLD, 12), TEAL));
        card.add(Box.createVerticalStrut(7));
        card.add(label("Your messages. Your privacy.",
            new Font("SansSerif", Font.BOLD, 25), INK));
        card.add(Box.createVerticalStrut(8));
        card.add(label("Sign in or create an account to begin.", FONT, MUTED));
        card.add(Box.createVerticalStrut(27));

        usernameField = new JTextField();
        passwordField = new JPasswordField();
        confirmField = new JPasswordField();
        card.add(field("Username", usernameField));
        card.add(Box.createVerticalStrut(15));
        card.add(field("Password", passwordField));
        card.add(Box.createVerticalStrut(15));
        confirmRow = field("Confirm password", confirmField);
        card.add(confirmRow);
        card.add(Box.createVerticalStrut(17));

        authHint = label("", FONT.deriveFont(12f), MUTED);
        card.add(authHint);
        card.add(Box.createVerticalStrut(12));
        authButton = primary("Sign in");
        authButton.addActionListener(e -> submitAuth());
        card.add(authButton);
        card.add(Box.createVerticalStrut(14));
        JButton toggle = linkButton("New here? Create an account");
        toggle.addActionListener(e -> {
            registering = !registering;
            confirmRow.setVisible(registering);
            authButton.setText(registering ? "Create account" : "Sign in");
            toggle.setText(registering ? "Already registered? Sign in"
                : "New here? Create an account");
            authHint.setText(registering ? "Username: 3-20 lowercase characters; password: 8+"
                : "Enter your existing username and password.");
            card.revalidate();
        });
        card.add(toggle);
        confirmRow.setVisible(false);
        authHint.setText("Enter your existing username and password.");
        screen.add(card);
        return screen;
    }

    private JPanel buildConnectPage() {
        JPanel screen = pageWithCenter();
        JPanel card = formCard();
        card.add(label("STEP 02  /  CONNECT", new Font("SansSerif", Font.BOLD, 12), TEAL));
        card.add(Box.createVerticalStrut(7));
        card.add(label("Start a secure conversation",
            new Font("SansSerif", Font.BOLD, 24), INK));
        card.add(Box.createVerticalStrut(8));
        card.add(label("Use one server and one client on this computer.", FONT, MUTED));
        card.add(Box.createVerticalStrut(23));

        JPanel mode = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 0));
        mode.setOpaque(false);
        serverChoice = new JRadioButton("Start server", true);
        JRadioButton clientChoice = new JRadioButton("Join as client", false);
        for (JRadioButton radio : new JRadioButton[]{serverChoice, clientChoice}) {
            radio.setOpaque(false);
            radio.setFont(FONT.deriveFont(Font.BOLD));
            radio.setForeground(INK);
        }
        ButtonGroup group = new ButtonGroup();
        group.add(serverChoice);
        group.add(clientChoice);
        mode.add(serverChoice);
        mode.add(clientChoice);
        card.add(mode);
        card.add(Box.createVerticalStrut(20));

        hostField = new JTextField("localhost");
        portField = new JTextField("8888");
        sharedField = new JPasswordField();
        card.add(field("Server address", hostField));
        card.add(Box.createVerticalStrut(12));
        card.add(field("Port", portField));
        card.add(Box.createVerticalStrut(12));
        card.add(field("Shared chat passphrase (12+ characters)", sharedField));
        card.add(Box.createVerticalStrut(14));

        JLabel explanation = label("Both users must enter the same private chat passphrase.",
            FONT.deriveFont(12f), MUTED);
        card.add(explanation);
        card.add(Box.createVerticalStrut(13));
        connectStatus = label("Ready to establish an encrypted connection.",
            FONT.deriveFont(12f), MUTED);
        card.add(connectStatus);
        card.add(Box.createVerticalStrut(15));
        connectButton = primary("Start listening");
        connectButton.addActionListener(e -> startConnection());
        card.add(connectButton);
        card.add(Box.createVerticalStrut(8));
        cancelButton = linkButton("Cancel waiting");
        cancelButton.addActionListener(e -> cancelConnection());
        cancelButton.setEnabled(false);
        card.add(cancelButton);
        card.add(Box.createVerticalStrut(9));
        JButton logout = linkButton("Sign out of account");
        logout.addActionListener(e -> logout());
        card.add(logout);
        serverChoice.addActionListener(e -> updateMode());
        clientChoice.addActionListener(e -> updateMode());
        updateMode();
        screen.add(card);
        return screen;
    }

    private void updateMode() {
        if (serverChoice == null) return;
        hostField.setEnabled(!serverChoice.isSelected());
        hostField.setBackground(serverChoice.isSelected() ? BG : Color.WHITE);
        connectButton.setText(serverChoice.isSelected() ? "Start listening" : "Connect to peer");
    }

    private static JPanel pageWithCenter() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(BG);
        panel.setBorder(new EmptyBorder(30, 35, 30, 35));
        return panel;
    }

    private static JScrollPane scrollPage(JPanel page) {
        JScrollPane scroll = new JScrollPane(page,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private static JPanel formCard() {
        RoundedPanel card = new RoundedPanel(Color.WHITE, 22);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(35, 40, 35, 40));
        card.setPreferredSize(new Dimension(555, 635));
        return card;
    }

    private static JPanel field(String caption, JComponent input) {
        JPanel row = new JPanel();
        row.setOpaque(false);
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        JLabel title = label(caption, new Font("SansSerif", Font.BOLD, 12), INK);
        row.add(title);
        row.add(Box.createVerticalStrut(6));
        input.setFont(FONT);
        input.setForeground(INK);
        input.setMaximumSize(new Dimension(475, 42));
        input.setPreferredSize(new Dimension(475, 42));
        input.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1, true),
            new EmptyBorder(8, 11, 8, 11)));
        row.add(input);
        return row;
    }

    private void submitAuth() {
        String username = usernameField.getText().trim();
        char[] raw = passwordField.getPassword();
        String password = new String(raw);
        Arrays.fill(raw, '\0');
        String confirmation = null;
        if (registering) {
            char[] check = confirmField.getPassword();
            confirmation = new String(check);
            Arrays.fill(check, '\0');
            if (!password.equals(confirmation)) {
                error("The two passwords do not match.");
                return;
            }
        }
        authButton.setEnabled(false);
        authHint.setText("Checking account...");
        final boolean create = registering;
        new SwingWorker<EdmsPrototype.Session, Void>() {
            @Override protected EdmsPrototype.Session doInBackground() throws Exception {
                if (create && !auth.registerUser(username, password)) {
                    throw new IllegalArgumentException("Username already registered. Please sign in.");
                }
                return auth.authenticate(username, password)
                    .orElseThrow(() -> new IllegalArgumentException("Invalid username or password."));
            }

            @Override protected void done() {
                authButton.setEnabled(true);
                try {
                    currentUser = get();
                    history = new EdmsPrototype.ChatHistory(storage, currentUser);
                    audit.info("GUI login succeeded: " + currentUser.user.username);
                    sideStatus.setText(currentUser.user.username + " signed in");
                    passwordField.setText("");
                    confirmField.setText("");
                    authHint.setText("Account ready.");
                    showPage("connect");
                } catch (Exception e) {
                    audit.warning("GUI login/registration failed");
                    authHint.setText("Please check the entered details.");
                    error(friendly(e));
                }
            }
        }.execute();
    }

    private void startConnection() {
        if (currentUser == null) return;
        final int port;
        try {
            port = Integer.parseInt(portField.getText().trim());
            if (port < 1 || port > 65535) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            error("Enter a port between 1 and 65535.");
            return;
        }
        final String host = hostField.getText().trim();
        final boolean server = serverChoice.isSelected();
        final String localName = currentUser.user.username;
        if (!server && host.isBlank()) {
            error("Enter the server hostname (use localhost for this demo).");
            return;
        }
        char[] raw = sharedField.getPassword();
        final String passphrase = new String(raw);
        Arrays.fill(raw, '\0');
        if (passphrase.length() < 12) {
            error("The shared chat passphrase must have at least 12 characters.");
            return;
        }
        connectButton.setEnabled(false);
        cancelButton.setEnabled(true);
        serverChoice.setEnabled(false);
        connectStatus.setText(server ? "Opening listener..." : "Connecting securely...");
        final EdmsPrototype.NetworkManager manager = new EdmsPrototype.NetworkManager();
        pendingNetwork = manager;
        connectWorker = new SwingWorker<EdmsPrototype.PeerConnection, String>() {
            @Override protected EdmsPrototype.PeerConnection doInBackground() throws Exception {
                if (server) {
                    manager.listen(port);
                    publish("Waiting for a peer on localhost:" + manager.boundPort() + " ...");
                    manager.accept();
                } else {
                    manager.connect(host, port);
                }
                publish("Verifying the encrypted handshake...");
                return EdmsPrototype.secureHandshake(manager, server, localName, passphrase);
            }

            @Override protected void process(java.util.List<String> updates) {
                connectStatus.setText(updates.get(updates.size() - 1));
            }

            @Override protected void done() {
                connectButton.setEnabled(true);
                cancelButton.setEnabled(false);
                serverChoice.setEnabled(true);
                try {
                    if (isCancelled()) return;
                    EdmsPrototype.PeerConnection connected = get();
                    network = manager;
                    pendingNetwork = null;
                    transport = connected.transport;
                    peerName = connected.peerName;
                    active.set(true);
                    sharedField.setText("");
                    audit.info("GUI encrypted peer connection established");
                    openChat();
                    receiveInBackground();
                } catch (Exception e) {
                    manager.close();
                    pendingNetwork = null;
                    connectStatus.setText("Connection failed. Verify the peer and try again.");
                    String message = friendly(e);
                    if (rootCause(e) instanceof AEADBadTagException) {
                        message = "Shared passphrases differ, or the encrypted handshake was altered.";
                    }
                    error(message);
                }
            }
        };
        connectWorker.execute();
    }

    private void cancelConnection() {
        if (connectWorker != null) connectWorker.cancel(true);
        if (pendingNetwork != null) pendingNetwork.close();
        pendingNetwork = null;
        cancelButton.setEnabled(false);
        connectButton.setEnabled(true);
        serverChoice.setEnabled(true);
        connectStatus.setText("Waiting cancelled. Choose a connection again.");
    }

    private void openChat() {
        if (chat != null) pages.remove(chat.panel);
        chat = new ChatPanel(currentUser.user.username, peerName);
        chat.send.addActionListener(e -> sendMessage());
        chat.composer.getInputMap().put(
            javax.swing.KeyStroke.getKeyStroke("ctrl ENTER"), "send-message");
        chat.composer.getActionMap().put("send-message", new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { sendMessage(); }
        });
        chat.history.addActionListener(e -> showHistory());
        chat.disconnect.addActionListener(e -> disconnect());
        chat.signOut.addActionListener(e -> logout());
        pages.add(chat.panel, "chat");
        sideStatus.setText(currentUser.user.username + " online");
        showPage("chat");
    }

    private void sendMessage() {
        if (!active.get() || chat == null) return;
        String text = chat.composer.getText();
        final EdmsPrototype.Message message;
        try {
            message = EdmsPrototype.Message.create(currentUser.user.username, peerName, text);
        } catch (IllegalArgumentException e) {
            error("Message must contain 1-2,000 characters and cannot be blank.");
            return;
        }
        chat.send.setEnabled(false);
        chat.composer.setEditable(false);
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() throws Exception {
                network.send("ENC|" + transport.encrypt("MSG|" + message.serialize()));
                history.saveMessage("SENT", message);
                audit.info("GUI outgoing encrypted message stored");
                return null;
            }
            @Override protected void done() {
                chat.send.setEnabled(active.get());
                chat.composer.setEditable(active.get());
                try {
                    get();
                    chat.appendBubble(message, true);
                    chat.composer.setText("");
                    chat.composer.requestFocusInWindow();
                } catch (Exception e) {
                    error("Could not send message: " + friendly(e));
                }
            }
        }.execute();
    }

    private void receiveInBackground() {
        final EdmsPrototype.NetworkManager manager = network;
        final String expectedPeer = peerName;
        final String local = currentUser.user.username;
        final EdmsPrototype.EncryptionService cipher = transport;
        final EdmsPrototype.ChatHistory localHistory = history;
        Thread thread = new Thread(() -> {
            String reason = "Peer disconnected.";
            try {
                while (active.get()) {
                    String frame = manager.receive();
                    if (frame == null) break;
                    if (!frame.startsWith("ENC|")) {
                        audit.warning("GUI rejected unencrypted network frame");
                        continue;
                    }
                    String clear = cipher.decrypt(frame.substring(4));
                    if (!clear.startsWith("MSG|")) {
                        audit.warning("GUI rejected unexpected encrypted frame");
                        continue;
                    }
                    EdmsPrototype.Message incoming = EdmsPrototype.Message.parse(clear.substring(4));
                    if (!incoming.sender.equals(expectedPeer) || !incoming.receiver.equals(local)) {
                        audit.warning("GUI rejected incorrect message participants");
                        continue;
                    }
                    localHistory.saveMessage("RECEIVED", incoming);
                    audit.info("GUI incoming encrypted message stored");
                    SwingUtilities.invokeLater(() -> {
                        if (chat != null && active.get()) chat.appendBubble(incoming, false);
                    });
                }
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                reason = "Encrypted message verification failed. Connection closed.";
                audit.warning("GUI incoming encrypted message failed verification");
            } catch (IOException e) {
                reason = "Network connection interrupted.";
            } finally {
                active.set(false);
                manager.close();
                final String banner = reason;
                SwingUtilities.invokeLater(() -> {
                    if (chat != null && network == manager) chat.markDisconnected(banner);
                });
            }
        }, "edms-gui-receiver");
        thread.setDaemon(true);
        thread.start();
    }

    private void showHistory() {
        if (history == null) return;
        try {
            EdmsPrototype.HistoryResult saved = history.getMessages();
            StringBuilder body = new StringBuilder();
            for (EdmsPrototype.HistoryEntry entry : saved.entries) {
                EdmsPrototype.Message m = entry.message;
                body.append("[").append(m.timestamp).append("] ")
                    .append(entry.direction).append("  ").append(m.sender)
                    .append(" -> ").append(m.receiver).append("\n")
                    .append(m.content).append("\n\n");
            }
            if (saved.entries.isEmpty()) body.append("No messages saved yet.");
            if (saved.unreadableCount > 0) body.append("\nWarning: ")
                .append(saved.unreadableCount).append(" unreadable records were skipped.");
            JTextArea area = new JTextArea(body.toString(), 22, 62);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setFont(FONT);
            area.setMargin(new Insets(15, 15, 15, 15));
            JOptionPane.showMessageDialog(frame, new JScrollPane(area),
                "Encrypted local chat history - " + currentUser.user.username,
                JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) { error("Could not load chat history: " + e.getMessage()); }
    }

    private void disconnect() {
        active.set(false);
        if (network != null) network.close();
        network = null;
        peerName = null;
        transport = null;
        if (currentUser != null) {
            sideStatus.setText(currentUser.user.username + " signed in");
            connectStatus.setText("Disconnected. Start a new secure chat.");
            showPage("connect");
        }
        audit.info("GUI session disconnected");
    }

    private void logout() {
        cancelConnection();
        disconnect();
        currentUser = null;
        history = null;
        usernameField.setText("");
        passwordField.setText("");
        confirmField.setText("");
        sideStatus.setText("Not signed in");
        showPage("auth");
    }

    private void shutdown() {
        cancelConnection();
        if (network != null) network.close();
        active.set(false);
        audit.close();
        frame.dispose();
    }

    private void error(String text) {
        JOptionPane.showMessageDialog(frame, text, "EDMS", JOptionPane.WARNING_MESSAGE);
    }

    private static Throwable rootCause(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null) root = root.getCause();
        return root;
    }

    private static String friendly(Throwable throwable) {
        Throwable root = rootCause(throwable);
        if (root instanceof java.util.concurrent.CancellationException) return "Operation cancelled.";
        return root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        label.setForeground(color);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static RoundButton primary(String title) {
        RoundButton button = new RoundButton(title, TEAL, Color.WHITE);
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        button.setPreferredSize(new Dimension(400, 46));
        return button;
    }

    private static JButton linkButton(String title) {
        JButton button = new JButton(title);
        button.setBorder(new EmptyBorder(8, 0, 8, 0));
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setForeground(TEAL);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        return button;
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;")
            .replace(">", "&gt;").replace("\n", "<br>");
    }

    private static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final int arc;
        RoundedPanel(Color fill, int arc) {
            this.fill = fill;
            this.arc = arc;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(fill);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class RoundButton extends JButton {
        private final Color fill;
        RoundButton(String title, Color fill, Color foreground) {
            super(title);
            this.fill = fill;
            setForeground(foreground);
            setFont(new Font("SansSerif", Font.BOLD, 13));
            setFocusPainted(false);
            setBorder(new EmptyBorder(11, 18, 11, 18));
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(isEnabled() ? (getModel().isRollover() ? fill.darker() : fill) : BORDER);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 15, 15);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class ChatPanel {
        final JPanel panel;
        final JPanel messageList;
        final JScrollPane scroll;
        final JTextArea composer;
        final RoundButton send;
        final JButton history;
        final JButton disconnect;
        final JButton signOut;
        final JLabel connectionState;

        ChatPanel(String local, String peer) {
            panel = new JPanel(new BorderLayout(0, 14));
            panel.setBackground(BG);
            panel.setBorder(new EmptyBorder(22, 28, 24, 28));

            RoundedPanel header = new RoundedPanel(Color.WHITE, 18);
            header.setLayout(new BorderLayout());
            header.setBorder(new EmptyBorder(17, 22, 17, 22));
            JPanel identity = new JPanel();
            identity.setOpaque(false);
            identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
            identity.add(label("CHAT WITH " + peer.toUpperCase(),
                new Font("SansSerif", Font.BOLD, 11), TEAL));
            identity.add(Box.createVerticalStrut(4));
            identity.add(label(peer, new Font("SansSerif", Font.BOLD, 24), INK));
            identity.add(Box.createVerticalStrut(3));
            connectionState = label("●  Encrypted connection active", FONT.deriveFont(12f), TEAL);
            identity.add(connectionState);
            header.add(identity, BorderLayout.WEST);
            JPanel tools = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
            tools.setOpaque(false);
            history = linkButton("View history");
            disconnect = linkButton("Disconnect");
            signOut = linkButton("Sign out");
            tools.add(history);
            tools.add(disconnect);
            tools.add(signOut);
            header.add(tools, BorderLayout.EAST);
            panel.add(header, BorderLayout.NORTH);

            messageList = new JPanel();
            messageList.setBackground(BG);
            messageList.setLayout(new BoxLayout(messageList, BoxLayout.Y_AXIS));
            messageList.setBorder(new EmptyBorder(19, 10, 10, 10));
            JPanel messageContainer = new JPanel(new BorderLayout());
            messageContainer.setBackground(BG);
            messageContainer.add(messageList, BorderLayout.NORTH);
            scroll = new JScrollPane(messageContainer);
            scroll.setBorder(BorderFactory.createEmptyBorder());
            scroll.getViewport().setBackground(BG);
            scroll.getVerticalScrollBar().setUnitIncrement(14);
            panel.add(scroll, BorderLayout.CENTER);

            RoundedPanel composeBox = new RoundedPanel(Color.WHITE, 18);
            composeBox.setLayout(new BorderLayout(13, 8));
            composeBox.setBorder(new EmptyBorder(13, 17, 12, 14));
            composer = new JTextArea(3, 38);
            composer.setFont(FONT);
            composer.setForeground(INK);
            composer.setLineWrap(true);
            composer.setWrapStyleWord(true);
            composer.setOpaque(false);
            composer.setBorder(new EmptyBorder(8, 6, 8, 6));
            composeBox.add(new JScrollPane(composer,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
                    setBorder(null);
                    setOpaque(false);
                    getViewport().setOpaque(false);
                }}, BorderLayout.CENTER);
            send = new RoundButton("Send message  →", TEAL, Color.WHITE);
            send.setPreferredSize(new Dimension(167, 50));
            composeBox.add(send, BorderLayout.EAST);
            JPanel footer = new JPanel(new BorderLayout(0, 8));
            footer.setOpaque(false);
            footer.add(composeBox, BorderLayout.CENTER);
            footer.add(label("Messages are encrypted before sending  •  Ctrl+Enter to send",
                FONT.deriveFont(11f), MUTED), BorderLayout.SOUTH);
            panel.add(footer, BorderLayout.SOUTH);
        }

        void appendBubble(EdmsPrototype.Message message, boolean sent) {
            JPanel row = new JPanel(new BorderLayout());
            row.setOpaque(false);
            RoundedPanel bubble = new RoundedPanel(sent ? TEAL : Color.WHITE, 17);
            bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
            bubble.setBorder(new EmptyBorder(13, 18, 12, 18));
            Color text = sent ? Color.WHITE : INK;
            bubble.add(label(sent ? "YOU" : message.sender.toUpperCase(),
                new Font("SansSerif", Font.BOLD, 11), sent ? new Color(210, 252, 246) : TEAL));
            bubble.add(Box.createVerticalStrut(6));
            JLabel body = label("<html><div style='width: 330px'>" + escape(message.content)
                + "</div></html>", FONT, text);
            bubble.add(body);
            bubble.add(Box.createVerticalStrut(7));
            bubble.add(label(TIME.format(message.timestamp), FONT.deriveFont(10f),
                sent ? new Color(218, 251, 248) : MUTED));
            row.add(bubble, sent ? BorderLayout.EAST : BorderLayout.WEST);
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                bubble.getPreferredSize().height + 4));
            messageList.add(row);
            messageList.add(Box.createVerticalStrut(12));
            messageList.revalidate();
            messageList.repaint();
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar()
                .setValue(scroll.getVerticalScrollBar().getMaximum()));
        }

        void markDisconnected(String message) {
            connectionState.setText("●  " + message);
            connectionState.setForeground(new Color(182, 100, 64));
            composer.setEditable(false);
            send.setEnabled(false);
        }
    }

    /** Headless image of the actual Swing chat layout for the user guide. */
    private static void renderPreview(Path imagePath) throws IOException {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        root.add(sidebar(new JLabel("alice online")), BorderLayout.WEST);
        ChatPanel conversation = new ChatPanel("alice", "bob");
        conversation.appendBubble(EdmsPrototype.Message.create("bob", "alice",
            "Hello Alice! Our secure chat is working."), false);
        conversation.appendBubble(EdmsPrototype.Message.create("alice", "bob",
            "That looks great. The messages are encrypted before they travel."), true);
        conversation.appendBubble(EdmsPrototype.Message.create("bob", "alice",
            "Let's show the chat history and tests in our demo!"), false);
        conversation.composer.setText("Type a message to Bob...");
        root.add(conversation.panel, BorderLayout.CENTER);
        root.setSize(1100, 740);
        layoutTree(root);
        BufferedImage image = new BufferedImage(1100, 740, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        root.printAll(graphics);
        graphics.dispose();
        ImageIO.write(image, "png", imagePath.toFile());
    }

    private static void layoutTree(Container root) {
        root.doLayout();
        for (Component child : root.getComponents()) {
            if (child instanceof Container) layoutTree((Container) child);
        }
    }

    public static void main(String[] args) {
        if (args.length == 2 && args[0].equals("--preview")) {
            try { renderPreview(Path.of(args[1])); }
            catch (IOException e) { System.err.println("Preview failed: " + e.getMessage()); }
            return;
        }
        Path data = Path.of("data");
        if (args.length == 2 && args[0].equals("--data")) data = Path.of(args[1]);
        else if (args.length != 0) {
            System.err.println("Usage: java -cp out EdmsGui [--data DIRECTORY]");
            return;
        }
        final Path directory = data;
        UIManager.put("OptionPane.messageFont", FONT);
        UIManager.put("OptionPane.buttonFont", FONT);
        SwingUtilities.invokeLater(() -> {
            try { new EdmsGui(directory).frame.setVisible(true); }
            catch (IOException e) {
                JOptionPane.showMessageDialog(null, "Unable to start EDMS: " + e.getMessage());
            }
        });
    }
}
