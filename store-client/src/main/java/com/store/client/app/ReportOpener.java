package com.store.client.app;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * Saves an exported report as a local .txt file and opens it in the system's plain-text editor (req 7c):
 * Notepad on Windows, TextEdit on macOS, the default handler elsewhere.
 */
public final class ReportOpener {

    private ReportOpener() { }

    /** The command that opens {@code file} on the given OS ({@code os.name}). */
    public static List<String> command(String osName, Path file) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        String path = file.toString();
        if (os.startsWith("windows")) {
            return List.of("notepad.exe", path);
        }
        if (os.startsWith("mac")) {
            return List.of("open", "-e", path);
        }
        return List.of("xdg-open", path);
    }

    /** "Notepad" or the equivalent, for button labels. */
    public static String editorName(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.startsWith("windows")) {
            return "Notepad";
        }
        if (os.startsWith("mac")) {
            return "TextEdit";
        }
        return "text editor";
    }

    public static String editorName() {
        return editorName(System.getProperty("os.name"));
    }

    /** Writes the report under the temp folder, keeping the server's file name. */
    public static Path save(String fileName, String text) throws IOException {
        return save(Path.of(System.getProperty("java.io.tmpdir"), "store-reports"), fileName, text);
    }

    /** Writes the report into {@code dir}; only the last part of {@code fileName} is used. */
    public static Path save(Path dir, String fileName, String text) throws IOException {
        Files.createDirectories(dir);
        String name = Path.of(fileName == null || fileName.isBlank() ? "report.txt" : fileName).getFileName().toString();
        Path file = dir.resolve(name.endsWith(".txt") ? name : name + ".txt");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return file;
    }

    public static void open(Path file) throws IOException {
        try {
            new ProcessBuilder(command(System.getProperty("os.name"), file)).start();
        } catch (IOException e) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(file.toFile());
            } else {
                throw e;
            }
        }
    }
}
