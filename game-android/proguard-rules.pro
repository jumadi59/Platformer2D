# Offline asset keys are generated into the consuming application and loaded by name.
-keep class com.jumbox.game.generated.AssetPackKey {
    public static byte[] key();
}

# ProjectSceneLoader discovers this generated registration entry point with Class.forName.
-keep class com.jumbox.generated.ProjectBehaviorInitializer {
    public static void register(com.jumbox.game.core.scene.BehaviorRegistry);
}
