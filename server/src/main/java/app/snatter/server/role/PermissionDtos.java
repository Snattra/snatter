package app.snatter.server.role;

import app.snatter.api.model.PermissionDto;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Maps permissions to and from their names in the API. */
public final class PermissionDtos {

    private PermissionDtos() {
    }

    public static List<PermissionDto> toDto(Set<Permission> permissions) {
        return permissions.stream().sorted().map(p -> PermissionDto.fromValue(p.name())).toList();
    }

    public static Set<Permission> fromDto(List<PermissionDto> permissions) {
        EnumSet<Permission> set = EnumSet.noneOf(Permission.class);
        for (PermissionDto p : permissions) {
            set.add(Permission.valueOf(p.toString()));
        }
        return set;
    }
}
