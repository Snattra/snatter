package app.snatter.server.persistence;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import org.jdbi.v3.core.Jdbi;

/**
 * Exposes a single {@link Jdbi} instance backed by the Agroal connection pool.
 *
 * <p>Transaction rule: Agroal enlists every connection in the active JTA
 * transaction, so demarcate transactions with {@code @Transactional} on the
 * service method and use {@code jdbi.withHandle} / {@code jdbi.useHandle}
 * inside. Do <em>not</em> use {@code jdbi.inTransaction} or
 * {@code handle.begin()}; those try to drive the JDBC transaction directly and
 * conflict with the JTA-managed connection.
 */
public class JdbiProducer {

    @Produces
    @Singleton
    Jdbi jdbi(AgroalDataSource dataSource) {
        Jdbi jdbi = Jdbi.create(dataSource);
        jdbi.registerArgument(new ValueArgumentFactory());
        return jdbi;
    }
}
