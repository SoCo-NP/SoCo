package ide.net;

public interface CollabCallbacks {
    // text/cursor
    void applyRemoteEdit(String path, String text);
    void applyRemoteCursor(String path, String nick, int dot, int mark);
    void onRoleInfo(String nick, String role);
    void applyRemoteViewport(String path, int line);
    void applyRemoteLaser(String path, int x, int y);

}