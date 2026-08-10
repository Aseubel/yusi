package com.aseubel.yusi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.aseubel.yusi.config.VoiceInputProperties;
import com.aseubel.yusi.config.WebSocketTokenAuthenticator;
import com.aseubel.yusi.service.ai.asr.SpeechModelRegistry;
import com.aseubel.yusi.service.ai.asr.StreamingSpeechToTextListener;
import com.aseubel.yusi.service.ai.asr.StreamingSpeechToTextSession;
import com.aseubel.yusi.service.ai.asr.StreamingTranscriptionEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Native WebSocket transport for diary voice input. Audio is forwarded, never persisted. */
@Slf4j
@Component
public class DiaryVoiceWebSocketHandler extends AbstractWebSocketHandler {

    public static final String ENDPOINT = "/ws-diary-voice";
    private static final String START_TYPE = "start";
    private static final String FINISH_TYPE = "finish";
    private static final String CANCEL_TYPE = "cancel";
    private static final String PING_TYPE = "ping";
    private static final String PCM_FORMAT = "pcm_s16le";
    private static final int SAMPLE_RATE = 16_000;
    private static final int CHANNELS = 1;

    private final ObjectMapper objectMapper;
    private final SpeechModelRegistry speechModelRegistry;
    private final WebSocketTokenAuthenticator tokenAuthenticator;
    private final VoiceInputProperties properties;
    private final ScheduledExecutorService timeoutExecutor;
    private final Map<String, VoiceConnection> connections = new ConcurrentHashMap<>();

    public DiaryVoiceWebSocketHandler(ObjectMapper objectMapper,
                                      SpeechModelRegistry speechModelRegistry,
                                      WebSocketTokenAuthenticator tokenAuthenticator,
                                      VoiceInputProperties properties) {
        this.objectMapper = objectMapper;
        this.speechModelRegistry = speechModelRegistry;
        this.tokenAuthenticator = tokenAuthenticator;
        this.properties = properties;
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "yusi-diary-voice-timeout");
            thread.setDaemon(true);
            return thread;
        };
        this.timeoutExecutor = Executors.newScheduledThreadPool(2, threadFactory);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                session,
                Math.max(1_000, properties.getSendTimeLimitMillis()),
                Math.max(16 * 1024, properties.getSendBufferSizeBytes()));
        VoiceConnection connection = new VoiceConnection(decorated);
        if (connections.putIfAbsent(session.getId(), connection) != null) {
            closeQuietly(decorated, CloseStatus.PROTOCOL_ERROR);
            return;
        }
        scheduleHandshakeTimeout(connection);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        VoiceConnection connection = connections.get(session.getId());
        if (connection == null || connection.closed.get()) {
            return;
        }
        try {
            JsonNode command = objectMapper.readTree(message.getPayload());
            String type = command.path("type").asText("");
            switch (type) {
                case START_TYPE -> start(connection, command);
                case FINISH_TYPE -> finish(connection);
                case CANCEL_TYPE -> cancel(connection);
                case PING_TYPE -> send(connection, message("pong"));
                default -> fail(connection, "INVALID_COMMAND", "无法识别的语音输入指令", null,
                        CloseStatus.POLICY_VIOLATION);
            }
        } catch (Exception exception) {
            fail(connection, "INVALID_COMMAND", "语音输入指令格式错误", exception,
                    CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        VoiceConnection connection = connections.get(session.getId());
        if (connection == null || connection.closed.get()) {
            return;
        }
        StreamingSpeechToTextSession asrSession = connection.asrSession;
        if (!connection.started || asrSession == null || connection.finishing.get()) {
            fail(connection, "NOT_STARTED", "语音输入尚未开始或已经结束", null,
                    CloseStatus.POLICY_VIOLATION);
            return;
        }

        ByteBuffer payload = message.getPayload();
        int frameBytes = payload.remaining();
        if (frameBytes == 0 || frameBytes > properties.getMaxFrameBytes() || frameBytes % 2 != 0) {
            fail(connection, "INVALID_AUDIO_FRAME", "音频分片大小或格式不正确", null,
                    CloseStatus.POLICY_VIOLATION);
            return;
        }
        long totalBytes = connection.audioBytes.addAndGet(frameBytes);
        if (totalBytes > properties.getMaxAudioBytes()) {
            fail(connection, "AUDIO_TOO_LARGE", "语音输入时长已达到上限", null,
                    CloseStatus.POLICY_VIOLATION);
            return;
        }

        byte[] copied = new byte[frameBytes];
        payload.get(copied);
        try {
            asrSession.sendAudioFrame(ByteBuffer.wrap(copied));
            scheduleIdleTimeout(connection);
        } catch (RuntimeException exception) {
            fail(connection, "ASR_FAILED", "语音识别服务暂时不可用", exception,
                    CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        VoiceConnection connection = connections.remove(session.getId());
        if (connection == null || !connection.closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeouts(connection);
        cancelAsr(connection);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        VoiceConnection connection = connections.get(session.getId());
        if (connection != null) {
            fail(connection, "CONNECTION_FAILED", "语音输入连接已断开", exception,
                    CloseStatus.SERVER_ERROR);
        }
    }

    @PreDestroy
    public void shutdown() {
        timeoutExecutor.shutdownNow();
        connections.values().forEach(connection -> {
            cancelTimeouts(connection);
            cancelAsr(connection);
            closeQuietly(connection.socket, CloseStatus.GOING_AWAY);
        });
        connections.clear();
    }

    private void start(VoiceConnection connection, JsonNode command) {
        synchronized (connection) {
            if (connection.started) {
                fail(connection, "ALREADY_STARTED", "语音输入已经开始", null,
                        CloseStatus.POLICY_VIOLATION);
                return;
            }
            String authorization = command.path("authorization").asText(null);
            if (!StringUtils.hasText(authorization)) {
                fail(connection, "UNAUTHORIZED", "语音输入需要登录", null,
                        CloseStatus.POLICY_VIOLATION);
                return;
            }
            int sampleRate = command.path("sampleRate").asInt(0);
            int channels = command.path("channels").asInt(0);
            String format = command.path("format").asText("");
            if (sampleRate != SAMPLE_RATE || channels != CHANNELS || !PCM_FORMAT.equals(format)) {
                fail(connection, "UNSUPPORTED_AUDIO", "浏览器音频格式必须为 16kHz 单声道 PCM", null,
                        CloseStatus.POLICY_VIOLATION);
                return;
            }

            try {
                connection.userId = tokenAuthenticator.authenticate(authorization);
                connection.started = true;
            } catch (RuntimeException exception) {
                fail(connection, "UNAUTHORIZED", "登录状态已失效，请重新登录", exception,
                        CloseStatus.POLICY_VIOLATION);
                return;
            }
        }

        StreamingSpeechToTextSession asrSession;
        try {
            asrSession = speechModelRegistry.startStreaming(new Listener(connection));
        } catch (RuntimeException exception) {
            fail(connection, "MODEL_UNAVAILABLE", "当前没有可用的流式语音识别模型", exception,
                    CloseStatus.SERVER_ERROR);
            return;
        }

        synchronized (connection) {
            if (connection.closed.get()) {
                asrSession.cancel();
                return;
            }
            connection.asrSession = asrSession;
        }
        cancelHandshakeTimeout(connection);
        scheduleMaxDurationTimeout(connection);
        scheduleIdleTimeout(connection);
        send(connection, readyMessage(asrSession.modelId()));
    }

    private void finish(VoiceConnection connection) {
        StreamingSpeechToTextSession asrSession = connection.asrSession;
        if (!connection.started || asrSession == null) {
            fail(connection, "NOT_STARTED", "语音输入尚未开始", null, CloseStatus.POLICY_VIOLATION);
            return;
        }
        if (connection.finishing.compareAndSet(false, true)) {
            cancelIdleTimeout(connection);
            asrSession.finish();
        }
    }

    private void cancel(VoiceConnection connection) {
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeouts(connection);
        cancelAsr(connection);
        send(connection, message("cancelled"));
        closeQuietly(connection.socket, CloseStatus.NORMAL);
        connections.remove(connection.socket.getId());
    }

    private void complete(VoiceConnection connection) {
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeouts(connection);
        ObjectNode completed = message("completed");
        completed.put("transcript", connection.transcript.transcript());
        send(connection, completed);
        closeQuietly(connection.socket, CloseStatus.NORMAL);
        connections.remove(connection.socket.getId());
    }

    private void fail(VoiceConnection connection, String code, String message,
                      Throwable cause, CloseStatus closeStatus) {
        if (cause != null) {
            log.warn("日记语音输入失败: connectionId={}, code={}, userId={}",
                    connection.socket.getId(), code, connection.userId, cause);
        }
        if (!connection.closed.compareAndSet(false, true)) {
            return;
        }
        cancelTimeouts(connection);
        cancelAsr(connection);
        ObjectNode error = message("error");
        error.put("code", code);
        error.put("message", message);
        send(connection, error);
        closeQuietly(connection.socket, closeStatus);
        connections.remove(connection.socket.getId());
    }

    private void cancelAsr(VoiceConnection connection) {
        StreamingSpeechToTextSession asrSession = connection.asrSession;
        if (asrSession != null) {
            try {
                asrSession.cancel();
            } catch (RuntimeException exception) {
                log.debug("取消日记语音 ASR 会话失败: connectionId={}", connection.socket.getId(), exception);
            }
        }
    }

    private ObjectNode readyMessage(String modelId) {
        ObjectNode ready = message("ready");
        ready.put("modelId", modelId);
        ready.put("format", PCM_FORMAT);
        ready.put("sampleRate", SAMPLE_RATE);
        ready.put("channels", CHANNELS);
        return ready;
    }

    private ObjectNode message(String type) {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("type", type);
        return result;
    }

    private void send(VoiceConnection connection, ObjectNode message) {
        if (!connection.socket.isOpen()) {
            return;
        }
        try {
            connection.socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException exception) {
            log.debug("发送日记语音 WebSocket 消息失败: connectionId={}", connection.socket.getId(), exception);
        }
    }

    private void scheduleHandshakeTimeout(VoiceConnection connection) {
        connection.handshakeTimeout = timeoutExecutor.schedule(
                () -> fail(connection, "HANDSHAKE_TIMEOUT", "语音输入握手超时", null,
                        CloseStatus.POLICY_VIOLATION),
                Math.max(1, properties.getHandshakeTimeoutSeconds()), TimeUnit.SECONDS);
    }

    private void scheduleMaxDurationTimeout(VoiceConnection connection) {
        connection.maxDurationTimeout = timeoutExecutor.schedule(
                () -> fail(connection, "MAX_DURATION", "语音输入时长已达到上限", null,
                        CloseStatus.POLICY_VIOLATION),
                Math.max(1, properties.getMaxDurationSeconds()), TimeUnit.SECONDS);
    }

    private void scheduleIdleTimeout(VoiceConnection connection) {
        cancelIdleTimeout(connection);
        connection.idleTimeout = timeoutExecutor.schedule(
                () -> fail(connection, "IDLE_TIMEOUT", "长时间没有收到音频数据", null,
                        CloseStatus.POLICY_VIOLATION),
                Math.max(1, properties.getIdleTimeoutSeconds()), TimeUnit.SECONDS);
    }

    private void cancelHandshakeTimeout(VoiceConnection connection) {
        cancel(connection.handshakeTimeout);
        connection.handshakeTimeout = null;
    }

    private void cancelIdleTimeout(VoiceConnection connection) {
        cancel(connection.idleTimeout);
        connection.idleTimeout = null;
    }

    private void cancelTimeouts(VoiceConnection connection) {
        cancel(connection.handshakeTimeout);
        cancel(connection.idleTimeout);
        cancel(connection.maxDurationTimeout);
    }

    private void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.close(status);
        } catch (IOException exception) {
            log.debug("关闭日记语音 WebSocket 失败: connectionId={}", session.getId(), exception);
        }
    }

    private final class Listener implements StreamingSpeechToTextListener {
        private final VoiceConnection connection;

        private Listener(VoiceConnection connection) {
            this.connection = connection;
        }

        @Override
        public void onEvent(StreamingTranscriptionEvent event) {
            if (connection.closed.get() || event == null || !StringUtils.hasText(event.text())) {
                return;
            }
            String text = event.text().trim();
            ObjectNode result = message(event.sentenceEnd() ? "final" : "partial");
            result.put("text", text);
            if (event.sentenceId() != null) {
                result.put("sentenceId", event.sentenceId());
            }
            if (event.sentenceEnd()) {
                connection.transcript.add(event.sentenceId(), text);
            }
            send(connection, result);
        }

        @Override
        public void onComplete() {
            complete(connection);
        }

        @Override
        public void onError(Exception exception) {
            fail(connection, "ASR_FAILED", "语音识别服务暂时不可用", exception, CloseStatus.SERVER_ERROR);
        }
    }

    private static final class VoiceConnection {
        private final WebSocketSession socket;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicBoolean finishing = new AtomicBoolean();
        private final AtomicLong audioBytes = new AtomicLong();
        private final TranscriptAccumulator transcript = new TranscriptAccumulator();
        private volatile String userId;
        private volatile boolean started;
        private volatile StreamingSpeechToTextSession asrSession;
        private volatile ScheduledFuture<?> handshakeTimeout;
        private volatile ScheduledFuture<?> idleTimeout;
        private volatile ScheduledFuture<?> maxDurationTimeout;

        private VoiceConnection(WebSocketSession socket) {
            this.socket = socket;
        }
    }

    private static final class TranscriptAccumulator {
        private final Map<String, String> segments = new LinkedHashMap<>();
        private long sequence;

        private synchronized void add(Long sentenceId, String text) {
            String key = sentenceId == null ? "sequence-" + sequence++ : "sentence-" + sentenceId;
            segments.put(key, text);
        }

        private synchronized String transcript() {
            return String.join("", segments.values());
        }
    }
}
