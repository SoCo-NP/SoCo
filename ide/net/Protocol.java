package ide.net;

/**
 * 소켓 통신 프로토콜에 사용되는 메시지 상수와 헬퍼 메소드를 정의하는 클래스.
 *
 * 클라이언트와 서버 간의 통신 규약을 상수로 관리하여 오타를 방지하고 가독성을 높인다.
 * 각 메시지는 파이프('|') 문자로 구분된 문자열 형태를 가진다.
 */
public class Protocol {
    // 메시지 구분자
    public static final String DELIMITER = "\\|";
    public static final String SEPARATOR = "|";

    // --- 메시지 타입 (Message Types) ---

    // 연결 및 인증 (Connection & Auth)
    /** 클라이언트 -> 서버: 입장 요청 (JOIN|Nickname|Role) */
    public static final String JOIN = "JOIN";
    /** 서버 -> 클라이언트: 시스템 메시지 (INFO|Message) */
    public static final String INFO = "INFO";
    /** 서버 <-> 클라이언트: 역할 정보 교환 (ROLE_INFO|Nickname|Role) */
    public static final String ROLE_INFO = "ROLE_INFO";

    // 질문 관련 (Student -> Professor via Server)
    /** 학생 -> 서버 -> 교수자: 질문 전송 (QUESTION|StudentNick|Base64QuestionText) */
    public static final String QUESTION = "QUESTION";

    // 에디터 액션 (Editor Actions)
    /** 양방향: 텍스트 편집 (EDIT|Path|Base64Content) */
    public static final String EDIT = "EDIT";
    /** 양방향: 커서 이동 (CURSOR|Path|Nickname|Dot|Mark) */
    public static final String CURSOR = "CURSOR";
    /** 양방향: 뷰포트 스크롤 (VIEWPORT|Path|LineNumber) */
    public static final String VIEWPORT = "VIEWPORT";
    /** 양방향: 레이저 포인터 (LASER|Path|X|Y) */
    public static final String LASER = "LASER";

    // 파일 시스템 액션 (브로드캐스트 전용)
    /** 파일 생성 알림 (FILE_CREATE|Path|IsDir|Nickname) */
    public static final String FILE_CREATE = "FILE_CREATE";
    /** 파일 삭제 알림 (FILE_DELETE|Path|Nickname) */
    public static final String FILE_DELETE = "FILE_DELETE";
    /** 파일 이름 변경 알림 (FILE_RENAME|OldPath|NewPath|Nickname) */
    public static final String FILE_RENAME = "FILE_RENAME";

    // 컴파일 및 실행 제어 (Locking mechanism)
    /** 클라이언트 -> 서버: 컴파일 권한 요청 */
    public static final String COMPILE_REQ = "COMPILE_REQ";
    /** 서버 -> 클라이언트: 컴파일 권한 승인 */
    public static final String COMPILE_GRANTED = "COMPILE_GRANTED";
    /** 서버 -> 클라이언트: 컴파일 권한 거부 (다른 사용자가 사용 중) */
    public static final String COMPILE_DENIED = "COMPILE_DENIED";
    /** 클라이언트 -> 서버: 컴파일 권한 반납 */
    public static final String COMPILE_RELEASE = "COMPILE_RELEASE";

    // 컴파일 출력 스트리밍 (Compilation Output Streaming)
    public static final String COMPILE_START = "COMPILE_START";
    public static final String COMPILE_OUT = "COMPILE_OUT";
    public static final String COMPILE_END = "COMPILE_END";

    /**
     * 수신된 메시지 라인을 구분자로 분리한다.
     *
     * @param line 수신된 원본 메시지 문자열
     * @return 분리된 문자열 배열
     */
    public static String[] parse(String line) {
        if (line == null)
            return new String[0];
        return line.split(DELIMITER);
    }

    /**
     * 문자열을 정수로 안전하게 변환한다.
     * 변환 실패 시 0을 반환한다.
     *
     * @param s 변환할 문자열
     * @return 변환된 정수 값 또는 0
     */
    public static int safeInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return 0;
        }
    }
}
