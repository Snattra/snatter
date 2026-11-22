package app.snatter.server.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;

@ApplicationScoped
public class AfterCommit {

    private final TransactionSynchronizationRegistry transactions;

    public AfterCommit(TransactionSynchronizationRegistry transactions) {
        this.transactions = transactions;
    }

    /** Runs once the current transaction commits; immediately if there is none. */
    public void run(Runnable action) {
        afterCompletion(Status.STATUS_COMMITTED, action);
    }

    /** Runs if the current transaction rolls back; never if there is none. */
    public void onRollback(Runnable action) {
        afterCompletion(Status.STATUS_ROLLEDBACK, action);
    }

    private void afterCompletion(int whenStatus, Runnable action) {
        if (transactions.getTransactionStatus() == Status.STATUS_NO_TRANSACTION) {
            if (whenStatus == Status.STATUS_COMMITTED) {
                action.run();
            }
            return;
        }
        transactions.registerInterposedSynchronization(new Synchronization() {
            @Override
            public void beforeCompletion() {
            }

            @Override
            public void afterCompletion(int status) {
                if (status == whenStatus) {
                    action.run();
                }
            }
        });
    }
}
