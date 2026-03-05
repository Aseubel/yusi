package com.aseubel.yusi.service.ai.model;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
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

    public ChatModel createChatProxy(String bindingName, String defaultLanguage, String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(bindingName, defaultLanguage, defaultScene, true);
        return (ChatModel) Proxy.newProxyInstance(ChatModel.class.getClassLoader(), new Class[] { ChatModel.class }, handler);
    }

    public StreamingChatModel createStreamingProxy(String bindingName, String defaultLanguage, String defaultScene) {
        InvocationHandler handler = new RoutingInvocationHandler(bindingName, defaultLanguage, defaultScene, false);
        return (StreamingChatModel) Proxy.newProxyInstance(StreamingChatModel.class.getClassLoader(),
                new Class[] { StreamingChatModel.class }, handler);
    }

    private class RoutingInvocationHandler implements InvocationHandler {
        private final String bindingName;
        private final String defaultLanguage;
        private final String defaultScene;
        private final boolean chatMode;

        private RoutingInvocationHandler(String bindingName, String defaultLanguage, String defaultScene, boolean chatMode) {
            this.bindingName = bindingName;
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
                    Object result = method.invoke(delegate, args);
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
            throw new IllegalStateException("No available model instance for binding: " + bindingName);
        }

        private ModelRouteContext resolveContext() {
            ModelRouteContext context = ModelRouteContextHolder.get();
            String language = context == null ? null : context.getLanguage();
            String scene = context == null ? null : context.getScene();
            String group = context == null ? null : context.getGroup();
            String bindingGroup = modelRouterService.resolveBindingGroup(bindingName,
                    language == null ? defaultLanguage : language, scene == null ? defaultScene : scene);
            return ModelRouteContext.builder()
                    .language(Objects.requireNonNullElse(language, defaultLanguage))
                    .scene(Objects.requireNonNullElse(scene, defaultScene))
                    .group(group == null || group.isBlank() ? bindingGroup : group)
                    .build();
        }
    }
}
