package app.snatter.server.invite;

import app.snatter.server.common.Value;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The public code of an invite, as it appears at the end of an invite link.
 * Implements {@link CharSequence} so that the contract's pattern constraint can
 * validate it when it arrives as a path parameter.
 */
public record InviteCode(String value) implements Value<String>, CharSequence {

    static final int LENGTH = 8;
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9]{" + LENGTH + "}$");

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public InviteCode {
        Objects.requireNonNull(value, "value");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("Not an invite code: " + value);
        }
    }

    public static InviteCode random(SecureRandom random) {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return new InviteCode(sb.toString());
    }

    /** Used by JAX-RS for path parameters. */
    public static InviteCode fromString(String s) {
        return new InviteCode(s);
    }

    @Override
    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public int length() {
        return value.length();
    }

    @Override
    public char charAt(int index) {
        return value.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return value.subSequence(start, end);
    }

    @Override
    public String toString() {
        return value;
    }
}
