package app.snatter.server.role;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.snatter.server.account.Account;
import app.snatter.server.account.AccountId;
import app.snatter.server.account.AccountRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

@QuarkusTest
class RoleRepositoryTest {

    @Inject
    RoleRepository roles;

    @Inject
    AccountRepository accounts;

    @Test
    @Transactional
    void movedRolesStayAssignedAndOrdered() {
        Account member = accounts.createLocal(AccountId.newId(), "rr_" + UUID.randomUUID().toString().substring(0, 8), "RR", "x");
        Role manager = roles.insertAtBottom(RoleId.newId(), "Manager", null, Set.of(Permission.MANAGE_ROLES, Permission.TIMEOUT_MEMBERS));
        Role admin = roles.insertAtBottom(RoleId.newId(), "Admin", null, Set.of(Permission.MANAGE_SERVER));
        assertEquals(0, roles.find(admin.id()).orElseThrow().position());
        assertEquals(1, roles.find(manager.id()).orElseThrow().position());

        roles.moveTo(admin.id(), 5);
        roles.moveTo(manager.id(), 3);
        assertEquals(6, roles.find(admin.id()).orElseThrow().position(), "admin shifted up past the moved manager");
        assertEquals(3, roles.find(manager.id()).orElseThrow().position());

        roles.assign(member.id(), manager.id());
        List<Role> assigned = roles.findByAccount(member.id());
        assertEquals(List.of(manager.id()), assigned.stream().map(Role::id).toList());
        assertTrue(assigned.get(0).permissions().contains(Permission.MANAGE_ROLES));
        assertEquals(List.of(manager.id()), accounts.findById(member.id()).orElseThrow().roleIds());
    }
}
