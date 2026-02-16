package util;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * SCD Concept: Refactoring (Separate Concerns)
 * Thread-safe utility for logging events to the console.
 */
public class Logger {
    private static JTextArea logArea;
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final int MAX_LINES = 500;
    private static boolean autoScroll = true;
    private static boolean consoleLogging = true;
    
    public static void setLogArea(JTextArea area) {
        logArea = area;
    }
    
    public static void setAutoScroll(boolean enabled) {
        autoScroll = enabled;
    }
    
    public static void setConsoleLogging(boolean enabled) {
        consoleLogging = enabled;
    }
    
    public static void log(String message) {
        String time = LocalTime.now().format(FORMATTER);
        String line = String.format("[%s] %s", time, message);

        // Console logging
        if (consoleLogging) {
            System.out.println(line);
        }
        
        // GUI logging (if available)
        if (logArea != null) {
            SwingUtilities.invokeLater(() -> {
                logArea.append(line + "\n");
                trimExcessLines();

                if (autoScroll) {
                    logArea.setCaretPosition(
                            logArea.getDocument().getLength()
                    );
                }
            });
        }
    }
    
    private static void trimExcessLines() {
        if (logArea == null) return;
        int lineCount = logArea.getLineCount();
        if (lineCount <= MAX_LINES) return;

        try {
            int end = logArea.getLineStartOffset(
                    lineCount - MAX_LINES
            );
            logArea.getDocument().remove(0, end);
        } catch (BadLocationException ignored) {
        }
    }
}
