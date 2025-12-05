package ide.app;

/**
 * 애플리케이션 계층(Application Layer)에서 정의하는 액션 인터페이스.
 * UI 컴포넌트들이 이 인터페이스를 통해 컨트롤러(CollabIDE)에게 기능을 요청한다.
 * UI는 네트워크 클라이언트에 직접 의존하지 않고 이 인터페이스를 사용한다.
 */
public interface CollabActions {

    /**
     * 서버에 접속을 시도한다.
     *
     * @param host     서버 IP 주소
     * @param port     서버 포트 번호
     * @param nickname 사용자 닉네임
     * @param role     사용자 역할 (Student, Professor)
     */
    void connect(String host, int port, String nickname, String role);

    /**
     * 서버와의 연결을 종료한다.
     */
    void disconnect();

    /**
     * 현재 서버에 연결되어 있는지 확인한다.
     *
     * @return 연결 상태라면 true, 아니면 false
     */
    boolean isConnected();

    /**
     * 현재 사용자의 닉네임을 반환한다.
     *
     * @return 닉네임 문자열
     */
    String getNickname();

    /**
     * 화면 동기화(Follow Me) 기능을 설정한다.
     * 교수자 권한에서만 사용된다.
     *
     * @param active true면 활성화, false면 비활성화
     */
    void setFollowMe(boolean active);

    /**
     * 레이저 포인터 기능을 설정한다.
     *
     * @param active true면 활성화, false면 비활성화
     */
    void setLaser(boolean active);

    /**
     * 에디터의 텍스트 변경사항(스냅샷)을 전송한다.
     *
     * @param vPath 가상 파일 경로
     * @param text  변경된 텍스트 전체
     */
    void sendSnapshot(String vPath, String text);

    /**
     * 커서 위치 정보를 전송한다.
     *
     * @param vPath 파일 경로
     * @param dot   커서 위치 (caret)
     * @param mark  선택 영역 시작점
     */
    void sendCursor(String vPath, int dot, int mark);

    /**
     * 현재 보고 있는 뷰포트 위치를 전송한다.
     * Follow Me 기능 동작 시 사용된다.
     *
     * @param vPath 파일 경로
     * @param line  현재 보고 있는 줄 번호
     */
    void sendViewport(String vPath, int line);

    /**
     * 레이저 포인터의 좌표를 전송한다.
     *
     * @param vPath 파일 경로
     * @param x     X 좌표
     * @param y     Y 좌표
     */
    void sendLaser(String vPath, int x, int y);

    /**
     * 파일 또는 디렉토리 생성을 요청한다.
     *
     * @param path  생성할 경로
     * @param isDir 디렉토리 여부 (true: 폴더, false: 파일)
     */
    void sendFileCreate(String path, boolean isDir);

    /**
     * 파일 또는 디렉토리 삭제를 요청한다.
     *
     * @param path 삭제할 경로
     */
    void sendFileDelete(String path);

    /**
     * 파일 이름을 변경한다.
     *
     * @param oldPath 기존 경로
     * @param newPath 변경할 경로
     */
    void sendFileRename(String oldPath, String newPath);

    /**
     * 현재 접속 중인 사용자들의 닉네임 목록을 반환한다.
     * 출석 체크 등에 사용된다.
     *
     * @return 접속 중인 사용자 닉네임 집합
     */
    java.util.Set<String> getConnectedUsers();

    /**
     * 현재 접속 중인 학생들의 닉네임 목록을 반환한다.
     * Role이 STUDENT인 사용자만 포함된다.
     *
     * @return 접속 중인 학생 닉네임 집합
     */
    java.util.Set<String> getConnectedStudents();
}
