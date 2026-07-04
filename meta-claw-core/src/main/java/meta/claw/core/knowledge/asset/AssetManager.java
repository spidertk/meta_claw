package meta.claw.core.knowledge.asset;

import meta.claw.core.knowledge.source.AssetRef;
import meta.claw.core.knowledge.source.KnowledgeSource;

import java.io.InputStream;
import java.nio.file.Path;

public  interface AssetManager {
        AssetRef store(KnowledgeSource source, String vesselId);

        InputStream load(AssetRef ref);

        Path resolvePath(AssetRef ref);
}

