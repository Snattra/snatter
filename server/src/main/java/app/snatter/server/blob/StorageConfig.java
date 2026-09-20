package app.snatter.server.blob;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.nio.file.Path;

@ConfigMapping(prefix = "snatter.storage")
public interface StorageConfig {

    /** Directory under which uploaded content is stored. Created on first use. */
    @WithDefault("./data")
    Path root();
}
