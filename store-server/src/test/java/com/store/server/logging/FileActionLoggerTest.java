package com.store.server.logging;

import com.store.server.chat.ChatMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FileActionLoggerTest {

    @TempDir
    Path dir;

    private FileActionLogger logger;

    @BeforeEach
    void setUp() {
        logger = new FileActionLogger(dir);
    }

    @AfterEach
    void tearDown() {
        logger.close();
    }

    @Test
    void queriesByTypeNewestFirstWithLimit() {
        logger.log(ActionType.EMPLOYEE_CREATED, "admin", "dana");
        logger.log(ActionType.SALE, "tlv.cashier", "sale=1");
        logger.log(ActionType.CUSTOMER_CREATED, "tlv.cashier", "noa");
        logger.log(ActionType.SALE, "tlv.cashier", "sale=2");

        List<LogEntry> sales = logger.query(ActionType.SALE, null, null, 10);
        assertEquals(2, sales.size());
        assertEquals("sale=2", sales.get(0).details());
        assertEquals("tlv.cashier", sales.get(0).actor());

        assertEquals(4, logger.query(null, null, null, 10).size());
        assertEquals(1, logger.query(null, null, null, 1).size());
    }

    @Test
    void filtersByDate() {
        logger.log(ActionType.LOGIN, "admin", "");
        LocalDate today = LocalDate.now();
        assertEquals(1, logger.query(null, today, today, 10).size());
        assertTrue(logger.query(null, today.plusDays(1), null, 10).isEmpty());
        assertTrue(logger.query(null, null, today.minusDays(1), 10).isEmpty());
    }

    @Test
    void savesAndReadsTranscriptsOnlyWhenRequested() {
        long now = System.currentTimeMillis();
        logger.logChat(new ChatMetadata("CHAT-0000000a", "a", List.of("a", "b"), now, now, 1), "a: hello\n");
        logger.logChat(new ChatMetadata("CHAT-0000000b", "a", List.of("a", "b"), now, now, 1), null);

        assertEquals(List.of("CHAT-0000000a"), logger.listTranscripts());
        assertEquals("a: hello\n", logger.readTranscript("CHAT-0000000a").orElseThrow());
        assertTrue(logger.readTranscript("CHAT-0000000b").isEmpty());
        assertEquals(2, logger.query(ActionType.CHAT_ENDED, null, null, 10).size(), "metadata is always logged");
    }

    @Test
    void transcriptLookupCannotLeaveTheChatsFolder() throws Exception {
        Files.writeString(dir.resolve("secret.txt"), "secret");
        assertTrue(logger.readTranscript("../secret").isEmpty());
        assertTrue(logger.readTranscript("..\\secret").isEmpty());
        assertTrue(logger.readTranscript(null).isEmpty());
    }
}
