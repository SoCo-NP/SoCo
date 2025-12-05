package ide.domain;

import java.awt.Color;

/**
 * 시스템 사용자 정보를 담는 도메인 클래스.
 *
 * 사용자의 닉네임, 역할(Role), 그리고 UI에 표시될 고유 색상 정보를 포함한다.
 * 이 클래스는 불변(Immutable) 객체로 설계되었다.
 *
 */
public class User {
    private final String nickname;
    private final Role role;
    private final Color color;

    /**
     * User 생성자.
     *
     * @param nickname 사용자 닉네임
     * @param role     사용자 역할 (교수, 학생 등)
     * @param color    사용자 고유 색상 (커서 및 강조 표시에 사용)
     */
    public User(String nickname, Role role, Color color) {
        this.nickname = nickname;
        this.role = role;
        this.color = color;
    }

    /**
     * 사용자 닉네임을 반환한다.
     *
     * @return 닉네임 문자열
     */
    public String getNickname() {
        return nickname;
    }

    /**
     * 사용자 역할을 반환한다.
     *
     * @return Role 열거형 상수
     */
    public Role getRole() {
        return role;
    }

    /**
     * 사용자 고유 색상을 반환한다.
     *
     * @return Color 객체
     */
    public Color getColor() {
        return color;
    }

    /**
     * 사용자 정보를 문자열로 반환한다.
     * 형식: "닉네임 (역할)"
     *
     * @return 사용자 정보 문자열
     */
    @Override
    public String toString() {
        return nickname + " (" + role + ")";
    }
}
