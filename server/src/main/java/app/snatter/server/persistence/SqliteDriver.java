package app.snatter.server.persistence;

import io.quarkus.runtime.annotations.RegisterForReflection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/**
 * The SQLite JDBC driver with the connection settings Snatter depends on,
 * applied whatever the JDBC URL says, and the database's directory created
 * on first use so a fresh install needs nothing but a path.
 *
 * <ul>
 *   <li>{@code foreign_keys}: SQLite ignores foreign keys unless each
 *       connection turns them on, and the schema relies on their cascades.</li>
 *   <li>{@code journal_mode=WAL}: readers never wait for the writer, nor it
 *       for them.</li>
 *   <li>{@code synchronous=NORMAL}: with WAL this survives the process
 *       crashing; losing power may lose the last moments of commits, but
 *       never corrupts the database.</li>
 *   <li>{@code transaction_mode=IMMEDIATE}: a transaction takes the write
 *       lock when it begins. SQLite has one writer at a time, and a
 *       transaction that read first and then tried to write would otherwise
 *       fail when another had written in between, rather than wait.</li>
 *   <li>{@code busy_timeout}: how long a transaction waits for the write lock
 *       before failing.</li>
 * </ul>
 */
@RegisterForReflection
public class SqliteDriver extends org.sqlite.JDBC {

    static final String BUSY_TIMEOUT_MILLIS = "10000";

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null;
        }
        createDirectoryFor(url);
        Properties settings = new Properties();
        if (info != null) {
            settings.putAll(info);
        }
        settings.setProperty("foreign_keys", "true");
        settings.setProperty("journal_mode", "WAL");
        settings.setProperty("synchronous", "NORMAL");
        settings.setProperty("transaction_mode", "IMMEDIATE");
        settings.setProperty("busy_timeout", BUSY_TIMEOUT_MILLIS);
        return super.connect(url, settings);
    }

    /** Creates the directory of a file database; SQLite creates the file but not its directory. */
    private static void createDirectoryFor(String url) throws SQLException {
        String address = url.substring(PREFIX.length());
        if (address.startsWith("file:")) {
            address = address.substring("file:".length());
        }
        int query = address.indexOf('?');
        if (query >= 0) {
            address = address.substring(0, query);
        }
        if (address.isEmpty() || address.startsWith(":")) {
            return;   // in memory, or a resource
        }
        Path parent = Path.of(address).toAbsolutePath().getParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException e) {
            throw new SQLException("Cannot create the database directory " + parent, e);
        }
    }
}
