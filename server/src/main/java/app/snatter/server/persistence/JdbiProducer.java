package app.snatter.server.persistence;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.transaction.Status;
import jakarta.transaction.SystemException;
import jakarta.transaction.TransactionManager;
import java.sql.SQLException;
import org.jdbi.v3.core.Jdbi;

/**
 * Exposes a single {@link Jdbi} instance over the two connection pools.
 *
 * <p>Transaction rule: demarcate transactions with {@code @Transactional} on
 * the service or repository method and use {@code jdbi.withHandle} /
 * {@code jdbi.useHandle} inside. Do <em>not</em> use {@code jdbi.inTransaction}
 * or {@code handle.begin()}; those try to drive the JDBC transaction directly
 * and conflict with the JTA-managed connection.
 *
 * <p>Inside a transaction, statements run on the one writing connection,
 * which Agroal enlists in the transaction. SQLite has one writer at a time,
 * so transactions queue for that connection, and each holds it from its
 * first statement to its end. Keep transactions to the work that must be
 * atomic: do slow work such as password hashing before, and read without a
 * transaction where nothing is written.
 *
 * <p>Outside a transaction, statements run on the read-only pool, whose
 * connections read side by side and never wait for the writer. A write there
 * fails, so every write needs a transaction.
 */
public class JdbiProducer {

    @Produces
    @Singleton
    Jdbi jdbi(AgroalDataSource writer, @DataSource("reader") AgroalDataSource reader, TransactionManager transactions) {
        Jdbi jdbi = Jdbi.create(() -> (inTransaction(transactions) ? writer : reader).getConnection());
        jdbi.registerArgument(new ValueArgumentFactory());
        return jdbi;
    }

    private static boolean inTransaction(TransactionManager transactions) throws SQLException {
        try {
            int status = transactions.getStatus();
            return status == Status.STATUS_ACTIVE || status == Status.STATUS_MARKED_ROLLBACK;
        } catch (SystemException e) {
            throw new SQLException("Cannot tell whether a transaction is active", e);
        }
    }
}
