package com.store.client.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReportOpenerTest {

    private static final Path FILE = Path.of("report.txt");

    @Test
    void windowsOpensNotepad() {
        assertEquals(List.of("notepad.exe", "report.txt"), ReportOpener.command("Windows 11", FILE));
        assertEquals("Notepad", ReportOpener.editorName("Windows 10"));
    }

    @Test
    void macOpensTextEdit() {
        assertEquals(List.of("open", "-e", "report.txt"), ReportOpener.command("Mac OS X", FILE));
        assertEquals("TextEdit", ReportOpener.editorName("Mac OS X"));
    }

    @Test
    void otherSystemsUseTheDefaultHandler() {
        assertEquals(List.of("xdg-open", "report.txt"), ReportOpener.command("Linux", FILE));
        assertEquals(List.of("xdg-open", "report.txt"), ReportOpener.command(null, FILE));
    }

    @Test
    void saveKeepsTheServerFileNameAndText(@TempDir Path dir) throws Exception {
        String text = "Sales report\r\nTLV   12\r\n";
        Path file = ReportOpener.save(dir, "sales-BRANCH-2026-10-30.txt", text);

        assertEquals(dir.resolve("sales-BRANCH-2026-10-30.txt"), file);
        assertEquals(text, Files.readString(file, StandardCharsets.UTF_8));
    }

    @Test
    void saveNeverWritesOutsideTheFolder(@TempDir Path dir) throws Exception {
        Path file = ReportOpener.save(dir, "../../evil", "x");
        assertEquals(dir.resolve("evil.txt"), file);
    }
}
