package ide.domain;

import java.awt.Color;

public class User {
    private final String nickname;
    private final Role role;
    private final Color color; // Domain model can have color concept (or separate visual model, but keeping
                               // simple)

    public User(String nickname, Role role, Color color) {
        this.nickname = nickname;
        this.role = role;
        this.color = color;
    }

    public String getNickname() {
        return nickname;
    }

    public Role getRole() {
        return role;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return nickname + " (" + role + ")";
    }
}
