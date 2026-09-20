package app.snatter.server.settings;

/** Who may create an account on this server. */
public enum RegistrationMode {
    OPEN("open"),
    INVITE_ONLY("invite_only");

    private final String dbValue;

    RegistrationMode(String dbValue) {
        this.dbValue = dbValue;
    }

    public String dbValue() {
        return dbValue;
    }

    public static RegistrationMode fromDbValue(String value) {
        for (RegistrationMode mode : values()) {
            if (mode.dbValue.equals(value)) {
                return mode;
            }
        }
        throw new IllegalArgumentException("Unknown registration mode: " + value);
    }
}
