package com.store.client.ui.chat;

import com.store.client.app.Session;
import com.store.client.dto.Dtos.Branch;
import com.store.client.dto.Dtos.ChatEnded;
import com.store.client.dto.Dtos.ChatMessage;
import com.store.client.dto.Dtos.ChatParticipant;
import com.store.client.dto.Dtos.ChatPending;
import com.store.client.dto.Dtos.ChatRequestResult;
import com.store.client.dto.Dtos.ChatStarted;
import com.store.client.dto.Dtos.ChatSummary;
import com.store.client.dto.EventType;
import com.store.client.dto.Permission;
import com.store.client.net.ClientEventBus;
import com.store.client.net.ServerApi;
import com.store.client.ui.Async;
import com.store.client.ui.ListTableModel;
import com.store.client.ui.Ui;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.store.client.ui.ListTableModel.col;
import static com.store.client.ui.ListTableModel.num;

/**
 * Inter-branch one-on-one chat (req 9). The queue, the offers to free employees and the relaying of messages
 * all happen on the server; this panel sends the requests and shows the CHAT_* events.
 * <ul>
 *   <li>Request a chat with another branch: connected right away, or queued until someone there is free.</li>
 *   <li>A waiting request offered to this user (CHAT_PENDING_REQUEST) can be accepted or declined.</li>
 *   <li>On leaving, the user chooses whether the transcript is saved.</li>
 *   <li>Shift managers (SUPERVISE_CHAT) see the active chats and can join one.</li>
 * </ul>
 */
public final class ChatPanel extends JPanel {

    private final ServerApi api;
    private final Session session;
    private final Map<String, String> branchNames = new HashMap<>();

    private final JComboBox<Branch> target = new JComboBox<>();
    private final JButton requestButton = new JButton("Request chat");
    private final JLabel status = new JLabel("Not in a chat.");

    private final JLabel participants = new JLabel(" ");
    private final JTextArea conversation = Ui.readOnlyText(18, 50, false);
    private final JTextField message = new JTextField(40);
    private final JButton sendButton = new JButton("Send");
    private final JButton leaveButton = new JButton("Leave chat");

    private final ListTableModel<ChatSummary> active = new ListTableModel<>(
            col("Participants", s -> String.join(", ", s.participants())),
            num("Messages", ChatSummary::messageCount),
            col("Started", s -> Ui.time(s.startedAt())));
    private final JTable activeTable = Ui.table(active);

    /** The chat this user is in, or null. */
    private String sessionId;
    private List<String> currentParticipants = List.of();

    public ChatPanel(ServerApi api, ClientEventBus bus, Session session) {
        super(new BorderLayout());
        this.api = api;
        this.session = session;

        target.setRenderer(Ui.renderer((Branch b) -> b.name(), "Choose a branch"));
        requestButton.addActionListener(e -> requestChat());
        add(Ui.titled(Ui.row(new JLabel("Chat with an employee of branch:"), target, requestButton, status),
                "New chat"), BorderLayout.NORTH);

        sendButton.addActionListener(e -> send());
        message.addActionListener(e -> send());
        leaveButton.addActionListener(e -> leave());
        JPanel chat = new JPanel(new BorderLayout());
        chat.add(Ui.row(participants), BorderLayout.NORTH);
        chat.add(Ui.scroll(conversation), BorderLayout.CENTER);
        chat.add(Ui.row(message, sendButton, leaveButton), BorderLayout.SOUTH);
        add(Ui.titled(chat, "Conversation"), BorderLayout.CENTER);

        if (session.has(Permission.SUPERVISE_CHAT)) {
            JPanel supervise = new JPanel(new BorderLayout());
            supervise.add(Ui.scroll(activeTable), BorderLayout.CENTER);
            supervise.add(Ui.row(Ui.button("Refresh", e -> loadActive()), Ui.button("Join selected", e -> join())),
                    BorderLayout.SOUTH);
            supervise.setPreferredSize(new Dimension(360, 0));
            add(Ui.titled(supervise, "Active chats (shift manager)"), BorderLayout.EAST);
            loadActive();
        }

        bus.on(EventType.CHAT_PENDING_REQUEST, ChatPending.class, this::onPending);
        bus.on(EventType.CHAT_STARTED, ChatStarted.class, this::onStarted);
        bus.on(EventType.CHAT_MESSAGE, ChatMessage.class, this::onMessage);
        bus.on(EventType.CHAT_PARTICIPANT_JOINED, ChatParticipant.class, this::onJoined);
        bus.on(EventType.CHAT_PARTICIPANT_LEFT, ChatParticipant.class, this::onLeft);
        bus.on(EventType.CHAT_ENDED, ChatEnded.class, this::onEnded);

        Async.then(this, api.branches(), this::showBranches);
        updateControls();
    }

    private void showBranches(List<Branch> branches) {
        branches.forEach(b -> branchNames.put(b.id(), b.name()));
        List<Branch> others = branches.stream().filter(b -> !b.id().equals(session.branchId())).toList();
        Ui.setItems(target, others, Branch::id);
    }

    // ---------------------------------------------------------------- user actions

    private void requestChat() {
        Branch b = (Branch) target.getSelectedItem();
        if (b == null) {
            Ui.showError(this, "Choose the branch you want to chat with.");
            return;
        }
        requestButton.setEnabled(false);
        Async.then(api.requestChat(b.id()), (ChatRequestResult r) -> {
            updateControls();
            if (r.status() == ChatRequestResult.Status.CONNECTED) {
                status.setText("Connected to " + b.name() + ".");
            } else {
                status.setText("Everyone at " + b.name() + " is busy. You are number " + r.position()
                        + " in the queue; the chat opens when someone is free.");
            }
        }, error -> {
            updateControls();
            Ui.showError(this, error);
        });
    }

    private void send() {
        String text = message.getText().trim();
        if (sessionId == null || text.isEmpty()) {
            return;
        }
        message.setText("");
        // The message is shown when the server relays it back as CHAT_MESSAGE.
        Async.then(this, api.sendChat(sessionId, text), m -> { });
    }

    private void leave() {
        if (sessionId == null) {
            return;
        }
        boolean save = false;
        if (currentParticipants.size() > 2) {
            // The server only saves a transcript when the chat ends, which it won't while two others remain.
            if (!Ui.confirm(this, "Leave the chat? It continues for the other participants.")) {
                return;
            }
        } else {
            int answer = JOptionPane.showConfirmDialog(this, "Leaving ends the chat. Save the transcript?",
                    "Leave chat", JOptionPane.YES_NO_CANCEL_OPTION);
            if (answer != JOptionPane.YES_OPTION && answer != JOptionPane.NO_OPTION) {
                return;
            }
            save = answer == JOptionPane.YES_OPTION;
        }
        String leaving = sessionId;
        Async.then(this, api.leaveChat(leaving, save), v -> {
            if (leaving.equals(sessionId)) {
                append("You left the chat.");
                endLocalChat("Not in a chat.");
            }
        });
    }

    private void loadActive() {
        Async.then(this, api.activeChats(), active::setRows);
    }

    private void join() {
        if (sessionId != null) {
            Ui.showError(this, "Leave your current chat first.");
            return;
        }
        Ui.selected(activeTable, active).ifPresentOrElse(
                s -> Async.then(this, api.joinChat(s.sessionId()), v -> loadActive()),
                () -> Ui.showError(this, "Select a chat to join."));
    }

    // ---------------------------------------------------------------- server events

    private void onPending(ChatPending p) {
        bringToFront();
        String from = p.from() + " from " + branchNames.getOrDefault(p.fromBranch(), p.fromBranch());
        int answer = JOptionPane.showConfirmDialog(SwingUtilities.getWindowAncestor(this),
                from + " is waiting to chat with your branch (requested at " + Ui.time(p.requestedAt()) + ").\n"
                        + "Accept the chat?", "Chat request", JOptionPane.YES_NO_OPTION);
        if (answer == JOptionPane.YES_OPTION) {
            Async.then(this, api.acceptChat(p.requestId()), ref -> { });
        } else {
            Async.then(this, api.declineChat(p.requestId()), v -> { });
        }
    }

    private void onStarted(ChatStarted s) {
        sessionId = s.sessionId();
        conversation.setText("");
        append("Chat started with " + others(s.participants()) + ".");
        showParticipants(s.participants());
        status.setText("In a chat.");
        updateControls();
        bringToFront();
        message.requestFocusInWindow();
    }

    private void onMessage(ChatMessage m) {
        if (m.sessionId().equals(sessionId)) {
            String who = m.from().equals(session.username()) ? "You" : m.from();
            append("[" + Ui.time(m.timestamp()) + "] " + who + ": " + m.text());
        }
    }

    private void onJoined(ChatParticipant p) {
        if (p.user().equals(session.username()) && sessionId == null) {
            // A shift manager joining gets no CHAT_STARTED; this event opens the chat for them.
            sessionId = p.sessionId();
            conversation.setText("");
            append("You joined the chat of " + others(p.participants()) + ".");
            status.setText("In a chat (joined as shift manager).");
            updateControls();
        } else if (p.sessionId().equals(sessionId)) {
            append(p.user() + " (shift manager) joined the chat.");
        }
        if (p.sessionId().equals(sessionId)) {
            showParticipants(p.participants());
        }
    }

    private void onLeft(ChatParticipant p) {
        if (p.sessionId().equals(sessionId)) {
            append(p.user() + " left the chat.");
            showParticipants(p.participants());
        }
    }

    private void onEnded(ChatEnded e) {
        if (e.sessionId().equals(sessionId)) {
            append("The chat has ended.");
            endLocalChat("The last chat has ended.");
        }
    }

    // ---------------------------------------------------------------- helpers

    private void endLocalChat(String statusText) {
        sessionId = null;
        currentParticipants = List.of();
        participants.setText(" ");
        status.setText(statusText);
        updateControls();
        if (session.has(Permission.SUPERVISE_CHAT)) {
            loadActive();
        }
    }

    private void updateControls() {
        boolean inChat = sessionId != null;
        requestButton.setEnabled(!inChat);
        target.setEnabled(!inChat);
        message.setEnabled(inChat);
        sendButton.setEnabled(inChat);
        leaveButton.setEnabled(inChat);
    }

    private void showParticipants(List<String> users) {
        currentParticipants = List.copyOf(users);
        participants.setText("Participants: " + String.join(", ", users));
    }

    private String others(List<String> users) {
        List<String> rest = users.stream().filter(u -> !u.equals(session.username())).toList();
        return rest.isEmpty() ? "nobody" : String.join(", ", rest);
    }

    private void append(String line) {
        conversation.append(line + "\n");
        conversation.setCaretPosition(conversation.getDocument().getLength());
    }

    private void bringToFront() {
        JTabbedPane tabs = (JTabbedPane) SwingUtilities.getAncestorOfClass(JTabbedPane.class, this);
        if (tabs != null) {
            tabs.setSelectedComponent(this);
        }
    }
}
