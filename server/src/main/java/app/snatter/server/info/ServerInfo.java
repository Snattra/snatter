package app.snatter.server.info;

/**
 * Public, unauthenticated description of this Snatter server.
 * Clients call this first to learn what they are talking to.
 *
 * @param name         software name, always "Snatter"
 * @param version      software version
 * @param apiVersion   API compatibility version
 * @param community    the community hosted by this server
 */
public record ServerInfo(String name, String version, int apiVersion, Community community) {

    public record Community(String name, String description) {
    }
}
