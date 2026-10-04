package app.snatter.server.testing;

import app.snatter.server.ratelimit.RateLimitFilter;
import app.snatter.server.settings.ServerSettingsService;
import io.agroal.api.AgroalDataSource;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ArcContainer;
import io.quarkus.arc.InjectableContext;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Singleton;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Puts the server back the way a fresh install starts: every table holds
 * what the migrations left in it and nothing else, and the beans that keep
 * database state in memory start over. {@link ResetDatabaseBeforeEach} does
 * this before every test.
 *
 * <p>What the migrations left is read at startup, before any test runs.
 */
@Singleton
public class DatabaseReset {

    /** Beans that remember what they read from the database; destroyed, they are created afresh on next use. */
    private static final List<Class<?>> CACHING_BEANS = List.of(ServerSettingsService.class, RateLimitFilter.class);

    private record Table(String name, List<String> columns, List<Object[]> rows) {

        String insert() {
            return "INSERT INTO " + quote(name) + " (" + columns.stream().map(DatabaseReset::quote).collect(Collectors.joining(", "))
                + ") VALUES (" + String.join(", ", Collections.nCopies(columns.size(), "?")) + ")";
        }
    }

    private final AgroalDataSource database;
    private List<Table> fresh;

    DatabaseReset(AgroalDataSource database) {
        this.database = database;
    }

    void readFreshDatabase(@Observes StartupEvent event) throws SQLException {
        List<Table> tables = new ArrayList<>();
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            List<String> names = new ArrayList<>();
            try (ResultSet rs = statement.executeQuery("SELECT name FROM sqlite_schema WHERE type = 'table'"
                + " AND name NOT LIKE 'sqlite\\_%' ESCAPE '\\' AND name <> 'flyway_schema_history'")) {
                while (rs.next()) {
                    names.add(rs.getString(1));
                }
            }
            for (String name : names) {
                try (ResultSet rs = statement.executeQuery("SELECT * FROM " + quote(name))) {
                    ResultSetMetaData meta = rs.getMetaData();
                    List<String> columns = new ArrayList<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columns.add(meta.getColumnName(i));
                    }
                    List<Object[]> rows = new ArrayList<>();
                    while (rs.next()) {
                        Object[] row = new Object[columns.size()];
                        for (int i = 0; i < row.length; i++) {
                            row[i] = rs.getObject(i + 1);
                        }
                        rows.add(row);
                    }
                    tables.add(new Table(name, columns, rows));
                }
            }
        }
        fresh = tables;
    }

    public void reset() {
        try {
            restoreTables();
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot reset the database", e);
        }
        ArcContainer arc = Arc.requireContainer();
        InjectableContext application = arc.getActiveContext(ApplicationScoped.class);
        for (Class<?> type : CACHING_BEANS) {
            application.destroy(arc.instance(type).getBean());
        }
    }

    /**
     * Empties and refills every table in one transaction. Foreign keys are off
     * meanwhile, so the order does not matter and no cascade runs; SQLite only
     * lets them be switched outside a transaction.
     */
    private void restoreTables() throws SQLException {
        try (Connection connection = database.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            try {
                connection.setAutoCommit(false);
                for (Table table : fresh) {
                    statement.execute("DELETE FROM " + quote(table.name()));
                    try (PreparedStatement insert = connection.prepareStatement(table.insert())) {
                        for (Object[] row : table.rows()) {
                            for (int i = 0; i < row.length; i++) {
                                insert.setObject(i + 1, row[i]);
                            }
                            insert.executeUpdate();
                        }
                    }
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
                statement.execute("PRAGMA foreign_keys = ON");
            }
        }
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
