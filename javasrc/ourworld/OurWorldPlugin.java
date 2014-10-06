package ourworld;

import java.io.*;
import cljminecraft.ClojurePlugin;
import org.bukkit.generator.ChunkGenerator;

public class OurWorldPlugin extends ClojurePlugin {
    ChunkGenerator getDefaultChunkGenerator (String worldName, String id) {
    	return (ChunkGenerator) clojure.lang.RT.var("ourworld.core", "generator").invoke(this, worldName, id);
    }
}
