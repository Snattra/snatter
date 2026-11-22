package app.snatter.server.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AfterCommitTest {

    @Inject
    AfterCommit afterCommit;

    @Test
    void runsOnlyForTheMatchingOutcome() {
        List<String> actions = new ArrayList<>();
        afterCommit.run(() -> actions.add("immediate"));
        afterCommit.onRollback(() -> actions.add("no transaction"));
        assertEquals(List.of("immediate"), actions);

        QuarkusTransaction.requiringNew().run(() -> {
            afterCommit.run(() -> actions.add("committed"));
            afterCommit.onRollback(() -> actions.add("not rolled back"));
            assertEquals(List.of("immediate"), actions);
        });
        assertEquals(List.of("immediate", "committed"), actions);

        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            afterCommit.run(() -> actions.add("not committed"));
            afterCommit.onRollback(() -> actions.add("rolled back"));
            throw new IllegalStateException("rollback");
        }));
        assertEquals(List.of("immediate", "committed", "rolled back"), actions);
    }
}
