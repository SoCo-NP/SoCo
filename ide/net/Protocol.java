package ide.net;

/**
 * Defines all network message constants and helper methods for the socket
 * protocol.
 * Using constants prevents typos and makes the code more readable.
 */
public class Protocol {
    // Delimiter used in messages
    public static final String DELIMITER = "\\|";
    public static final String SEPARATOR = "|";

    // --- Message Types ---

    // Connection & Auth
    public static final String JOIN = "JOIN"; // Client -> Server: JOIN|Nickname|Role
    public static final String INFO = "INFO"; // Server -> Client: INFO|Message
    public static final String ROLE_INFO = "ROLE_INFO"; // Server <-> Client: ROLE_INFO|Nickname|Role

    // Editor Actions
    public static final String EDIT = "EDIT"; // Bidirectional: EDIT|Path|Base64Content
    public static final String CURSOR = "CURSOR"; // Bidirectional: CURSOR|Path|Nickname|Dot|Mark
    public static final String VIEWPORT = "VIEWPORT"; // Bidirectional: VIEWPORT|Path|LineNumber
    public static final String LASER = "LASER"; // Bidirectional: LASER|Path|X|Y

    // File System Actions (Broadcast only)
    public static final String FILE_CREATE = "FILE_CREATE"; // FILE_CREATE|Path|IsDir|Nickname
    public static final String FILE_DELETE = "FILE_DELETE"; // FILE_DELETE|Path|Nickname
    public static final String FILE_RENAME = "FILE_RENAME"; // FILE_RENAME|OldPath|NewPath|Nickname

    // Compilation (Locking mechanism)
    public static final String COMPILE_REQ = "COMPILE_REQ"; // Client -> Server
    public static final String COMPILE_GRANTED = "COMPILE_GRANTED"; // Server -> Client
    public static final String COMPILE_DENIED = "COMPILE_DENIED"; // Server -> Client
    public static final String COMPILE_RELEASE = "COMPILE_RELEASE"; // Client -> Server

    // Compilation Output Streaming
    public static final String COMPILE_START = "COMPILE_START";
    public static final String COMPILE_OUT = "COMPILE_OUT";
    public static final String COMPILE_END = "COMPILE_END";

    /**
     * Helper to split a message line safely.
     */
    public static String[] parse(String line) {
        if (line == null)
            return new String[0];
        return line.split(DELIMITER);
    }

    /**
     * Helper to parse integer safely.
     */
    public static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
