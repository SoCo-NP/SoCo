package ide.domain;

public enum Role {
    PROFESSOR,
    STUDENT,
    GUEST;

    public static Role fromString(String role) {
        if (role == null)
            return GUEST;
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            return GUEST;
        }
    }

    @Override
    public String toString() {
        // Return capitalized name (e.g., "Professor")
        String s = name();
        return s.charAt(0) + s.substring(1).toLowerCase();
    }
}
