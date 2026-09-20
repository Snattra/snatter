package app.snatter.server.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies the named rate limit policy from the server settings to a resource
 * method, keyed by client IP.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface RateLimited {

    /** Policy name: {@code login}, {@code register} or {@code challenge}. */
    String value();
}
