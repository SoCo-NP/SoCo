package ide.net;

public interface CollabCallbacks {
    // text/cursor
    void applyRemoteEdit(String path, String text);
    void applyRemoteCursor(String path, String nick, int dot, int mark);
    void onRoleInfo(String nick, String role);
    void applyRemoteViewport(String path, int line);
    void applyRemoteLaser(String path, int x, int y);

    // compile
    void onCompileGranted(String fpath, String byNick);
    void onCompileDenied(String fpath, String holder);
    void onCompileStart(String fpath, String nick);
    void onCompileOut(String fpath, String nick, String line);
    void onCompileEnd(String fpath, String nick, int exit);
    void onCompileReleased(String fpath, String nick);
}