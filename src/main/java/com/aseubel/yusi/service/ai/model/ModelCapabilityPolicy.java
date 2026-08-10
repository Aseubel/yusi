package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;

import java.util.Set;

/**
 * Resolves the model capability required by a business scene.
 *
 * A visual model is deliberately explicit. An empty capability list keeps the
 * text-model default, but it never implies visual understanding.
 */
public final class ModelCapabilityPolicy {

    private static final String IMAGE_UNDERSTANDING_SCENE = "image-understanding";
    private static final Set<ModelCapability> DEFAULT_TEXT_CAPABILITIES =
            Set.of(ModelCapability.CHAT, ModelCapability.STREAMING_CHAT);

    private ModelCapabilityPolicy() {
    }

    public static boolean supportsScene(ModelRoutingProperties.ModelDefinition model, String scene) {
        if (model == null) {
            return false;
        }
        Set<ModelCapability> capabilities = model.getCapabilities() == null
                || model.getCapabilities().isEmpty()
                ? DEFAULT_TEXT_CAPABILITIES
                : Set.copyOf(model.getCapabilities());
        return supportsScene(capabilities, scene);
    }

    public static boolean supportsScene(ModelInstance instance, String scene) {
        return instance != null && supportsScene(instance.getCapabilities(), scene);
    }

    public static boolean supportsScene(Set<ModelCapability> capabilities, String scene) {
        if (requiresVision(scene)) {
            return capabilities != null && capabilities.contains(ModelCapability.VLM);
        }
        return capabilities == null || capabilities.isEmpty()
                || capabilities.contains(ModelCapability.CHAT)
                || capabilities.contains(ModelCapability.STREAMING_CHAT);
    }

    public static String requiredCapabilityLabel(String scene) {
        return requiresVision(scene) ? "VLM" : "Chat";
    }

    public static boolean requiresVision(String scene) {
        return IMAGE_UNDERSTANDING_SCENE.equalsIgnoreCase(normalize(scene));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
