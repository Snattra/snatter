package app.snatter.server.info;

/**
 * Public, unauthenticated description of this Snatter server.
 * Clients call this first to learn what they are talking to.
 */
public record ServerInfo(String name, String version, int apiVersion) {
}
