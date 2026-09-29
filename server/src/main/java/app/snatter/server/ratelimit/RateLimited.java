package app.snatter.server.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies the named rate limit policy from the server settings to a resource
 * method, counted per client IP or per account.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {

    /** Who a request counts against. */
    enum Per {
        /** The client's IP address, for endpoints anyone may call. */
        IP,
        /** The signed-in account, wherever it connects from; for authenticated endpoints only. */
        ACCOUNT
    }

    /** Policy name: {@code login}, {@code register}, {@code challenge}, {@code invite} or {@code message}. */
    String value();

    Per per() default Per.IP;
}
