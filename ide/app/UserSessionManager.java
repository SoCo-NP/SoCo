package ide.app;

import ide.domain.Role;

import java.awt.Color;
import java.util.*;

/**
 * 사용자 세션 정보를 관리하는 클래스.
 * 
 * 접속한 사용자들의 역할, 상태, 색상 등을 관리한다.
 */
public class UserSessionManager {
    private final Map<String, Role> userRoles = new HashMap<>();
    private final Map<String, Color> userColors = new HashMap<>();
    private final Random colorRandom = new Random();

    /**
     * 사용자를 추가하거나 업데이트한다.
     *
     * @param nickname 사용자 닉네임
     * @param role     사용자 역할
     */
    public void addUser(String nickname, Role role) {
        userRoles.put(nickname, role);

        // 색상이 없으면 생성
        if (!userColors.containsKey(nickname)) {
            float hue = colorRandom.nextFloat();
            userColors.put(nickname, Color.getHSBColor(hue, 0.6f, 0.9f));
        }
    }

    /**
     * 사용자를 제거한다.
     *
     * @param nickname 사용자 닉네임
     */
    public void removeUser(String nickname) {
        userRoles.remove(nickname);
        userColors.remove(nickname);
    }

    /**
     * 모든 접속 중인 사용자 목록을 반환한다.
     *
     * @return 사용자 닉네임 집합
     */
    public Set<String> getConnectedUsers() {
        return new HashSet<>(userRoles.keySet());
    }

    /**
     * 학생 역할을 가진 사용자 목록만 반환한다.
     *
     * @return 학생 닉네임 집합
     */
    public Set<String> getConnectedStudents() {
        Set<String> students = new HashSet<>();
        for (Map.Entry<String, Role> entry : userRoles.entrySet()) {
            if (entry.getValue() == Role.STUDENT) {
                students.add(entry.getKey());
            }
        }
        return students;
    }

    /**
     * 사용자의 역할을 반환한다.
     *
     * @param nickname 사용자 닉네임
     * @return 사용자 역할 (없으면 null)
     */
    public Role getUserRole(String nickname) {
        return userRoles.get(nickname);
    }

    /**
     * 사용자의 색상을 반환한다.
     * 색상이 없으면 새로 생성한다.
     *
     * @param nickname 사용자 닉네임
     * @return 사용자 색상
     */
    public Color getColorForNick(String nickname) {
        return userColors.computeIfAbsent(nickname, k -> {
            float hue = colorRandom.nextFloat();
            return Color.getHSBColor(hue, 0.6f, 0.9f);
        });
    }

    /**
     * 모든 사용자 정보를 초기화한다.
     * 연결 해제 시 호출된다.
     */
    public void clear() {
        userRoles.clear();
        userColors.clear();
    }
}
