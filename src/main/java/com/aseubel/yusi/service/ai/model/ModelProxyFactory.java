package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.mask.MaskResult;
import com.aseubel.yusi.service.ai.mask.SensitiveDataMaskService;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters;
import dev.langchain4j.model.openai.OpenAiChatRequestParameters.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelProxyFactory {

    private final ModelRouterService modelRouterService;
    private final ModelStateCenter modelStateCenter;
    private final SensitiveDataMaskService maskService;

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
                    log.warn("AI model invocation failed, attempt {}/{}, model: {}, error: {}",
                            i + 1, 4, selected.getModelName(), root.getMessage());
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
                // 无 scene 参数时也需要处理脱敏
                return invokeWithMasking(delegate, method, args);
            }
            // 处理 ChatRequest 参数，同时保留其他参数（如 StreamingResponseHandler）
            if (args != null && args.length > 0 && args[0] instanceof ChatRequest chatRequest) {
                ChatRequestParameters overrideParams = buildOverrideParameters(sceneDef);
                ChatRequest newRequest = ChatRequest.builder()
                        .messages(chatRequest.messages())
                        .parameters(mergeParameters(chatRequest.parameters(), overrideParams))
                        .build();
                // 构建新的参数数组，保留原有其他参数
                Object[] newArgs = new Object[args.length];
                newArgs[0] = newRequest;
                for (int i = 1; i < args.length; i++) {
                    newArgs[i] = args[i];
                }
                return invokeWithMasking(delegate, method, newArgs);
            }
            return invokeWithMasking(delegate, method, args);
        }

        // ── 脱敏核心逻辑 ──────────────────────────────────────

        /**
         * 在发送给外部 LLM 前对 ChatRequest 的所有消息统一脱敏，
         * 并在收到响应后统一还原。
         * <p>
         * 这确保了：
         * 1. 语义一致性 — SystemPrompt、聊天历史、用户消息、Tool 结果全部统一脱敏
         * 2. 无线程问题 — 拦截发生在最终 HTTP 调用前
         */
        private Object invokeWithMasking(Object delegate, Method method, Object[] args) throws Throwable {
            if (args == null || args.length == 0 || !(args[0] instanceof ChatRequest chatRequest)) {
                return method.invoke(delegate, args);
            }

            // 1. 拼接所有消息文本，统一脱敏
            List<ChatMessage> originalMessages = chatRequest.messages();
            String allText = extractAllText(originalMessages);
            MaskResult maskResult = maskService.mask(allText);

            if (!maskResult.isHasMasked()) {
                // 无敏感信息，直接调用
                return method.invoke(delegate, args);
            }

            Map<String, String> mapping = maskResult.getMappingTable();
            log.debug("脱敏拦截: 映射表大小={}", mapping.size());

            // 2. 构建脱敏后的 ChatRequest
            List<ChatMessage> maskedMessages = maskMessages(originalMessages, mapping);
            ChatRequest maskedRequest = ChatRequest.builder()
                    .messages(maskedMessages)
                    .parameters(chatRequest.parameters())
                    .build();
            Object[] maskedArgs = new Object[args.length];
            maskedArgs[0] = maskedRequest;

            // 3. 处理流式响应（包装 handler 进行 unmask）
            if (!chatMode && args.length > 1 && args[1] instanceof StreamingChatResponseHandler originalHandler) {
                maskedArgs[1] = wrapStreamingHandler(originalHandler, mapping);
                for (int i = 2; i < args.length; i++) {
                    maskedArgs[i] = args[i];
                }
                return method.invoke(delegate, maskedArgs);
            }

            // 4. 同步响应：调用后 unmask
            for (int i = 1; i < args.length; i++) {
                maskedArgs[i] = args[i];
            }
            Object result = method.invoke(delegate, maskedArgs);
            if (result instanceof ChatResponse chatResponse) {
                return unmaskChatResponse(chatResponse, mapping);
            }
            return result;
        }

        /**
         * 提取所有消息的文本内容，拼接为一个字符串用于统一脱敏
         */
        private String extractAllText(List<ChatMessage> messages) {
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : messages) {
                if (msg instanceof SystemMessage sm) {
                    sb.append(sm.text()).append("\n");
                } else if (msg instanceof UserMessage um) {
                    sb.append(um.singleText()).append("\n");
                } else if (msg instanceof AiMessage am && am.text() != null) {
                    sb.append(am.text()).append("\n");
                } else if (msg instanceof ToolExecutionResultMessage tm) {
                    sb.append(tm.text()).append("\n");
                }
            }
            return sb.toString();
        }

        /**
         * 对每条消息的文本内容应用脱敏替换
         */
        private List<ChatMessage> maskMessages(List<ChatMessage> messages, Map<String, String> mapping) {
            // 反转映射表：原始值 → 占位符
            Map<String, String> reverseMapping = new HashMap<>();
            for (Map.Entry<String, String> entry : mapping.entrySet()) {
                reverseMapping.put(entry.getValue(), entry.getKey());
            }

            return messages.stream().map(msg -> maskSingleMessage(msg, reverseMapping)).collect(Collectors.toList());
        }

        private ChatMessage maskSingleMessage(ChatMessage msg, Map<String, String> reverseMapping) {
            if (msg instanceof SystemMessage sm) {
                return SystemMessage.from(replaceAll(sm.text(), reverseMapping));
            } else if (msg instanceof UserMessage um) {
                // UserMessage 可能包含多种 Content（文本 + 图片），只替换文本部分
                List<Content> maskedContents = um.contents().stream().map(content -> {
                    if (content instanceof TextContent tc) {
                        return (Content) TextContent.from(replaceAll(tc.text(), reverseMapping));
                    }
                    return content;
                }).collect(Collectors.toList());
                return UserMessage.from(um.name(), maskedContents);
            } else if (msg instanceof AiMessage am) {
                String maskedText = am.text() != null ? replaceAll(am.text(), reverseMapping) : null;
                if (am.hasToolExecutionRequests()) {
                    return AiMessage.from(maskedText, am.toolExecutionRequests());
                }
                return maskedText != null ? AiMessage.from(maskedText) : am;
            } else if (msg instanceof ToolExecutionResultMessage tm) {
                return ToolExecutionResultMessage.from(tm.id(), tm.toolName(),
                        replaceAll(tm.text(), reverseMapping));
            }
            return msg;
        }

        /**
         * 批量替换：将文本中的原始值替换为占位符
         */
        private String replaceAll(String text, Map<String, String> reverseMapping) {
            if (text == null || text.isEmpty()) return text;
            String result = text;
            for (Map.Entry<String, String> entry : reverseMapping.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue());
            }
            return result;
        }

        /**
         * 包装流式响应处理器，对每个 token 和最终响应做 unmask
         */
        private StreamingChatResponseHandler wrapStreamingHandler(
                StreamingChatResponseHandler original, Map<String, String> mapping) {
            return new StreamingChatResponseHandler() {
                @Override
                public void onPartialResponse(String partialResponse) {
                    original.onPartialResponse(maskService.unmask(mapping, partialResponse));
                }

                @Override
                public void onCompleteResponse(ChatResponse completeResponse) {
                    original.onCompleteResponse(unmaskChatResponse(completeResponse, mapping));
                }

                @Override
                public void onError(Throwable error) {
                    original.onError(error);
                }
            };
        }

        /**
         * 对 ChatResponse 中的 AiMessage 文本做 unmask
         */
        private ChatResponse unmaskChatResponse(ChatResponse response, Map<String, String> mapping) {
            if (response == null || response.aiMessage() == null || response.aiMessage().text() == null) {
                return response;
            }
            AiMessage original = response.aiMessage();
            String unmasked = maskService.unmask(mapping, original.text());
            AiMessage newAiMessage;
            if (original.hasToolExecutionRequests()) {
                newAiMessage = AiMessage.from(unmasked, original.toolExecutionRequests());
            } else {
                newAiMessage = AiMessage.from(unmasked);
            }
            return ChatResponse.builder()
                    .aiMessage(newAiMessage)
                    .metadata(response.metadata())
                    .build();
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
