package app.snatter.server.common;

/**
 * A small value record wrapping a single primitive-like value, such as a
 * typed id or a code. Implementations serialise to JSON as the bare value and
 * bind to SQL as it through {@code persistence.ValueArgumentFactory}.
 */
public interface Value<T> {

    T value();
}
