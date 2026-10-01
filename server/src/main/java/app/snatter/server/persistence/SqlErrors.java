package app.snatter.server.persistence;

import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

/** Recognises database failures that callers turn into API errors. */
public final class SqlErrors {

    private SqlErrors() {
    }

    /** Whether a UNIQUE or PRIMARY KEY constraint caused the failure, anywhere in its causes. */
    public static boolean isUniqueViolation(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof SQLiteException e) {
                return e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE
                    || e.getResultCode() == SQLiteErrorCode.SQLITE_CONSTRAINT_PRIMARYKEY;
            }
        }
        return false;
    }
}
