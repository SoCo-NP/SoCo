package ide.app;

import java.io.File;

/**
 * Interface defined by the Application Layer.
 * The UI components use this to request actions.
 * The Controller (CollabIDE) implements this.
 */
public interface CollabActions {
    // Network
    void connect(String host, int port, String nickname, String role);

    void disconnect();

    boolean isConnected();

    String getNickname();

    // Features
    void setFollowMe(boolean active);

    void setLaser(boolean active);

    // Editor Actions (Propagated from UI)
    void sendSnapshot(String vPath, String text);

    void sendCursor(String vPath, int dot, int mark);

    void sendViewport(String vPath, int line);

    void sendLaser(String vPath, int x, int y);

    // File Actions (Propagated from UI to sync with others)
    void sendFileCreate(String path, boolean isDir);

    void sendFileDelete(String path);

    void sendFileRename(String oldPath, String newPath);
}
