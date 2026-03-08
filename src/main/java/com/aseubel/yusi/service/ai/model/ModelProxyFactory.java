package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelProxyFactory {

    private final ModelRouterService modelRouterService;
    private final ModelStateCenter modelStateCenter;

    public ChatModel createChatProxy(String defaultLanguage, String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(defaultLanguage, defaultScene, true);
        return (ChatModel) Proxy.newProxyInstance(ChatModel.class.getClassLoader(), new Class[] { ChatModel.class }, handler);
    }

    public StreamingChatModel createStreamingProxy(String defaultLanguage, String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(defaultLanguage, defaultScene, false);
        return (StreamingChatModel) Proxy.newProxyInstance(StreamingChatModel.class.getClassLoader(),
                new Class[] { StreamingChatModel.class }, handler);
    }

    private class RoutingInvocationHandler implements InvocationHandler {
        private final String defaultLanguage;
        private final String defaultScene;
        private final boolean chatMode;

        private RoutingInvocationHandler(String defaultLanguage, String defaultScene, boolean chatMode) {
            this.defaultLanguage = defaultLanguage;
            this.defaultScene = defaultScene;
            this.chatMode = chatMode;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return method.invoke(this, args);
            }
            Set<String> excluded = new HashSet<>();
            Throwable lastError = null;
            for (int i = 0; i < 4; i++) {
                ModelRouteContext context = resolveContext();
                ModelInstance selected = modelRouterService.select(context, excluded);
                if (!modelStateCenter.allowRequest(selected.getId())) {
                    excluded.add(selected.getId());
                    continue;
                }
                long start = System.currentTimeMillis();
                try {
                    Object delegate = chatMode ? selected.getChatModel() : selected.getStreamingChatModel();
                    Object result = invokeWithSceneParameters(delegate, method, args, context);
                    modelStateCenter.recordSuccess(selected.getId(), selected.getModelName(),
                            System.currentTimeMillis() - start);
                    return result;
                } catch (Throwable throwable) {
                    Throwable root = throwable.getCause() == null ? throwable : throwable.getCause();
                    modelStateCenter.recordFailure(selected.getId(), selected.getModelName(),
                            System.currentTimeMillis() - start, root);
                    excluded.add(selected.getId());
                    lastError = root;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new IllegalStateException("No available model instance for language: " + defaultLanguage + ", scene: " + defaultScene);
        }

        private Object invokeWithSceneParameters(Object delegate, Method method, Object[] args, ModelRouteContext context) throws Throwable {
            ModelRoutingProperties.SceneDefinition sceneDef = modelRouterService.resolveSceneDefinition(
                    context.getLanguage(), context.getScene());
            if (sceneDef == null || !hasSceneParameters(sceneDef)) {
                return method.invoke(delegate, args);
            }
            if (args != null && args.length > 0 && args[0] instanceof ChatRequest chatRequest) {
                ChatRequestParameters overrideParams = buildOverrideParameters(sceneDef);
                ChatRequest newRequest = ChatRequest.builder()
                        .messages(chatRequest.messages())
                        .parameters(mergeParameters(chatRequest.parameters(), overrideParams))
                        .build();
                return method.invoke(delegate, new Object[] { newRequest });
            }
            return method.invoke(delegate, args);
        }

        private boolean hasSceneParameters(ModelRoutingProperties.SceneDefinition sceneDef) {
            return sceneDef.getMaxTokens() != null
                    || sceneDef.getTemperature() != null
                    || sceneDef.getTopP() != null
                    || sceneDef.getMaxCompletionTokens() != null
                    || (sceneDef.getCustomParameters() != null && !sceneDef.getCustomParameters().isEmpty());
        }

        private ChatRequestParameters buildOverrideParameters(ModelRoutingProperties.SceneDefinition sceneDef) {
            Builder builder = OpenAiChatRequestParameters.builder();
            if (sceneDef.getMaxTokens() != null) {
                builder.maxOutputTokens(sceneDef.getMaxTokens());
            }
            if (sceneDef.getTemperature() != null) {
                builder.temperature(sceneDef.getTemperature());
            }
            if (sceneDef.getTopP() != null) {
                builder.topP(sceneDef.getTopP());
            }
            if (sceneDef.getMaxCompletionTokens() != null) {
                builder.maxCompletionTokens(sceneDef.getMaxCompletionTokens());
            }
            if (sceneDef.getCustomParameters() != null && !sceneDef.getCustomParameters().isEmpty()) {
                builder.customParameters(sceneDef.getCustomParameters());
            }
            return builder.build();
        }

        private ChatRequestParameters mergeParameters(ChatRequestParameters base, ChatRequestParameters override) {
            if (base == null) {
                return override;
            }
            return base.overrideWith(override);
        }

        private ModelRouteContext resolveContext() {
            ModelRouteContext context = ModelRouteContextHolder.get();
            String language = context == null ? null : context.getLanguage();
            String scene = context == null ? null : context.getScene();
            String group = context == null ? null : context.getGroup();
            String resolvedLanguage = Objects.requireNonNullElse(language, defaultLanguage);
            String resolvedScene = Objects.requireNonNullElse(scene, defaultScene);
            String resolvedGroup = group == null || group.isBlank()
                    ? modelRouterService.resolveGroup(resolvedLanguage, resolvedScene)
                    : group;
            return ModelRouteContext.builder()
                    .language(resolvedLanguage)
                    .scene(resolvedScene)
                    .group(resolvedGroup)
                    .build();
        }
    }
}
