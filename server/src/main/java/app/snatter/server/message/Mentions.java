package app.snatter.server.message;

import app.snatter.server.account.AccountId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The member mentions in message content: {@code <@accountId>} tokens. */
final class Mentions {

    /** The most members one message may mention. */
    static final int MAX = 20;

    // Not <@&roleId>: the & keeps a role token from matching.
    private static final Pattern MEMBER = Pattern.compile(
        "<@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>");

    private Mentions() {
    }

    /** The accounts mentioned, each once, in order of first mention. */
    static Set<AccountId> in(String content) {
        Set<AccountId> mentioned = new LinkedHashSet<>();
        Matcher matcher = MEMBER.matcher(content);
        while (matcher.find()) {
            mentioned.add(new AccountId(UUID.fromString(matcher.group(1))));
        }
        return mentioned;
    }
}
