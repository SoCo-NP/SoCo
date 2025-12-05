package ide.domain;

/**
 * 사용자 역할을 정의하는 열거형(Enum).
 *
 * 시스템 내에서 사용자는 교수(PROFESSOR), 학생(STUDENT), 게스트(GUEST) 중 하나의 역할을 가진다.
 * 역할에 따라 기능 접근 권한이 달라질 수 있다.
 *
 */
public enum Role {
    /** 교수 권한 (모든 기능 접근 가능) */
    PROFESSOR,
    /** 학생 권한 (일부 기능 제한 가능) */
    STUDENT,
    /** 게스트 권한 (읽기 전용 등 제한적 접근) */
    GUEST;

    /**
     * 문자열을 Role 열거형으로 변환한다.
     * 유효하지 않은 문자열이 입력되면 기본값으로 GUEST를 반환한다.
     *
     * @param role 변환할 역할 문자열 (대소문자 무관)
     * @return 매칭되는 Role 상수, 또는 GUEST
     */
    public static Role fromString(String role) {
        if (role == null)
            return GUEST;
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GUEST;
        }
    }

    /**
     * 역할을 사용자 친화적인 문자열로 반환한다.
     * 예: "Professor", "Student"
     *
     * @return 첫 글자만 대문자인 역할 이름
     */
    @Override
    public String toString() {
        String s = name();
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
