package com.aseubel.yusi.service.ai.asr.adapter;

import com.alibaba.dashscope.audio.asr.recognition.Recognition;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionParam;
import com.alibaba.dashscope.audio.asr.recognition.RecognitionResult;
import com.alibaba.dashscope.common.ResultCallback;
import com.alibaba.dashscope.protocol.ConnectionOptions;
import com.aseubel.yusi.config.ai.properties.ModelRoutingProperties;
import com.aseubel.yusi.service.ai.asr.StreamingSpeechToTextClient;
import com.aseubel.yusi.service.ai.asr.StreamingSpeechToTextListener;
import com.aseubel.yusi.service.ai.asr.StreamingSpeechToTextSession;
import com.aseubel.yusi.service.ai.asr.StreamingTranscriptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** DashScope's full-duplex Recognition API backed by paraformer-realtime-v2. */
@Slf4j
public class DashScopeStreamingSpeechToTextClient implements StreamingSpeechToTextClient {

    private static final String FORMAT = "pcm";
    private static final int SAMPLE_RATE = 16_000;
    private static final String DEFAULT_WEBSOCKET_URL =
            "wss://dashscope.aliyuncs.com/api-ws/v1/inference";

    private final ModelRoutingProperties.ModelDefinition definition;
    private final ConnectionOptions connectionOptions;

    public DashScopeStreamingSpeechToTextClient(ModelRoutingProperties.ModelDefinition definition) {
        this.definition = definition;
        int timeoutSeconds = Math.max(1, definition.getTimeoutSeconds() == null
                ? 120 : definition.getTimeoutSeconds());
        this.connectionOptions = ConnectionOptions.builder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .writeTimeout(Duration.ofSeconds(timeoutSeconds))
                .readTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public String modelId() {
        return definition.getId();
    }

    @Override
    public StreamingSpeechToTextSession start(StreamingSpeechToTextListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("流式语音识别回调不能为空");
        }
        if (!StringUtils.hasText(definition.getApikey())) {
            throw new IllegalStateException("流式语音识别模型未配置 API Key");
        }

        Recognition recognition = new Recognition(connectionOptions, resolveWebSocketUrl());
        RecognitionParam param = RecognitionParam.builder()
                .model(definition.getModel())
                .apiKey(definition.getApikey())
                .format(FORMAT)
                .sampleRate(SAMPLE_RATE)
                .disfluencyRemovalEnabled(true)
                .build();
        DashScopeSession session = new DashScopeSession(recognition, modelId(), listener);
        try {
            recognition.call(param, session.callback());
            return session;
        } catch (RuntimeException exception) {
            session.cancel();
            throw exception;
        }
    }

    private String resolveWebSocketUrl() {
        String baseUrl = definition.getBaseurl();
        if (!StringUtils.hasText(baseUrl)) {
            return DEFAULT_WEBSOCKET_URL;
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/api-ws/v1/inference")) {
            return normalized;
        }
        if (normalized.endsWith("/api/v1")) {
            normalized = normalized.substring(0, normalized.length() - "/api/v1".length());
        }
        if (normalized.endsWith("/api")) {
            normalized = normalized.substring(0, normalized.length() - "/api".length());
        }
        if (normalized.startsWith("https://")) {
            normalized = "wss://" + normalized.substring("https://".length());
        } else if (normalized.startsWith("http://")) {
            normalized = "ws://" + normalized.substring("http://".length());
        }
        return normalized + "/api-ws/v1/inference";
    }

    private static final class DashScopeSession implements StreamingSpeechToTextSession {

        private final Recognition recognition;
        private final String modelId;
        private final StreamingSpeechToTextListener listener;
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final AtomicBoolean terminated = new AtomicBoolean();

        private DashScopeSession(Recognition recognition, String modelId,
                                 StreamingSpeechToTextListener listener) {
            this.recognition = recognition;
            this.modelId = modelId;
            this.listener = listener;
        }

        @Override
        public String modelId() {
            return modelId;
        }

        private ResultCallback<RecognitionResult> callback() {
            return new ResultCallback<>() {
                @Override
                public void onEvent(RecognitionResult result) {
                    if (terminated.get() || result == null || result.getSentence() == null) {
                        return;
                    }
                    if (result.getSentence().isHeartbeat()
                            || result.isCompleteResult()
                            || !StringUtils.hasText(result.getSentence().getText())) {
                        return;
                    }
                    listener.onEvent(new StreamingTranscriptionEvent(
                            result.getSentence().getText(),
                            result.isSentenceEnd(),
                            result.getSentence().getSentenceId()));
                }

                @Override
                public void onComplete() {
                    if (terminated.compareAndSet(false, true)) {
                        listener.onComplete();
                    }
                    closeUpstream();
                }

                @Override
                public void onError(Exception exception) {
                    if (terminated.compareAndSet(false, true)) {
                        listener.onError(exception);
                    }
                    closeUpstream();
                }
            };
        }

        @Override
        public void sendAudioFrame(ByteBuffer audioFrame) {
            if (terminated.get() || finishing.get()) {
                return;
            }
            recognition.sendAudioFrame(audioFrame);
        }

        @Override
        public void finish() {
            if (!finishing.compareAndSet(false, true) || terminated.get()) {
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    recognition.stop();
                } catch (RuntimeException exception) {
                    if (terminated.compareAndSet(false, true)) {
                        listener.onError(exception);
                    }
                    closeUpstream();
                }
            });
        }

        @Override
        public void cancel() {
            if (!terminated.compareAndSet(false, true)) {
                return;
            }
            closeUpstream();
        }

        private void closeUpstream() {
            try {
                recognition.getDuplexApi().cancel();
            } catch (RuntimeException exception) {
                log.debug("关闭 DashScope ASR 会话失败: modelId={}", modelId, exception);
            }
            try {
                recognition.getDuplexApi().close(1000, "bye");
            } catch (RuntimeException exception) {
                log.debug("释放 DashScope ASR WebSocket 失败: modelId={}", modelId, exception);
            }
        }
    }
}
