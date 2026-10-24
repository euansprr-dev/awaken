package dev.awaken.turbo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Applies a Turbo mixin only on a verified build with its switch on, so with Turbo off (or after a game update)
 * Minecraft's classes load exactly as shipped. Each mixin still passes through at run time if its own probe fails.
 * Other Turbo mixin configs (dev.awaken.turbo.pixels) can point their "plugin" here and add their mixins to FEATURES.
 */
public final class TurboMixinPlugin implements IMixinConfigPlugin {
    /** Mixin class simple name → the switch that owns it. */
    private static final Map<String, TurboSwitches.Feature> FEATURES = Map.of(
            "VulkanRenderPipelineMixin", TurboSwitches.Feature.PIPELINE_CACHE,
            "GlslCompilerMixin", TurboSwitches.Feature.SHADER_OPT);

    @Override
    public void onLoad(String mixinPackage) {
        String problem = TurboVersions.problem();
        TurboVersions.LOG.info("[turbo] mixins: {} ({}; {})", problem == null ? "verified build" : problem,
                TurboVersions.describe(), TurboSwitches.describe());
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (TurboVersions.problem() != null) return false;
        TurboSwitches.Feature f = FEATURES.get(mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1));
        return f != null && TurboSwitches.on(f);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
}
