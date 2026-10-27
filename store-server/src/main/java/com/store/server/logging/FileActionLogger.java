package com.store.server.logging;

import com.store.server.chat.ChatMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Writes {@code timestamp | ACTION | actor | details} lines to {@code actions.log}, and chat transcripts
 * to {@code chats/<sessionId>.txt}. A single background thread does all I/O, so callers never block
 * on disk and lines are never interleaved.
 */
public class FileActionLogger implements ActionLogger, AutoCloseable {

    private static final Logger LOG = Logger.getLogger(FileActionLogger.class.getName());
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** Format produced by {@code IdGenerator.next("CHAT")}. */
    private static final Pattern SESSION_ID = Pattern.compile("CHAT-[0-9a-f]{8}");

    private final Path actionsFile;
    private final Path chatsDir;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "action-logger");
        t.setDaemon(true);
        return t;
    });

    public FileActionLogger(Path logsDir) {
        this.actionsFile = logsDir.resolve("actions.log");
        this.chatsDir = logsDir.resolve("chats");
        try {
            Files.createDirectories(chatsDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create log directory " + logsDir, e);
        }
    }

    @Override
    public void log(ActionType action, String actor, String details) {
        String line = String.join(" | ", now(), action.name(), actor == null ? "-" : actor,
                details == null ? "" : details.replace('\n', ' '));
        writer.execute(() -> append(actionsFile, line + System.lineSeparator()));
    }

    @Override
    public void logChat(ChatMetadata m, String transcript) {
        log(ActionType.CHAT_ENDED, m.initiator(), "session=" + m.sessionId()
                + " participants=" + m.participants()
                + " started=" + format(m.startedAt())
                + " ended=" + format(m.endedAt())
                + " messages=" + m.messageCount()
                + " transcriptSaved=" + (transcript != null));
        if (transcript != null) {
            Path file = chatsDir.resolve(m.sessionId() + ".txt");
            writer.execute(() -> append(file, transcript));
        }
    }

    /** Runs on the writer thread, so it sees every line logged before the call and never a half-written one. */
    @Override
    public List<LogEntry> query(ActionType type, LocalDate from, LocalDate to, int limit) {
        return onWriterThread(() -> {
            if (!Files.exists(actionsFile)) {
                return List.of();
            }
            List<String> lines = Files.readAllLines(actionsFile, StandardCharsets.UTF_8);
            List<LogEntry> result = new ArrayList<>();
            for (int i = lines.size() - 1; i >= 0 && result.size() < limit; i--) {
                LogEntry e = parse(lines.get(i));
                if (e != null && matches(e, type, from, to)) {
                    result.add(e);
                }
            }
            return result;
        });
    }

    @Override
    public List<String> listTranscripts() {
        return onWriterThread(() -> {
            try (Stream<Path> files = Files.list(chatsDir)) {
                return files.filter(p -> p.getFileName().toString().endsWith(".txt"))
                        .sorted(Comparator.comparingLong(FileActionLogger::lastModified).reversed())
                        .map(p -> p.getFileName().toString().replaceFirst("\\.txt$", ""))
                        .toList();
            }
        });
    }

    @Override
    public Optional<String> readTranscript(String sessionId) {
        // Only accept real session ids, so a request can never point outside the chats folder.
        if (sessionId == null || !SESSION_ID.matcher(sessionId).matches()) {
            return Optional.empty();
        }
        Path file = chatsDir.resolve(sessionId + ".txt");
        return onWriterThread(() -> Files.exists(file)
                ? Optional.of(Files.readString(file, StandardCharsets.UTF_8))
                : Optional.<String>empty());
    }

    /** Parses {@code timestamp | ACTION | actor | details}; null for lines in another format. */
    private static LogEntry parse(String line) {
        String[] parts = line.split(" \\| ", 4);
        if (parts.length < 3) {
            return null;
        }
        try {
            return new LogEntry(parts[0], ActionType.valueOf(parts[1]), parts[2], parts.length == 4 ? parts[3] : "");
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matches(LogEntry e, ActionType type, LocalDate from, LocalDate to) {
        if (type != null && e.action() != type) {
            return false;
        }
        if (from == null && to == null) {
            return true;
        }
        try {
            LocalDate day = LocalDate.parse(e.timestamp().substring(0, 10));
            return (from == null || !day.isBefore(from)) && (to == null || !day.isAfter(to));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private <T> T onWriterThread(Callable<T> task) {
        try {
            return writer.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while reading logs", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Cannot read logs", e.getCause());
        }
    }

    /** Flushes pending writes and stops the writer thread. */
    @Override
    public void close() {
        writer.shutdown();
        try {
            writer.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void append(Path file, String text) {
        try {
            Files.writeString(file, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Failed writing " + file, e);
        }
    }

    private static String now() {
        return LocalDateTime.now().format(TS);
    }

    private static String format(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()).format(TS);
    }
}
