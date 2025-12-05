package ide.net;

/**
 * 네트워크 계층에서 수신된 이벤트를 애플리케이션 계층으로 전달하기 위한 콜백 인터페이스.
 *
 * CollabClient가 서버로부터 메시지를 수신하면, 이 인터페이스의 메소드를 호출하여
 * UI 업데이트나 로직 처리를 위임한다.
 */
public interface CollabCallbacks {

    /**
     * 원격 사용자의 텍스트 편집 내용을 반영한다.
     *
     * @param path 파일 경로
     * @param text 변경된 전체 텍스트 내용
     */
    void applyRemoteEdit(String path, String text);

    /**
     * 원격 사용자의 커서 이동을 반영한다.
     *
     * @param path 파일 경로
     * @param nick 사용자 닉네임
     * @param dot  커서 위치 (Dot)
     * @param mark 선택 영역 시작점 (Mark)
     */
    void applyRemoteCursor(String path, String nick, int dot, int mark);

    /**
     * 사용자의 역할 정보를 수신했을 때 호출된다.
     *
     * @param nick 사용자 닉네임
     * @param role 역할 문자열 (PROFESSOR, STUDENT 등)
     */
    void onRoleInfo(String nick, String role);

    /**
     * 학생으로부터 질문이 도착했을 때 호출된다 (교수자 전용).
     *
     * @param studentNick  질문한 학생의 닉네임
     * @param questionText 질문 내용
     */
    void onQuestion(String studentNick, String questionText);

    /**
     * 원격 사용자의 뷰포트(스크롤) 위치를 반영한다.
     * (Follow Me 기능 등에서 사용)
     *
     * @param path 파일 경로
     * @param line 현재 보고 있는 줄 번호
     */
    void applyRemoteViewport(String path, int line);

    /**
     * 원격 사용자의 레이저 포인터 위치를 반영한다.
     *
     * @param path 파일 경로
     * @param x    X 좌표
     * @param y    Y 좌표
     */
    void applyRemoteLaser(String path, int x, int y);
}