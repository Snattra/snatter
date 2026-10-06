package app.snatter.server.media;

/** The peer's answer is not one the server can connect with. The message says why. */
public final class InvalidAnswerException extends Exception {

    public InvalidAnswerException(String message) {
        super(message);
    }
}
