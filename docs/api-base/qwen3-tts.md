实时语音合成-千问提供流式文本输入与流式音频输出能力，提供多种拟人音色，支持多语种/方言合成，可在同一音色下输出多语种，并能自适应调节语气，流畅处理复杂文本。

## **核心功能**

-   实时生成高保真语音，支持中英等多语种自然发声
    
-   提供[声音复刻（Qwen）](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-cloning)与[声音设计（Qwen）](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-design)两种音色定制方式
    
-   支持流式输入输出，低延迟响应实时交互场景
    
-   可调节语速、语调、音量与码率，精细控制语音表现
    
-   兼容主流音频格式，最高支持48kHz采样率输出
    
-   支持[指令控制](#12884a10929p9)，可通过自然语言指令控制语音表现力
    

## **适用范围**

**支持的模型：**

## 中国内地

在[中国内地部署模式](https://help.aliyun.com/zh/model-studio/regions/#080da663a75xh)下，接入点与数据存储均位于**北京地域**，模型推理计算资源仅限于中国内地。

调用以下模型时，请选择北京地域的[API Key](https://bailian.console.aliyun.com/?tab=model#/api-key)：

-   **千问3-TTS-Instruct-Flash-Realtime**：qwen3-tts-instruct-flash-realtime（稳定版，当前等同qwen3-tts-instruct-flash-realtime-2026-01-22）、qwen3-tts-instruct-flash-realtime-2026-01-22（最新快照版）
    
-   **千问3-TTS-VD-Realtime****：**qwen3-tts-vd-realtime-2026-01-15（最新快照版）、qwen3-tts-vd-realtime-2025-12-16（快照版）
    
-   **千问3-TTS-VC-Realtime****：**qwen3-tts-vc-realtime-2026-01-15（最新快照版）、qwen3-tts-vc-realtime-2025-11-27（快照版）
    
-   **千问3-TTS-Flash-Realtime****：**qwen3-tts-flash-realtime（稳定版，当前等同qwen3-tts-flash-realtime-2025-11-27）、qwen3-tts-flash-realtime-2025-11-27（最新快照版）、qwen3-tts-flash-realtime-2025-09-18（快照版）
    
-   **千问-TTS-Realtime****：**qwen-tts-realtime（稳定版，当前等同qwen-tts-realtime-2025-07-15）、qwen-tts-realtime-latest（最新版，当前等同qwen-tts-realtime-2025-07-15）、qwen-tts-realtime-2025-07-15（快照版）
    

## 国际

在[国际部署模式](https://help.aliyun.com/zh/model-studio/regions/#080da663a75xh)下，接入点与数据存储均位于**新加坡地域**，模型推理计算资源在全球范围内动态调度（不含中国内地）。

调用以下模型时，请选择新加坡地域的[API Key](https://modelstudio.console.aliyun.com/?tab=dashboard#/api-key)：

-   **千问3-TTS-Instruct-Flash-Realtime**：qwen3-tts-instruct-flash-realtime（稳定版，当前等同qwen3-tts-instruct-flash-realtime-2026-01-22）、qwen3-tts-instruct-flash-realtime-2026-01-22（最新快照版）
    
-   **千问3-TTS-VD-Realtime****：**qwen3-tts-vd-realtime-2026-01-15（最新快照版）、qwen3-tts-vd-realtime-2025-12-16（快照版）
    
-   **千问3-TTS-VC-Realtime****：**qwen3-tts-vc-realtime-2026-01-15（最新快照版）、qwen3-tts-vc-realtime-2025-11-27（快照版）
    
-   **千问3-TTS-Flash-Realtime****：**qwen3-tts-flash-realtime（稳定版，当前等同qwen3-tts-flash-realtime-2025-11-27）、qwen3-tts-flash-realtime-2025-11-27（最新快照版）、qwen3-tts-flash-realtime-2025-09-18（快照版）
    

更多信息请参见[模型列表](https://help.aliyun.com/zh/model-studio/models)

## **模型选型**

| **场景** | **推荐模型** | **推荐理由** |
| --- | --- | --- |
| **品牌形象、专属声音、扩展系统音色等语音定制（基于文本描述）** | qwen3-tts-vd-realtime-2026-01-15 | 支持声音设计，无需音频样本，通过文本描述创建定制化音色，适合从零开始设计品牌专属声音 |
| **品牌形象、专属声音、扩展系统音色等语音定制（基于音频样本）** | qwen3-tts-vc-realtime-2026-01-15 | 支持声音复刻，基于真实音频样本快速复刻音色，打造拟人化品牌声纹，确保音色高度还原与一致性 |
| **情感化内容生产（有声书、广播剧、游戏/动画配音）** | qwen3-tts-instruct-flash-realtime | 支持指令控制，通过自然语言描述精确控制音调、语速、情感、角色性格，适合需要丰富表现力和角色塑造的场景 |
| **专业播音（新闻、纪录片、广告宣传）** | qwen3-tts-instruct-flash-realtime | 支持指令控制，可描述播音风格和语气特点（如“庄重权威”、“轻松亲切”），适合专业内容制作 |
| **智能客服与对话机器人** | qwen3-tts-flash-realtime、qwen3-tts-instruct-flash-realtime | 支持流式输入输出，可调节语速音高；instruct版本支持指令控制，可根据对话场景动态调整语气（安抚、热情、专业） |
| **多语种内容播报** | qwen3-tts-flash-realtime、qwen3-tts-instruct-flash-realtime | 支持多种语言与中文方言，覆盖全球化内容分发需求 |
| **有声阅读与常规内容生产** | qwen3-tts-flash-realtime、qwen3-tts-instruct-flash-realtime | 可调节音量、语速、音高，满足有声书、播客等内容精细化制作需求 |
| **电商直播与短视频配音** | qwen3-tts-flash-realtime、qwen3-tts-instruct-flash-realtime | 支持 mp3/opus 压缩格式，适合带宽受限场景 |

更多说明请参见[模型功能特性对比](#6e3883d028fqq)

## **快速开始**

运行代码前，需要[获取并配置 API Key](https://help.aliyun.com/zh/model-studio/get-api-key)。如果通过SDK调用，还需要[安装最新版DashScope SDK](https://help.aliyun.com/zh/model-studio/install-sdk)。

## 使用系统音色进行语音合成

以下示例演示如何使用系统音色（参见[支持的音色](#bac280ddf5a1u)）进行语音合成。

如需使用[指令控制](#12884a10929p9)功能，请将`model`替换为`qwen3-tts-instruct-flash-realtime`，并通过`instructions`参数设置指令。

## 使用DashScope SDK

## Python

### **server commit模式**

```
import os
import base64
import threading
import time
import dashscope
from dashscope.audio.qwen_tts_realtime import *

qwen_tts_realtime: QwenTtsRealtime = None
text_to_synthesize = [
    '对吧~我就特别喜欢这种超市，',
    '尤其是过年的时候',
    '去逛超市',
    '就会觉得',
    '超级超级开心！',
    '想买好多好多的东西呢！'
]

DO_VIDEO_TEST = False

def init_dashscope_api_key():
    """
        Set your DashScope API-key. More information:
        https://github.com/aliyun/alibabacloud-bailian-speech-demo/blob/master/PREREQUISITES.md
    """

    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    if 'DASHSCOPE_API_KEY' in os.environ:
        dashscope.api_key = os.environ[
            'DASHSCOPE_API_KEY']  # load API-key from environment variable DASHSCOPE_API_KEY
    else:
        dashscope.api_key = 'your-dashscope-api-key'  # set API-key manually



class MyCallback(QwenTtsRealtimeCallback):
    def __init__(self):
        self.complete_event = threading.Event()
        self.file = open('result_24k.pcm', 'wb')

    def on_open(self) -> None:
        print('connection opened, init player')

    def on_close(self, close_status_code, close_msg) -> None:
        self.file.close()
        print('connection closed with code: {}, msg: {}, destroy player'.format(close_status_code, close_msg))

    def on_event(self, response: str) -> None:
        try:
            global qwen_tts_realtime
            type = response['type']
            if 'session.created' == type:
                print('start session: {}'.format(response['session']['id']))
            if 'response.audio.delta' == type:
                recv_audio_b64 = response['delta']
                self.file.write(base64.b64decode(recv_audio_b64))
            if 'response.done' == type:
                print(f'response {qwen_tts_realtime.get_last_response_id()} done')
            if 'session.finished' == type:
                print('session finished')
                self.complete_event.set()
        except Exception as e:
            print('[Error] {}'.format(e))
            return

    def wait_for_finished(self):
        self.complete_event.wait()


if __name__  == '__main__':
    init_dashscope_api_key()

    print('Initializing ...')

    callback = MyCallback()

    qwen_tts_realtime = QwenTtsRealtime(
        # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime
        model='qwen3-tts-flash-realtime',
        callback=callback, 
        # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
        url='wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
        )

    qwen_tts_realtime.connect()
    qwen_tts_realtime.update_session(
        voice = 'Cherry',
        response_format = AudioFormat.PCM_24000HZ_MONO_16BIT,
        # 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash-realtime
        # instructions='语速较快，带有明显的上扬语调，适合介绍时尚产品。',
        # optimize_instructions=True,
        mode = 'server_commit'        
    )
    for text_chunk in text_to_synthesize:
        print(f'send text: {text_chunk}')
        qwen_tts_realtime.append_text(text_chunk)
        time.sleep(0.1)
    qwen_tts_realtime.finish()
    callback.wait_for_finished()
    print('[Metric] session: {}, first audio delay: {}'.format(
                    qwen_tts_realtime.get_session_id(), 
                    qwen_tts_realtime.get_first_audio_delay(),
                    ))
```

### **commit模式**

```
import base64
import os
import threading
import dashscope
from dashscope.audio.qwen_tts_realtime import *

qwen_tts_realtime: QwenTtsRealtime = None
text_to_synthesize = [
    '这是第一句话。',
    '这是第二句话。',
    '这是第三句话。',
]

DO_VIDEO_TEST = False

def init_dashscope_api_key():
    """
        Set your DashScope API-key. More information:
        https://github.com/aliyun/alibabacloud-bailian-speech-demo/blob/master/PREREQUISITES.md
    """

    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    if 'DASHSCOPE_API_KEY' in os.environ:
        dashscope.api_key = os.environ[
            'DASHSCOPE_API_KEY']  # load API-key from environment variable DASHSCOPE_API_KEY
    else:
        dashscope.api_key = 'your-dashscope-api-key'  # set API-key manually



class MyCallback(QwenTtsRealtimeCallback):
    def __init__(self):
        super().__init__()
        self.response_counter = 0
        self.complete_event = threading.Event()
        self.file = open(f'result_{self.response_counter}_24k.pcm', 'wb')

    def reset_event(self):
        self.response_counter += 1
        self.file = open(f'result_{self.response_counter}_24k.pcm', 'wb')
        self.complete_event = threading.Event()

    def on_open(self) -> None:
        print('connection opened, init player')

    def on_close(self, close_status_code, close_msg) -> None:
        print('connection closed with code: {}, msg: {}, destroy player'.format(close_status_code, close_msg))

    def on_event(self, response: str) -> None:
        try:
            global qwen_tts_realtime
            type = response['type']
            if 'session.created' == type:
                print('start session: {}'.format(response['session']['id']))
            if 'response.audio.delta' == type:
                recv_audio_b64 = response['delta']
                self.file.write(base64.b64decode(recv_audio_b64))
            if 'response.done' == type:
                print(f'response {qwen_tts_realtime.get_last_response_id()} done')
                self.complete_event.set()
                self.file.close()
            if 'session.finished' == type:
                print('session finished')
                self.complete_event.set()
        except Exception as e:
            print('[Error] {}'.format(e))
            return

    def wait_for_response_done(self):
        self.complete_event.wait()


if __name__  == '__main__':
    init_dashscope_api_key()

    print('Initializing ...')

    callback = MyCallback()

    qwen_tts_realtime = QwenTtsRealtime(
        # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime
        model='qwen3-tts-flash-realtime',
        callback=callback,
        # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
        url='wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
        )

    qwen_tts_realtime.connect()
    qwen_tts_realtime.update_session(
        voice = 'Cherry',
        response_format = AudioFormat.PCM_24000HZ_MONO_16BIT,
        # 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash-realtime
        # instructions='语速较快，带有明显的上扬语调，适合介绍时尚产品。',
        # optimize_instructions=True,
        mode = 'commit'        
    )
    print(f'send text: {text_to_synthesize[0]}')
    qwen_tts_realtime.append_text(text_to_synthesize[0])
    qwen_tts_realtime.commit()
    callback.wait_for_response_done()
    callback.reset_event()
    
    print(f'send text: {text_to_synthesize[1]}')
    qwen_tts_realtime.append_text(text_to_synthesize[1])
    qwen_tts_realtime.commit()
    callback.wait_for_response_done()
    callback.reset_event()

    print(f'send text: {text_to_synthesize[2]}')
    qwen_tts_realtime.append_text(text_to_synthesize[2])
    qwen_tts_realtime.commit()
    callback.wait_for_response_done()
    
    qwen_tts_realtime.finish()
    print('[Metric] session: {}, first audio delay: {}'.format(
                    qwen_tts_realtime.get_session_id(), 
                    qwen_tts_realtime.get_first_audio_delay(),
                    ))
```

## Java

### **server commit模式**

```
import com.alibaba.dashscope.audio.qwen_tts_realtime.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    static String[] textToSynthesize = {
            "对吧~我就特别喜欢这种超市",
            "尤其是过年的时候",
            "去逛超市",
            "就会觉得",
            "超级超级开心！",
            "想买好多好多的东西呢！"
    };
    public static QwenTtsRealtimeAudioFormat ttsFormat = QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT;

    // 实时PCM音频播放器类
    public static class RealtimePcmPlayer {
        private int sampleRate;
        private SourceDataLine line;
        private AudioFormat audioFormat;
        private Thread decoderThread;
        private Thread playerThread;
        private AtomicBoolean stopped = new AtomicBoolean(false);
        private Queue<String> b64AudioBuffer = new ConcurrentLinkedQueue<>();
        private Queue<byte[]> RawAudioBuffer = new ConcurrentLinkedQueue<>();
        private ByteArrayOutputStream totalAudioStream = new ByteArrayOutputStream();

        // 构造函数初始化音频格式和音频线路
        public RealtimePcmPlayer(int sampleRate) throws LineUnavailableException {
            this.sampleRate = sampleRate;
            this.audioFormat = new AudioFormat(this.sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(audioFormat);
            line.start();
            decoderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        String b64Audio = b64AudioBuffer.poll();
                        if (b64Audio != null) {
                            byte[] rawAudio = Base64.getDecoder().decode(b64Audio);
                            RawAudioBuffer.add(rawAudio);
                            // 将音频数据写入 totalAudioStream
                            try {
                                totalAudioStream.write(rawAudio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            playerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        byte[] rawAudio = RawAudioBuffer.poll();
                        if (rawAudio != null) {
                            try {
                                playChunk(rawAudio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            decoderThread.start();
            playerThread.start();
        }

        // 播放一个音频块并阻塞直到播放完成
        private void playChunk(byte[] chunk) throws IOException, InterruptedException {
            if (chunk == null || chunk.length == 0) return;

            int bytesWritten = 0;
            while (bytesWritten < chunk.length) {
                bytesWritten += line.write(chunk, bytesWritten, chunk.length - bytesWritten);
            }
            int audioLength = chunk.length / (this.sampleRate*2/1000);
            // 等待缓冲区中的音频播放完成
            Thread.sleep(audioLength - 10);
        }

        public void write(String b64Audio) {
            b64AudioBuffer.add(b64Audio);
        }

        public void cancel() {
            b64AudioBuffer.clear();
            RawAudioBuffer.clear();
        }

        public void waitForComplete() throws InterruptedException {
            while (!b64AudioBuffer.isEmpty() || !RawAudioBuffer.isEmpty()) {
                Thread.sleep(100);
            }
            line.drain();
        }

        public void shutdown() throws InterruptedException, IOException {
            stopped.set(true);
            decoderThread.join();
            playerThread.join();

            // 保存完整音频文件
            File file = new File("TotalAudio_"+ttsFormat.getSampleRate()+"."+ttsFormat.getFormat());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(totalAudioStream.toByteArray());
            }

            if (line != null && line.isRunning()) {
                line.drain();
                line.close();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, LineUnavailableException, IOException {
        QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                // 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime
                .model("qwen3-tts-flash-realtime")
                // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                .apikey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
        AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<QwenTtsRealtime> qwenTtsRef = new AtomicReference<>(null);

        // 创建实时音频播放器实例
        RealtimePcmPlayer audioPlayer = new RealtimePcmPlayer(24000);

        QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
            @Override
            public void onOpen() {
                // 连接建立时的处理
            }
            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch(type) {
                    case "session.created":
                        // 会话创建时的处理
                        if (message.has("session")) {
                            String eventId = message.get("event_id").getAsString();
                            String sessionId = message.get("session").getAsJsonObject().get("id").getAsString();
                            System.out.println("[onEvent] session.created, session_id: "
                                    + sessionId + ", event_id: " + eventId);
                        }
                        break;
                    case "response.audio.delta":
                        String recvAudioB64 = message.get("delta").getAsString();
                        // 实时播放音频
                        audioPlayer.write(recvAudioB64);
                        break;
                    case "response.done":
                        // 响应完成时的处理
                        break;
                    case "session.finished":
                        // 会话结束时的处理
                        completeLatch.get().countDown();
                    default:
                        break;
                }
            }
            @Override
            public void onClose(int code, String reason) {
                // 连接关闭时的处理
            }
        });
        qwenTtsRef.set(qwenTtsRealtime);
        try {
            qwenTtsRealtime.connect();
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
        QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                .voice("Cherry")
                .responseFormat(ttsFormat)
                .mode("server_commit")
                // 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash-realtime
                // .instructions("")
                // .optimizeInstructions(true)
                .build();
        qwenTtsRealtime.updateSession(config);
        for (String text:textToSynthesize) {
            qwenTtsRealtime.appendText(text);
            Thread.sleep(100);
        }
        qwenTtsRealtime.finish();
        completeLatch.get().await();
        qwenTtsRealtime.close();

        // 等待音频播放完成并关闭播放器
        audioPlayer.waitForComplete();
        audioPlayer.shutdown();
        System.exit(0);
    }
}
```

### **commit模式**

```
import com.alibaba.dashscope.audio.qwen_tts_realtime.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.JsonObject;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.AudioSystem;
import java.io.*;
import java.util.Base64;
import java.util.Queue;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    public static QwenTtsRealtimeAudioFormat ttsFormat = QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT;
    // 实时PCM音频播放器类
    public static class RealtimePcmPlayer {
        private int sampleRate;
        private SourceDataLine line;
        private AudioFormat audioFormat;
        private Thread decoderThread;
        private Thread playerThread;
        private AtomicBoolean stopped = new AtomicBoolean(false);
        private Queue<String> b64AudioBuffer = new ConcurrentLinkedQueue<>();
        private Queue<byte[]> RawAudioBuffer = new ConcurrentLinkedQueue<>();
        private ByteArrayOutputStream totalAudioStream = new ByteArrayOutputStream();


        // 构造函数初始化音频格式和音频线路
        public RealtimePcmPlayer(int sampleRate) throws LineUnavailableException {
            this.sampleRate = sampleRate;
            this.audioFormat = new AudioFormat(this.sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(audioFormat);
            line.start();
            decoderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        String b64Audio = b64AudioBuffer.poll();
                        if (b64Audio != null) {
                            byte[] rawAudio = Base64.getDecoder().decode(b64Audio);
                            RawAudioBuffer.add(rawAudio);
                            // 将音频数据写入 totalAudioStream
                            try {
                                totalAudioStream.write(rawAudio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            playerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        byte[] rawAudio = RawAudioBuffer.poll();
                        if (rawAudio != null) {
                            try {
                                playChunk(rawAudio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            decoderThread.start();
            playerThread.start();
        }

        // 播放一个音频块并阻塞直到播放完成
        private void playChunk(byte[] chunk) throws IOException, InterruptedException {
            if (chunk == null || chunk.length == 0) return;

            int bytesWritten = 0;
            while (bytesWritten < chunk.length) {
                bytesWritten += line.write(chunk, bytesWritten, chunk.length - bytesWritten);
            }
            int audioLength = chunk.length / (this.sampleRate*2/1000);
            // 等待缓冲区中的音频播放完成
            Thread.sleep(audioLength - 10);
        }

        public void write(String b64Audio) {
            b64AudioBuffer.add(b64Audio);
        }

        public void cancel() {
            b64AudioBuffer.clear();
            RawAudioBuffer.clear();
        }

        public void waitForComplete() throws InterruptedException {
            // 等待所有缓冲区中的音频数据播放完成
            while (!b64AudioBuffer.isEmpty() || !RawAudioBuffer.isEmpty()) {
                Thread.sleep(100);
            }
            // 等待音频线路播放完成
            line.drain();
        }

        public void shutdown() throws InterruptedException {
            stopped.set(true);
            decoderThread.join();
            playerThread.join();
            // 保存完整音频文件
            File file = new File("TotalAudio_"+ttsFormat.getSampleRate()+"."+ttsFormat.getFormat());
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(totalAudioStream.toByteArray());
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            if (line != null && line.isRunning()) {
                line.drain();
                line.close();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException, LineUnavailableException, FileNotFoundException {
        Scanner scanner = new Scanner(System.in);

        QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                // 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime
                .model("qwen3-tts-flash-realtime")
                // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                .apikey(System.getenv("DASHSCOPE_API_KEY"))
                .build();

        AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>(new CountDownLatch(1));

        // 创建实时播放器实例
        RealtimePcmPlayer audioPlayer = new RealtimePcmPlayer(24000);

        final AtomicReference<QwenTtsRealtime> qwenTtsRef = new AtomicReference<>(null);
        QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
            //            File file = new File("result_24k.pcm");
//            FileOutputStream fos = new FileOutputStream(file);
            @Override
            public void onOpen() {
                System.out.println("connection opened");
                System.out.println("输入文本并按Enter发送，输入'quit'退出程序");
            }
            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch(type) {
                    case "session.created":
                        System.out.println("start session: " + message.get("session").getAsJsonObject().get("id").getAsString());
                        break;
                    case "response.audio.delta":
                        String recvAudioB64 = message.get("delta").getAsString();
                        byte[] rawAudio = Base64.getDecoder().decode(recvAudioB64);
                        //                            fos.write(rawAudio);
                        // 实时播放音频
                        audioPlayer.write(recvAudioB64);
                        break;
                    case "response.done":
                        System.out.println("response done");
                        // 等待音频播放完成
                        try {
                            audioPlayer.waitForComplete();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        // 为下一次输入做准备
                        completeLatch.get().countDown();
                        break;
                    case "session.finished":
                        System.out.println("session finished");
                        if (qwenTtsRef.get() != null) {
                            System.out.println("[Metric] response: " + qwenTtsRef.get().getResponseId() +
                                    ", first audio delay: " + qwenTtsRef.get().getFirstAudioDelay() + " ms");
                        }
                        completeLatch.get().countDown();
                    default:
                        break;
                }
            }
            @Override
            public void onClose(int code, String reason) {
                System.out.println("connection closed code: " + code + ", reason: " + reason);
                try {
//                    fos.close();
                    // 等待播放完成并关闭播放器
                    audioPlayer.waitForComplete();
                    audioPlayer.shutdown();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        qwenTtsRef.set(qwenTtsRealtime);
        try {
            qwenTtsRealtime.connect();
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
        QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                .voice("Cherry")
                .responseFormat(ttsFormat)
                .mode("commit")
                // 如需使用指令控制功能，请取消下方注释，并将model替换为qwen3-tts-instruct-flash-realtime
                // .instructions("")
                // .optimizeInstructions(true)
                .build();
        qwenTtsRealtime.updateSession(config);

        // 循环读取用户输入
        while (true) {
            System.out.print("请输入要合成的文本: ");
            String text = scanner.nextLine();

            // 如果用户输入quit，则退出程序
            if ("quit".equalsIgnoreCase(text.trim())) {
                System.out.println("正在关闭连接...");
                qwenTtsRealtime.finish();
                completeLatch.get().await();
                break;
            }

            // 如果用户输入为空，跳过
            if (text.trim().isEmpty()) {
                continue;
            }

            // 重新初始化倒计时锁存器
            completeLatch.set(new CountDownLatch(1));

            // 发送文本
            qwenTtsRealtime.appendText(text);
            qwenTtsRealtime.commit();

            // 等待本次合成完成
            completeLatch.get().await();
        }

        // 清理资源
        audioPlayer.waitForComplete();
        audioPlayer.shutdown();
        scanner.close();
        System.exit(0);
    }
}
```

## 使用WebSocket API

1.  **准备运行环境**
    
    根据您的操作系统安装 pyaudio。
    
    ## macOS
    
    ```
    brew install portaudio && pip install pyaudio
    ```
    
    ## Debian/Ubuntu
    
    ```
    sudo apt-get install python3-pyaudio
    
    或者
    
    pip install pyaudio
    ```
    
    ## CentOS
    
    ```
    sudo yum install -y portaudio portaudio-devel && pip install pyaudio
    ```
    
    ## Windows
    
    ```
    pip install pyaudio
    ```
    
    安装完成后，通过 pip 安装 websocket 相关的依赖：
    
    ```
    pip install websocket-client==1.8.0 websockets
    ```
    
2.  **创建客户端**
    
    在本地新建 Python 文件，命名为`tts_realtime_client.py`并复制以下代码到文件中：
    
    tts\_realtime\_client.py
    
    ```
    # -- coding: utf-8 --
    
    import asyncio
    import websockets
    import json
    import base64
    import time
    from typing import Optional, Callable, Dict, Any
    from enum import Enum
    
    
    class SessionMode(Enum):
        SERVER_COMMIT = "server_commit"
        COMMIT = "commit"
    
    
    class TTSRealtimeClient:
        """
        与 TTS Realtime API 交互的客户端。
    
        该类提供了连接 TTS Realtime API、发送文本数据、获取音频输出以及管理 WebSocket 连接的相关方法。
    
        属性说明:
            base_url (str):
                Realtime API 的基础地址。
            api_key (str):
                用于身份验证的 API Key。
            voice (str):
                服务器合成语音所使用的声音。
            mode (SessionMode):
                会话模式，可选 server_commit 或 commit。
            audio_callback (Callable[[bytes], None]):
                接收音频数据的回调函数。
            language_type(str)
                合成的语音的语种，可选值Chinese、English、German、Italian、Portuguese、Spanish、Japanese、Korean、French、Russian、Auto
        """
    
        def __init__(
                self,
                base_url: str,
                api_key: str,
                voice: str = "Cherry",
                mode: SessionMode = SessionMode.SERVER_COMMIT,
                audio_callback: Optional[Callable[[bytes], None]] = None,
            language_type: str = "Auto"):
            self.base_url = base_url
            self.api_key = api_key
            self.voice = voice
            self.mode = mode
            self.ws = None
            self.audio_callback = audio_callback
            self.language_type = language_type
    
            # 当前回复状态
            self._current_response_id = None
            self._current_item_id = None
            self._is_responding = False
            self._response_done_future = None
    
    
        async def connect(self) -> None:
            """与 TTS Realtime API 建立 WebSocket 连接。"""
            headers = {
                "Authorization": f"Bearer {self.api_key}"
            }
    
            self.ws = await websockets.connect(self.base_url, additional_headers=headers)
    
            # 设置默认会话配置
            await self.update_session({
                "mode": self.mode.value,
                "voice": self.voice,
                # 如需使用指令控制功能，请取消下方注释，并在server_commit.py或commit.py中将model替换为qwen3-tts-instruct-flash-realtime
                # "instructions": "语速较快，带有明显的上扬语调，适合介绍时尚产品。",
                # "optimize_instructions": true
                "language_type": self.language_type,
                "response_format": "pcm",
                "sample_rate": 24000
            })
    
    
        async def send_event(self, event) -> None:
            """发送事件到服务器。"""
            event['event_id'] = "event_" + str(int(time.time() * 1000))
            print(f"发送事件: type={event['type']}, event_id={event['event_id']}")
            await self.ws.send(json.dumps(event))
    
    
        async def update_session(self, config: Dict[str, Any]) -> None:
            """更新会话配置。"""
            event = {
                "type": "session.update",
                "session": config
            }
            print("更新会话配置: ", event)
            await self.send_event(event)
    
    
        async def append_text(self, text: str) -> None:
            """向 API 发送文本数据。"""
            event = {
                "type": "input_text_buffer.append",
                "text": text
            }
            await self.send_event(event)
    
    
        async def commit_text_buffer(self) -> None:
            """提交文本缓冲区以触发处理。"""
            event = {
                "type": "input_text_buffer.commit"
            }
            await self.send_event(event)
    
    
        async def clear_text_buffer(self) -> None:
            """清除文本缓冲区。"""
            event = {
                "type": "input_text_buffer.clear"
            }
            await self.send_event(event)
    
    
        async def finish_session(self) -> None:
            """结束会话。"""
            event = {
                "type": "session.finish"
            }
            await self.send_event(event)
    
    
        async def wait_for_response_done(self):
            """等待 response.done 事件"""
            if self._response_done_future:
                await self._response_done_future
    
    
        async def handle_messages(self) -> None:
            """处理来自服务器的消息。"""
            try:
                async for message in self.ws:
                    event = json.loads(message)
                    event_type = event.get("type")
    
                    if event_type != "response.audio.delta":
                        print(f"收到事件: {event_type}")
    
                    if event_type == "error":
                        print("错误: ", event.get('error', {}))
                        continue
                    elif event_type == "session.created":
                        print("会话创建，ID: ", event.get('session', {}).get('id'))
                    elif event_type == "session.updated":
                        print("会话更新，ID: ", event.get('session', {}).get('id'))
                    elif event_type == "input_text_buffer.committed":
                        print("文本缓冲区已提交，项目ID: ", event.get('item_id'))
                    elif event_type == "input_text_buffer.cleared":
                        print("文本缓冲区已清除")
                    elif event_type == "response.created":
                        self._current_response_id = event.get("response", {}).get("id")
                        self._is_responding = True
                        # 创建新的 future 来等待 response.done
                        self._response_done_future = asyncio.Future()
                        print("响应已创建，ID: ", self._current_response_id)
                    elif event_type == "response.output_item.added":
                        self._current_item_id = event.get("item", {}).get("id")
                        print("输出项已添加，ID: ", self._current_item_id)
                    # 处理音频增量
                    elif event_type == "response.audio.delta" and self.audio_callback:
                        audio_bytes = base64.b64decode(event.get("delta", ""))
                        self.audio_callback(audio_bytes)
                    elif event_type == "response.audio.done":
                        print("音频生成完成")
                    elif event_type == "response.done":
                        self._is_responding = False
                        self._current_response_id = None
                        self._current_item_id = None
                        # 标记 future 完成
                        if self._response_done_future and not self._response_done_future.done():
                            self._response_done_future.set_result(True)
                        print("响应完成")
                    elif event_type == "session.finished":
                        print("会话已结束")
    
            except websockets.exceptions.ConnectionClosed:
                print("连接已关闭")
            except Exception as e:
                print("消息处理出错: ", str(e))
    
    
        async def close(self) -> None:
            """关闭 WebSocket 连接。"""
            if self.ws:
                await self.ws.close()
    ```
    
3.  **选择语音合成模式**
    
    Realtime API 支持以下两种模式：
    
    -   **server\_commit 模式**
        
        客户端仅发送文本。服务端会智能判断文本分段方式与合成时机。适合低延迟且无需手动控制合成节奏的场景，例如 GPS 导航。
        
    -   **commit 模式**
        
        客户端先将文本添加至缓冲区，再主动触发服务端合成指定文本。适合需精细控制断句和停顿的场景，例如新闻播报。
        
    
    ## **server\_commit 模式**
    
    在`tts_realtime_client.py`的同级目录下新建另一个 Python 文件，命名为`server_commit.py`，并将以下代码复制进文件中：
    
    server\_commit.py
    
    ```
    import os
    import asyncio
    import logging
    import wave
    from tts_realtime_client import TTSRealtimeClient, SessionMode
    import pyaudio
    
    # QwenTTS 服务配置
    # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime，并在tts_realtime_client.py中取消instructions的注释
    # 以下是北京地域url，如果使用新加坡地域的模型，需要将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime?model=qwen3-tts-flash-realtime
    URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3-tts-flash-realtime"
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用百炼API Key将下行替换为：API_KEY="sk-xxx"
    API_KEY = os.getenv("DASHSCOPE_API_KEY")
    
    if not API_KEY:
        raise ValueError("Please set DASHSCOPE_API_KEY environment variable")
    
    # 收集音频数据
    _audio_chunks = []
    # 实时播放相关
    _AUDIO_SAMPLE_RATE = 24000
    _audio_pyaudio = pyaudio.PyAudio()
    _audio_stream = None  # 将在运行时打开
    
    def _audio_callback(audio_bytes: bytes):
        """TTSRealtimeClient 音频回调: 实时播放并缓存"""
        global _audio_stream
        if _audio_stream is not None:
            try:
                _audio_stream.write(audio_bytes)
            except Exception as exc:
                logging.error(f"PyAudio playback error: {exc}")
        _audio_chunks.append(audio_bytes)
        logging.info(f"Received audio chunk: {len(audio_bytes)} bytes")
    
    def _save_audio_to_file(filename: str = "output.wav", sample_rate: int = 24000) -> bool:
        """将收集到的音频数据保存为 WAV 文件"""
        if not _audio_chunks:
            logging.warning("No audio data to save")
            return False
    
        try:
            audio_data = b"".join(_audio_chunks)
            with wave.open(filename, 'wb') as wav_file:
                wav_file.setnchannels(1)  # 单声道
                wav_file.setsampwidth(2)  # 16-bit
                wav_file.setframerate(sample_rate)
                wav_file.writeframes(audio_data)
            logging.info(f"Audio saved to: {filename}")
            return True
        except Exception as exc:
            logging.error(f"Failed to save audio: {exc}")
            return False
    
    async def _produce_text(client: TTSRealtimeClient):
        """向服务器发送文本片段"""
        text_fragments = [
            "阿里云的大模型服务平台百炼是一站式的大模型开发及应用构建平台。",
            "不论是开发者还是业务人员，都能深入参与大模型应用的设计和构建。", 
            "您可以通过简单的界面操作，在5分钟内开发出一款大模型应用，",
            "或在几小时内训练出一个专属模型，从而将更多精力专注于应用创新。",
        ]
    
        logging.info("Sending text fragments…")
        for text in text_fragments:
            logging.info(f"Sending fragment: {text}")
            await client.append_text(text)
            await asyncio.sleep(0.1)  # 片段间稍作延时
    
        # 等待服务器完成内部处理后结束会话
        await asyncio.sleep(1.0)
        await client.finish_session()
    
    async def _run_demo():
        """运行完整 Demo"""
        global _audio_stream
        # 打开 PyAudio 输出流
        _audio_stream = _audio_pyaudio.open(
            format=pyaudio.paInt16,
            channels=1,
            rate=_AUDIO_SAMPLE_RATE,
            output=True,
            frames_per_buffer=1024
        )
    
        client = TTSRealtimeClient(
            base_url=URL,
            api_key=API_KEY,
            voice="Cherry",
            mode=SessionMode.SERVER_COMMIT,
            audio_callback=_audio_callback
        )
    
        # 建立连接
        await client.connect()
    
        # 并行执行消息处理与文本发送
        consumer_task = asyncio.create_task(client.handle_messages())
        producer_task = asyncio.create_task(_produce_text(client))
    
        await producer_task  # 等待文本发送完成
    
        # 等待 response.done
        await client.wait_for_response_done()
    
        # 关闭连接并取消消费者任务
        await client.close()
        consumer_task.cancel()
    
        # 关闭音频流
        if _audio_stream is not None:
            _audio_stream.stop_stream()
            _audio_stream.close()
        _audio_pyaudio.terminate()
    
        # 保存音频数据
        os.makedirs("outputs", exist_ok=True)
        _save_audio_to_file(os.path.join("outputs", "qwen_tts_output.wav"))
    
    def main():
        """同步入口"""
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s [%(levelname)s] %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        logging.info("Starting QwenTTS Realtime Client demo…")
        asyncio.run(_run_demo())
    
    if __name__ == "__main__":
        main() 
    ```
    
    运行`server_commit.py`，即可听到 Realtime API 实时生成的音频。
    
    ## **commit 模式**
    
    在`tts_realtime_client.py`的同级目录下新建另一个 python 文件，命名为`commit.py`，并将以下代码复制进文件中：
    
    commit.py
    
    ```
    import os
    import asyncio
    import logging
    import wave
    from tts_realtime_client import TTSRealtimeClient, SessionMode
    import pyaudio
    
    # QwenTTS 服务配置
    # 如需使用指令控制功能，请将model替换为qwen3-tts-instruct-flash-realtime，并在tts_realtime_client.py中取消instructions的注释
    # 以下是北京地域url，如果使用新加坡地域的模型，需要将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime?model=qwen3-tts-flash-realtime
    URL = "wss://dashscope.aliyuncs.com/api-ws/v1/realtime?model=qwen3-tts-flash-realtime"
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用百炼API Key将下行替换为：API_KEY="sk-xxx"
    API_KEY = os.getenv("DASHSCOPE_API_KEY")
    
    if not API_KEY:
        raise ValueError("Please set DASHSCOPE_API_KEY environment variable")
    
    # 收集音频数据
    _audio_chunks = []
    _AUDIO_SAMPLE_RATE = 24000
    _audio_pyaudio = pyaudio.PyAudio()
    _audio_stream = None
    
    def _audio_callback(audio_bytes: bytes):
        """TTSRealtimeClient 音频回调: 实时播放并缓存"""
        global _audio_stream
        if _audio_stream is not None:
            try:
                _audio_stream.write(audio_bytes)
            except Exception as exc:
                logging.error(f"PyAudio playback error: {exc}")
        _audio_chunks.append(audio_bytes)
        logging.info(f"Received audio chunk: {len(audio_bytes)} bytes")
    
    def _save_audio_to_file(filename: str = "output.wav", sample_rate: int = 24000) -> bool:
        """将收集到的音频数据保存为 WAV 文件"""
        if not _audio_chunks:
            logging.warning("No audio data to save")
            return False
    
        try:
            audio_data = b"".join(_audio_chunks)
            with wave.open(filename, 'wb') as wav_file:
                wav_file.setnchannels(1)  # 单声道
                wav_file.setsampwidth(2)  # 16-bit
                wav_file.setframerate(sample_rate)
                wav_file.writeframes(audio_data)
            logging.info(f"Audio saved to: {filename}")
            return True
        except Exception as exc:
            logging.error(f"Failed to save audio: {exc}")
            return False
    
    async def _user_input_loop(client: TTSRealtimeClient):
        """持续获取用户输入并发送文本，当用户输入空文本时发送commit事件并结束本次会话"""
        print("请输入文本（直接按Enter发送commit事件并结束本次会话，按Ctrl+C或Ctrl+D结束整个程序）：")
        
        while True:
            try:
                user_text = input("> ")
                if not user_text:  # 用户输入为空
                    # 空输入视为一次对话的结束: 提交缓冲区 -> 结束会话 -> 跳出循环
                    logging.info("空输入，发送 commit 事件并结束本次会话")
                    await client.commit_text_buffer()
                    # 适当等待服务器处理 commit，防止过早结束会话导致丢失音频
                    await asyncio.sleep(0.3)
                    await client.finish_session()
                    break  # 直接退出用户输入循环，无需再次回车
                else:
                    logging.info(f"发送文本: {user_text}")
                    await client.append_text(user_text)
                    
            except EOFError:  # 用户按下Ctrl+D
                break
            except KeyboardInterrupt:  # 用户按下Ctrl+C
                break
        
        # 结束会话
        logging.info("结束会话...")
    async def _run_demo():
        """运行完整 Demo"""
        global _audio_stream
        # 打开 PyAudio 输出流
        _audio_stream = _audio_pyaudio.open(
            format=pyaudio.paInt16,
            channels=1,
            rate=_AUDIO_SAMPLE_RATE,
            output=True,
            frames_per_buffer=1024
        )
    
        client = TTSRealtimeClient(
            base_url=URL,
            api_key=API_KEY,
            voice="Cherry",
            mode=SessionMode.COMMIT,  # 修改为COMMIT模式
            audio_callback=_audio_callback
        )
    
        # 建立连接
        await client.connect()
    
        # 并行执行消息处理与用户输入
        consumer_task = asyncio.create_task(client.handle_messages())
        producer_task = asyncio.create_task(_user_input_loop(client))
    
        await producer_task  # 等待用户输入完成
    
        # 等待 response.done
        await client.wait_for_response_done()
    
        # 关闭连接并取消消费者任务
        await client.close()
        consumer_task.cancel()
    
        # 关闭音频流
        if _audio_stream is not None:
            _audio_stream.stop_stream()
            _audio_stream.close()
        _audio_pyaudio.terminate()
    
        # 保存音频数据
        os.makedirs("outputs", exist_ok=True)
        _save_audio_to_file(os.path.join("outputs", "qwen_tts_output.wav"))
    
    def main():
        logging.basicConfig(
            level=logging.INFO,
            format='%(asctime)s [%(levelname)s] %(message)s',
            datefmt='%Y-%m-%d %H:%M:%S'
        )
        logging.info("Starting QwenTTS Realtime Client demo…")
        asyncio.run(_run_demo())
    
    if __name__ == "__main__":
        main() 
    ```
    
    运行`commit.py`，可多次输入要合成的文本。在未输入文本的情况下单击 Enter 键，您将从扬声器听到 Realtime API 返回的音频。
    

## 使用声音复刻音色进行语音合成

声音复刻服务不提供预览音频，需通过语音合成接口试听并评估效果。建议使用简短文本进行初次测试。

以下示例演示了如何在语音合成中使用声音复刻生成的专属音色，实现与原音高度相似的输出效果。这里参考了使用系统音色进行语音合成DashScope SDK的“server commit模式”示例代码，将`voice`参数替换为复刻生成的专属音色进行语音合成。

-   **关键原则**：声音复刻时使用的模型 (`target_model`) 必须与后续进行语音合成时使用的模型 (`model`) 保持一致，否则会导致合成失败。
    
-   示例使用本地音频文件 `voice.mp3` 进行声音复刻，运行代码时，请注意替换。
    

## Python

```
# coding=utf-8
# Installation instructions for pyaudio:
# APPLE Mac OS X
#   brew install portaudio
#   pip install pyaudio
# Debian/Ubuntu
#   sudo apt-get install python-pyaudio python3-pyaudio
#   or
#   pip install pyaudio
# CentOS
#   sudo yum install -y portaudio portaudio-devel && pip install pyaudio
# Microsoft Windows
#   python -m pip install pyaudio

import pyaudio
import os
import requests
import base64
import pathlib
import threading
import time
import dashscope
from dashscope.audio.qwen_tts_realtime import QwenTtsRealtime, QwenTtsRealtimeCallback, AudioFormat

# ======= 常量配置 =======
DEFAULT_TARGET_MODEL = "qwen3-tts-vc-realtime-2026-01-15"  # 声音复刻、语音合成要使用相同的模型
DEFAULT_PREFERRED_NAME = "guanyu"
DEFAULT_AUDIO_MIME_TYPE = "audio/mpeg"
VOICE_FILE_PATH = "voice.mp3"  # 用于声音复刻的本地音频文件的相对路径

TEXT_TO_SYNTHESIZE = [
    '对吧~我就特别喜欢这种超市，',
    '尤其是过年的时候',
    '去逛超市',
    '就会觉得',
    '超级超级开心！',
    '想买好多好多的东西呢！'
]

def create_voice(file_path: str,
                 target_model: str = DEFAULT_TARGET_MODEL,
                 preferred_name: str = DEFAULT_PREFERRED_NAME,
                 audio_mime_type: str = DEFAULT_AUDIO_MIME_TYPE) -> str:
    """
    创建音色，并返回 voice 参数
    """
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用百炼API Key将下行替换为：api_key = "sk-xxx"
    api_key = os.getenv("DASHSCOPE_API_KEY")

    file_path_obj = pathlib.Path(file_path)
    if not file_path_obj.exists():
        raise FileNotFoundError(f"音频文件不存在: {file_path}")

    base64_str = base64.b64encode(file_path_obj.read_bytes()).decode()
    data_uri = f"data:{audio_mime_type};base64,{base64_str}"

    # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1/services/audio/tts/customization
    url = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"
    payload = {
        "model": "qwen-voice-enrollment", # 不要修改该值
        "input": {
            "action": "create",
            "target_model": target_model,
            "preferred_name": preferred_name,
            "audio": {"data": data_uri}
        }
    }
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }

    resp = requests.post(url, json=payload, headers=headers)
    if resp.status_code != 200:
        raise RuntimeError(f"创建 voice 失败: {resp.status_code}, {resp.text}")

    try:
        return resp.json()["output"]["voice"]
    except (KeyError, ValueError) as e:
        raise RuntimeError(f"解析 voice 响应失败: {e}")

def init_dashscope_api_key():
    """
    初始化 dashscope SDK 的 API key
    """
    # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
    # 若没有配置环境变量，请用百炼API Key将下行替换为：dashscope.api_key = "sk-xxx"
    dashscope.api_key = os.getenv("DASHSCOPE_API_KEY")

# ======= 回调类 =======
class MyCallback(QwenTtsRealtimeCallback):
    """
    自定义 TTS 流式回调
    """
    def __init__(self):
        self.complete_event = threading.Event()
        self._player = pyaudio.PyAudio()
        self._stream = self._player.open(
            format=pyaudio.paInt16, channels=1, rate=24000, output=True
        )

    def on_open(self) -> None:
        print('[TTS] 连接已建立')

    def on_close(self, close_status_code, close_msg) -> None:
        self._stream.stop_stream()
        self._stream.close()
        self._player.terminate()
        print(f'[TTS] 连接关闭 code={close_status_code}, msg={close_msg}')

    def on_event(self, response: dict) -> None:
        try:
            event_type = response.get('type', '')
            if event_type == 'session.created':
                print(f'[TTS] 会话开始: {response["session"]["id"]}')
            elif event_type == 'response.audio.delta':
                audio_data = base64.b64decode(response['delta'])
                self._stream.write(audio_data)
            elif event_type == 'response.done':
                print(f'[TTS] 响应完成, Response ID: {qwen_tts_realtime.get_last_response_id()}')
            elif event_type == 'session.finished':
                print('[TTS] 会话结束')
                self.complete_event.set()
        except Exception as e:
            print(f'[Error] 处理回调事件异常: {e}')

    def wait_for_finished(self):
        self.complete_event.wait()

# ======= 主执行逻辑 =======
if __name__ == '__main__':
    init_dashscope_api_key()
    print('[系统] 初始化 Qwen TTS Realtime ...')

    callback = MyCallback()
    qwen_tts_realtime = QwenTtsRealtime(
        model=DEFAULT_TARGET_MODEL,
        callback=callback,
        # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
        url='wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
    )
    qwen_tts_realtime.connect()
    
    qwen_tts_realtime.update_session(
        voice=create_voice(VOICE_FILE_PATH), # 将voice参数替换为复刻生成的专属音色
        response_format=AudioFormat.PCM_24000HZ_MONO_16BIT,
        mode='server_commit'
    )

    for text_chunk in TEXT_TO_SYNTHESIZE:
        print(f'[发送文本]: {text_chunk}')
        qwen_tts_realtime.append_text(text_chunk)
        time.sleep(0.1)

    qwen_tts_realtime.finish()
    callback.wait_for_finished()

    print(f'[Metric] session_id={qwen_tts_realtime.get_session_id()}, '
          f'first_audio_delay={qwen_tts_realtime.get_first_audio_delay()}s')
```

## Java

需要导入Gson依赖，若是使用Maven或者Gradle，添加依赖方式如下：

## Maven

在`pom.xml`中添加如下内容：

```
<!-- https://mvnrepository.com/artifact/com.google.code.gson/gson -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.13.1</version>
</dependency>
```

## Gradle

在`build.gradle`中添加如下内容：

```
// https://mvnrepository.com/artifact/com.google.code.gson/gson
implementation("com.google.code.gson:gson:2.13.1")
```

```
import com.alibaba.dashscope.audio.qwen_tts_realtime.*;
import com.alibaba.dashscope.exception.NoApiKeyException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import javax.sound.sampled.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    // ===== 常量定义 =====
    // 声音复刻、语音合成要使用相同的模型
    private static final String TARGET_MODEL = "qwen3-tts-vc-realtime-2026-01-15";
    private static final String PREFERRED_NAME = "guanyu";
    // 用于声音复刻的本地音频文件的相对路径
    private static final String AUDIO_FILE = "voice.mp3";
    private static final String AUDIO_MIME_TYPE = "audio/mpeg";
    private static String[] textToSynthesize = {
            "对吧~我就特别喜欢这种超市",
            "尤其是过年的时候",
            "去逛超市",
            "就会觉得",
            "超级超级开心！",
            "想买好多好多的东西呢！"
    };

    // 生成 data URI
    public static String toDataUrl(String filePath) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(filePath));
        String encoded = Base64.getEncoder().encodeToString(bytes);
        return "data:" + AUDIO_MIME_TYPE + ";base64," + encoded;
    }

    // 调用 API 创建 voice
    public static String createVoice() throws Exception {
        // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
        // 若没有配置环境变量，请用百炼API Key将下行替换为：String apiKey = "sk-xxx"
        String apiKey = System.getenv("DASHSCOPE_API_KEY");

        String jsonPayload =
                "{"
                        + "\"model\": \"qwen-voice-enrollment\"," // 不要修改该值
                        + "\"input\": {"
                        +     "\"action\": \"create\","
                        +     "\"target_model\": \"" + TARGET_MODEL + "\","
                        +     "\"preferred_name\": \"" + PREFERRED_NAME + "\","
                        +     "\"audio\": {"
                        +         "\"data\": \"" + toDataUrl(AUDIO_FILE) + "\""
                        +     "}"
                        + "}"
                        + "}";

        HttpURLConnection con = (HttpURLConnection) new URL("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization").openConnection();
        con.setRequestMethod("POST");
        con.setRequestProperty("Authorization", "Bearer " + apiKey);
        con.setRequestProperty("Content-Type", "application/json");
        con.setDoOutput(true);

        try (OutputStream os = con.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        int status = con.getResponseCode();
        System.out.println("HTTP 状态码: " + status);

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(status >= 200 && status < 300 ? con.getInputStream() : con.getErrorStream(),
                        StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            System.out.println("返回内容: " + response);

            if (status == 200) {
                JsonObject jsonObj = new Gson().fromJson(response.toString(), JsonObject.class);
                return jsonObj.getAsJsonObject("output").get("voice").getAsString();
            }
            throw new IOException("创建语音失败: " + status + " - " + response);
        }
    }

    // 实时PCM音频播放器类
    public static class RealtimePcmPlayer {
        private int sampleRate;
        private SourceDataLine line;
        private AudioFormat audioFormat;
        private Thread decoderThread;
        private Thread playerThread;
        private AtomicBoolean stopped = new AtomicBoolean(false);
        private Queue<String> b64AudioBuffer = new ConcurrentLinkedQueue<>();
        private Queue<byte[]> RawAudioBuffer = new ConcurrentLinkedQueue<>();

        // 构造函数初始化音频格式和音频线路
        public RealtimePcmPlayer(int sampleRate) throws LineUnavailableException {
            this.sampleRate = sampleRate;
            this.audioFormat = new AudioFormat(this.sampleRate, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(audioFormat);
            line.start();
            decoderThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        String b64Audio = b64AudioBuffer.poll();
                        if (b64Audio != null) {
                            byte[] rawAudio = Base64.getDecoder().decode(b64Audio);
                            RawAudioBuffer.add(rawAudio);
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            playerThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    while (!stopped.get()) {
                        byte[] rawAudio = RawAudioBuffer.poll();
                        if (rawAudio != null) {
                            try {
                                playChunk(rawAudio);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }
                }
            });
            decoderThread.start();
            playerThread.start();
        }

        // 播放一个音频块并阻塞直到播放完成
        private void playChunk(byte[] chunk) throws IOException, InterruptedException {
            if (chunk == null || chunk.length == 0) return;

            int bytesWritten = 0;
            while (bytesWritten < chunk.length) {
                bytesWritten += line.write(chunk, bytesWritten, chunk.length - bytesWritten);
            }
            int audioLength = chunk.length / (this.sampleRate*2/1000);
            // 等待缓冲区中的音频播放完成
            Thread.sleep(audioLength - 10);
        }

        public void write(String b64Audio) {
            b64AudioBuffer.add(b64Audio);
        }

        public void cancel() {
            b64AudioBuffer.clear();
            RawAudioBuffer.clear();
        }

        public void waitForComplete() throws InterruptedException {
            while (!b64AudioBuffer.isEmpty() || !RawAudioBuffer.isEmpty()) {
                Thread.sleep(100);
            }
            line.drain();
        }

        public void shutdown() throws InterruptedException {
            stopped.set(true);
            decoderThread.join();
            playerThread.join();
            if (line != null && line.isRunning()) {
                line.drain();
                line.close();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                .model(TARGET_MODEL)
                // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
                .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                // 若没有配置环境变量，请用百炼API Key将下行替换为：.apikey("sk-xxx")
                .apikey(System.getenv("DASHSCOPE_API_KEY"))
                .build();
        AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>(new CountDownLatch(1));
        final AtomicReference<QwenTtsRealtime> qwenTtsRef = new AtomicReference<>(null);

        // 创建实时音频播放器实例
        RealtimePcmPlayer audioPlayer = new RealtimePcmPlayer(24000);

        QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
            @Override
            public void onOpen() {
                // 连接建立时的处理
            }
            @Override
            public void onEvent(JsonObject message) {
                String type = message.get("type").getAsString();
                switch(type) {
                    case "session.created":
                        // 会话创建时的处理
                        break;
                    case "response.audio.delta":
                        String recvAudioB64 = message.get("delta").getAsString();
                        // 实时播放音频
                        audioPlayer.write(recvAudioB64);
                        break;
                    case "response.done":
                        // 响应完成时的处理
                        break;
                    case "session.finished":
                        // 会话结束时的处理
                        completeLatch.get().countDown();
                    default:
                        break;
                }
            }
            @Override
            public void onClose(int code, String reason) {
                // 连接关闭时的处理
            }
        });
        qwenTtsRef.set(qwenTtsRealtime);
        try {
            qwenTtsRealtime.connect();
        } catch (NoApiKeyException e) {
            throw new RuntimeException(e);
        }
        QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                .voice(createVoice()) // 将voice参数替换为复刻生成的专属音色
                .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                .mode("server_commit")
                .build();
        qwenTtsRealtime.updateSession(config);
        for (String text:textToSynthesize) {
            qwenTtsRealtime.appendText(text);
            Thread.sleep(100);
        }
        qwenTtsRealtime.finish();
        completeLatch.get().await();

        // 等待音频播放完成并关闭播放器
        audioPlayer.waitForComplete();
        audioPlayer.shutdown();
        System.exit(0);
    }
}
```

## 使用声音设计音色进行语音合成

使用声音设计功能时，服务会返回预览音频数据。建议先试听该预览音频，确认效果符合预期后再用于语音合成，降低调用成本。

1.  生成专属音色并试听效果，若对效果满意，进行下一步；否则重新生成。
    
    ### Python
    
    ```
    import requests
    import base64
    import os
    
    def create_voice_and_play():
        # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
        # 若没有配置环境变量，请用百炼API Key将下行替换为：api_key = "sk-xxx"
        api_key = os.getenv("DASHSCOPE_API_KEY")
        
        if not api_key:
            print("错误: 未找到DASHSCOPE_API_KEY环境变量，请先设置API Key")
            return None, None, None
        
        # 准备请求数据
        headers = {
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json"
        }
        
        data = {
            "model": "qwen-voice-design",
            "input": {
                "action": "create",
                "target_model": "qwen3-tts-vd-realtime-2026-01-15",
                "voice_prompt": "沉稳的中年男性播音员，音色低沉浑厚，富有磁性，语速平稳，吐字清晰，适合用于新闻播报或纪录片解说。",
                "preview_text": "各位听众朋友，大家好，欢迎收听晚间新闻。",
                "preferred_name": "announcer",
                "language": "zh"
            },
            "parameters": {
                "sample_rate": 24000,
                "response_format": "wav"
            }
        }
        
        # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1/services/audio/tts/customization
        url = "https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization"
        
        try:
            # 发送请求
            response = requests.post(
                url,
                headers=headers,
                json=data,
                timeout=60  # 添加超时设置
            )
            
            if response.status_code == 200:
                result = response.json()
                
                # 获取音色名称
                voice_name = result["output"]["voice"]
                print(f"音色名称: {voice_name}")
                
                # 获取预览音频数据
                base64_audio = result["output"]["preview_audio"]["data"]
                
                # 解码Base64音频数据
                audio_bytes = base64.b64decode(base64_audio)
                
                # 保存音频文件到本地
                filename = f"{voice_name}_preview.wav"
                
                # 将音频数据写入本地文件
                with open(filename, 'wb') as f:
                    f.write(audio_bytes)
                
                print(f"音频已保存到本地文件: {filename}")
                print(f"文件路径: {os.path.abspath(filename)}")
                
                return voice_name, audio_bytes, filename
            else:
                print(f"请求失败，状态码: {response.status_code}")
                print(f"响应内容: {response.text}")
                return None, None, None
                
        except requests.exceptions.RequestException as e:
            print(f"网络请求发生错误: {e}")
            return None, None, None
        except KeyError as e:
            print(f"响应数据格式错误，缺少必要的字段: {e}")
            print(f"响应内容: {response.text if 'response' in locals() else 'No response'}")
            return None, None, None
        except Exception as e:
            print(f"发生未知错误: {e}")
            return None, None, None
    
    if __name__ == "__main__":
        print("开始创建语音...")
        voice_name, audio_data, saved_filename = create_voice_and_play()
        
        if voice_name:
            print(f"\n成功创建音色 '{voice_name}'")
            print(f"音频文件已保存: '{saved_filename}'")
            print(f"文件大小: {os.path.getsize(saved_filename)} 字节")
        else:
            print("\n音色创建失败")
    ```
    
    ### Java
    
    需要导入Gson依赖，若是使用Maven或者Gradle，添加依赖方式如下：
    
    #### Maven
    
    在`pom.xml`中添加如下内容：
    
    ```
    <!-- https://mvnrepository.com/artifact/com.google.code.gson/gson -->
    <dependency>
        <groupId>com.google.code.gson</groupId>
        <artifactId>gson</artifactId>
        <version>2.13.1</version>
    </dependency>
    ```
    
    #### Gradle
    
    在`build.gradle`中添加如下内容：
    
    ```
    // https://mvnrepository.com/artifact/com.google.code.gson/gson
    implementation("com.google.code.gson:gson:2.13.1")
    ```
    
    ```
    import com.google.gson.JsonObject;
    import com.google.gson.JsonParser;
    import java.io.*;
    import java.net.HttpURLConnection;
    import java.net.URL;
    import java.util.Base64;
    
    public class Main {
        public static void main(String[] args) {
            Main example = new Main();
            example.createVoice();
        }
    
        public void createVoice() {
            // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
            // 若没有配置环境变量，请用百炼API Key将下行替换为：String apiKey = "sk-xxx"
            String apiKey = System.getenv("DASHSCOPE_API_KEY");
    
            // 创建JSON请求体字符串
            String jsonBody = "{\n" +
                    "    \"model\": \"qwen-voice-design\",\n" +
                    "    \"input\": {\n" +
                    "        \"action\": \"create\",\n" +
                    "        \"target_model\": \"qwen3-tts-vd-realtime-2026-01-15\",\n" +
                    "        \"voice_prompt\": \"沉稳的中年男性播音员，音色低沉浑厚，富有磁性，语速平稳，吐字清晰，适合用于新闻播报或纪录片解说。\",\n" +
                    "        \"preview_text\": \"各位听众朋友，大家好，欢迎收听晚间新闻。\",\n" +
                    "        \"preferred_name\": \"announcer\",\n" +
                    "        \"language\": \"zh\"\n" +
                    "    },\n" +
                    "    \"parameters\": {\n" +
                    "        \"sample_rate\": 24000,\n" +
                    "        \"response_format\": \"wav\"\n" +
                    "    }\n" +
                    "}";
    
            HttpURLConnection connection = null;
            try {
                // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：https://dashscope-intl.aliyuncs.com/api/v1/services/audio/tts/customization
                URL url = new URL("https://dashscope.aliyuncs.com/api/v1/services/audio/tts/customization");
                connection = (HttpURLConnection) url.openConnection();
    
                // 设置请求方法和头部
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                connection.setDoInput(true);
    
                // 发送请求体
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonBody.getBytes("UTF-8");
                    os.write(input, 0, input.length);
                    os.flush();
                }
    
                // 获取响应
                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    // 读取响应内容
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getInputStream(), "UTF-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            response.append(responseLine.trim());
                        }
                    }
    
                    // 解析JSON响应
                    JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonObject outputObj = jsonResponse.getAsJsonObject("output");
                    JsonObject previewAudioObj = outputObj.getAsJsonObject("preview_audio");
    
                    // 获取音色名称
                    String voiceName = outputObj.get("voice").getAsString();
                    System.out.println("音色名称: " + voiceName);
    
                    // 获取Base64编码的音频数据
                    String base64Audio = previewAudioObj.get("data").getAsString();
    
                    // 解码Base64音频数据
                    byte[] audioBytes = Base64.getDecoder().decode(base64Audio);
    
                    // 保存音频到本地文件
                    String filename = voiceName + "_preview.wav";
                    saveAudioToFile(audioBytes, filename);
    
                    System.out.println("音频已保存到本地文件: " + filename);
    
                } else {
                    // 读取错误响应
                    StringBuilder errorResponse = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(connection.getErrorStream(), "UTF-8"))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            errorResponse.append(responseLine.trim());
                        }
                    }
    
                    System.out.println("请求失败，状态码: " + responseCode);
                    System.out.println("错误响应: " + errorResponse.toString());
                }
    
            } catch (Exception e) {
                System.err.println("请求发生错误: " + e.getMessage());
                e.printStackTrace();
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    
        private void saveAudioToFile(byte[] audioBytes, String filename) {
            try {
                File file = new File(filename);
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(audioBytes);
                }
                System.out.println("音频已保存到: " + file.getAbsolutePath());
            } catch (IOException e) {
                System.err.println("保存音频文件时发生错误: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    ```
    
2.  使用上一步生成的专属音色进行语音合成。
    
    这里参考了使用系统音色进行语音合成DashScope SDK的“server commit模式”示例代码，将`voice`参数替换为声音设计生成的专属音色进行语音合成。
    
    **关键原则**：声音设计时使用的模型 (`target_model`) 必须与后续进行语音合成时使用的模型 (`model`) 保持一致，否则会导致合成失败。
    
    ### Python
    
    ```
    # coding=utf-8
    # Installation instructions for pyaudio:
    # APPLE Mac OS X
    #   brew install portaudio
    #   pip install pyaudio
    # Debian/Ubuntu
    #   sudo apt-get install python-pyaudio python3-pyaudio
    #   or
    #   pip install pyaudio
    # CentOS
    #   sudo yum install -y portaudio portaudio-devel && pip install pyaudio
    # Microsoft Windows
    #   python -m pip install pyaudio
    
    import pyaudio
    import os
    import base64
    import threading
    import time
    import dashscope  # DashScope Python SDK 版本需要不低于1.23.9
    from dashscope.audio.qwen_tts_realtime import QwenTtsRealtime, QwenTtsRealtimeCallback, AudioFormat
    
    # ======= 常量配置 =======
    TEXT_TO_SYNTHESIZE = [
        '对吧~我就特别喜欢这种超市，',
        '尤其是过年的时候',
        '去逛超市',
        '就会觉得',
        '超级超级开心！',
        '想买好多好多的东西呢！'
    ]
    
    def init_dashscope_api_key():
        """
        初始化 dashscope SDK 的 API key
        """
        # 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
        # 若没有配置环境变量，请用百炼API Key将下行替换为：dashscope.api_key = "sk-xxx"
        dashscope.api_key = os.getenv("DASHSCOPE_API_KEY")
    
    # ======= 回调类 =======
    class MyCallback(QwenTtsRealtimeCallback):
        """
        自定义 TTS 流式回调
        """
        def __init__(self):
            self.complete_event = threading.Event()
            self._player = pyaudio.PyAudio()
            self._stream = self._player.open(
                format=pyaudio.paInt16, channels=1, rate=24000, output=True
            )
    
        def on_open(self) -> None:
            print('[TTS] 连接已建立')
    
        def on_close(self, close_status_code, close_msg) -> None:
            self._stream.stop_stream()
            self._stream.close()
            self._player.terminate()
            print(f'[TTS] 连接关闭 code={close_status_code}, msg={close_msg}')
    
        def on_event(self, response: dict) -> None:
            try:
                event_type = response.get('type', '')
                if event_type == 'session.created':
                    print(f'[TTS] 会话开始: {response["session"]["id"]}')
                elif event_type == 'response.audio.delta':
                    audio_data = base64.b64decode(response['delta'])
                    self._stream.write(audio_data)
                elif event_type == 'response.done':
                    print(f'[TTS] 响应完成, Response ID: {qwen_tts_realtime.get_last_response_id()}')
                elif event_type == 'session.finished':
                    print('[TTS] 会话结束')
                    self.complete_event.set()
            except Exception as e:
                print(f'[Error] 处理回调事件异常: {e}')
    
        def wait_for_finished(self):
            self.complete_event.wait()
    
    # ======= 主执行逻辑 =======
    if __name__ == '__main__':
        init_dashscope_api_key()
        print('[系统] 初始化 Qwen TTS Realtime ...')
    
        callback = MyCallback()
        qwen_tts_realtime = QwenTtsRealtime(
            # 声音设计、语音合成要使用相同的模型
            model="qwen3-tts-vd-realtime-2026-01-15",
            callback=callback,
            # 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
            url='wss://dashscope.aliyuncs.com/api-ws/v1/realtime'
        )
        qwen_tts_realtime.connect()
        
        qwen_tts_realtime.update_session(
            voice="myvoice", # 将voice参数替换为声音设计生成的专属音色
            response_format=AudioFormat.PCM_24000HZ_MONO_16BIT,
            mode='server_commit'
        )
    
        for text_chunk in TEXT_TO_SYNTHESIZE:
            print(f'[发送文本]: {text_chunk}')
            qwen_tts_realtime.append_text(text_chunk)
            time.sleep(0.1)
    
        qwen_tts_realtime.finish()
        callback.wait_for_finished()
    
        print(f'[Metric] session_id={qwen_tts_realtime.get_session_id()}, '
              f'first_audio_delay={qwen_tts_realtime.get_first_audio_delay()}s')
    ```
    
    ### Java
    
    ```
    import com.alibaba.dashscope.audio.qwen_tts_realtime.*;
    import com.alibaba.dashscope.exception.NoApiKeyException;
    import com.google.gson.JsonObject;
    
    import javax.sound.sampled.*;
    import java.io.*;
    import java.util.Base64;
    import java.util.Queue;
    import java.util.concurrent.CountDownLatch;
    import java.util.concurrent.atomic.AtomicReference;
    import java.util.concurrent.ConcurrentLinkedQueue;
    import java.util.concurrent.atomic.AtomicBoolean;
    
    public class Main {
        // ===== 常量定义 =====
        private static String[] textToSynthesize = {
                "对吧~我就特别喜欢这种超市",
                "尤其是过年的时候",
                "去逛超市",
                "就会觉得",
                "超级超级开心！",
                "想买好多好多的东西呢！"
        };
    
        // 实时音频播放器类
        public static class RealtimePcmPlayer {
            private int sampleRate;
            private SourceDataLine line;
            private AudioFormat audioFormat;
            private Thread decoderThread;
            private Thread playerThread;
            private AtomicBoolean stopped = new AtomicBoolean(false);
            private Queue<String> b64AudioBuffer = new ConcurrentLinkedQueue<>();
            private Queue<byte[]> RawAudioBuffer = new ConcurrentLinkedQueue<>();
    
            // 构造函数初始化音频格式和音频线路
            public RealtimePcmPlayer(int sampleRate) throws LineUnavailableException {
                this.sampleRate = sampleRate;
                this.audioFormat = new AudioFormat(this.sampleRate, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, audioFormat);
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(audioFormat);
                line.start();
                decoderThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!stopped.get()) {
                            String b64Audio = b64AudioBuffer.poll();
                            if (b64Audio != null) {
                                byte[] rawAudio = Base64.getDecoder().decode(b64Audio);
                                RawAudioBuffer.add(rawAudio);
                            } else {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                });
                playerThread = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        while (!stopped.get()) {
                            byte[] rawAudio = RawAudioBuffer.poll();
                            if (rawAudio != null) {
                                try {
                                    playChunk(rawAudio);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }
                    }
                });
                decoderThread.start();
                playerThread.start();
            }
    
            // 播放一个音频块并阻塞直到播放完成
            private void playChunk(byte[] chunk) throws IOException, InterruptedException {
                if (chunk == null || chunk.length == 0) return;
    
                int bytesWritten = 0;
                while (bytesWritten < chunk.length) {
                    bytesWritten += line.write(chunk, bytesWritten, chunk.length - bytesWritten);
                }
                int audioLength = chunk.length / (this.sampleRate*2/1000);
                // 等待缓冲区中的音频播放完成
                Thread.sleep(audioLength - 10);
            }
    
            public void write(String b64Audio) {
                b64AudioBuffer.add(b64Audio);
            }
    
            public void cancel() {
                b64AudioBuffer.clear();
                RawAudioBuffer.clear();
            }
    
            public void waitForComplete() throws InterruptedException {
                while (!b64AudioBuffer.isEmpty() || !RawAudioBuffer.isEmpty()) {
                    Thread.sleep(100);
                }
                line.drain();
            }
    
            public void shutdown() throws InterruptedException {
                stopped.set(true);
                decoderThread.join();
                playerThread.join();
                if (line != null && line.isRunning()) {
                    line.drain();
                    line.close();
                }
            }
        }
    
        public static void main(String[] args) throws Exception {
            QwenTtsRealtimeParam param = QwenTtsRealtimeParam.builder()
                    // 声音设计、语音合成要使用相同的模型
                    .model("qwen3-tts-vd-realtime-2026-01-15")
                    // 以下为北京地域url，若使用新加坡地域的模型，需将url替换为：wss://dashscope-intl.aliyuncs.com/api-ws/v1/realtime
                    .url("wss://dashscope.aliyuncs.com/api-ws/v1/realtime")
                    // 新加坡和北京地域的API Key不同。获取API Key：https://help.aliyun.com/zh/model-studio/get-api-key
                    // 若没有配置环境变量，请用百炼API Key将下行替换为：.apikey("sk-xxx")
                    .apikey(System.getenv("DASHSCOPE_API_KEY"))
                    .build();
            AtomicReference<CountDownLatch> completeLatch = new AtomicReference<>(new CountDownLatch(1));
            final AtomicReference<QwenTtsRealtime> qwenTtsRef = new AtomicReference<>(null);
    
            // 创建实时音频播放器实例
            RealtimePcmPlayer audioPlayer = new RealtimePcmPlayer(24000);
    
            QwenTtsRealtime qwenTtsRealtime = new QwenTtsRealtime(param, new QwenTtsRealtimeCallback() {
                @Override
                public void onOpen() {
                    // 连接建立时的处理
                }
                @Override
                public void onEvent(JsonObject message) {
                    String type = message.get("type").getAsString();
                    switch(type) {
                        case "session.created":
                            // 会话创建时的处理
                            break;
                        case "response.audio.delta":
                            String recvAudioB64 = message.get("delta").getAsString();
                            // 实时播放音频
                            audioPlayer.write(recvAudioB64);
                            break;
                        case "response.done":
                            // 响应完成时的处理
                            break;
                        case "session.finished":
                            // 会话结束时的处理
                            completeLatch.get().countDown();
                        default:
                            break;
                    }
                }
                @Override
                public void onClose(int code, String reason) {
                    // 连接关闭时的处理
                }
            });
            qwenTtsRef.set(qwenTtsRealtime);
            try {
                qwenTtsRealtime.connect();
            } catch (NoApiKeyException e) {
                throw new RuntimeException(e);
            }
            QwenTtsRealtimeConfig config = QwenTtsRealtimeConfig.builder()
                    .voice("myvoice") // 将voice参数替换为声音设计生成的专属音色
                    .responseFormat(QwenTtsRealtimeAudioFormat.PCM_24000HZ_MONO_16BIT)
                    .mode("server_commit")
                    .build();
            qwenTtsRealtime.updateSession(config);
            for (String text:textToSynthesize) {
                qwenTtsRealtime.appendText(text);
                Thread.sleep(100);
            }
            qwenTtsRealtime.finish();
            completeLatch.get().await();
    
            // 等待音频播放完成并关闭播放器
            audioPlayer.waitForComplete();
            audioPlayer.shutdown();
            System.exit(0);
        }
    }
    ```
    

更多示例代码请参见[github](https://github.com/aliyun/alibabacloud-bailian-speech-demo/tree/master/samples/conversation/omni)。

## **交互流程**

## server\_commit 模式

将`session.update`事件的`session.mode` 设为`"server_commit"`以启用该模式，服务端会智能处理文本分段和合成时机。

交互流程如下：

1.  客户端发送`session.update`事件，服务端响应`session.created`与`session.updated`事件。
    
2.  客户端发送 `input_text_buffer.append` 事件追加文本至服务端缓冲区。
    
3.  服务端智能处理文本分段和合成时机，并返回`response.created`、`response.output_item.added`、`response.content_part.added`、`response.audio.delta`事件。
    
4.  服务端响应完成后响应`response.audio.done`、`response.content_part.done`、`response.output_item.done`、`response.done`。
    
5.  服务端响应`session.finished`来结束会话。
    

| **生命周期** | **客户端事件** | **服务器事件** |
| --- | --- | --- |
| 会话初始化 | session.update > 会话配置 | session.created > 会话已创建 session.updated > 会话配置已更新 |
| 用户文本输入 | input\\_text\\_buffer.append > 添加文本到服务端 input\\_text\\_buffer.commit > 立即合成服务端缓存的文本 session.finish > 通知服务端不再有文本输入 | input\\_text\\_buffer.committed > 服务端收到提交的文本 |
| 服务器音频输出 | 无   | response.created > 服务端开始生成响应 response.output\\_item.added > 响应时有新的输出内容 response.content\\_part.added > 新的输出内容添加到assistant message response.audio.delta > 模型增量生成的音频 response.content\\_part.done > Assistant message 的文本或音频内容流式输出完成 response.output\\_item.done > Assistant message 的整个输出项流式传输完成 response.audio.done > 音频生成完成 response.done > 响应完成 |

## commit 模式

将`session.update`事件的`session.mode` 设为`"commit"`以启用该模式，客户端需主动提交文本缓冲区至服务端来获取响应。

交互流程如下：

1.  客户端发送`session.update`事件，服务端响应`session.created`与`session.updated`事件。
    
2.  客户端发送 `input_text_buffer.append` 事件追加文本至服务端缓冲区。
    
3.  客户端发送`input_text_buffer.commit`事件将缓冲区提交至服务端，并发送 `session.finish`事件表示后续无文本输入。
    
4.  服务端响应`response.created`，开始生成响应。
    
5.  服务端响应`response.output_item.added`、`response.content_part.added`、`response.audio.delta`事件。
    
6.  服务端响应完成后返回`response.audio.done`、`response.content_part.done`、`response.output_item.done`、`response.done`。
    
7.  服务端响应`session.finished`来结束会话。
    

| **生命周期** | **客户端事件** | **服务器事件** |
| --- | --- | --- |
| 会话初始化 | session.update > 会话配置 | session.created > 会话已创建 session.updated > 会话配置已更新 |
| 用户文本输入 | input\\_text\\_buffer.append > 添加文本到缓冲区 input\\_text\\_buffer.commit > 提交缓冲区到服务端 input\\_text\\_buffer.clear > 清除缓冲区 | input\\_text\\_buffer.committed > 服务器收到提交的文本 |
| 服务器音频输出 | 无   | response.created > 服务端开始生成响应 response.output\\_item.added > 响应时有新的输出内容 response.content\\_part.added > 新的输出内容添加到assistant message response.audio.delta > 模型增量生成的音频 response.content\\_part.done > Assistant message 的文本或音频内容流式输出完成 response.output\\_item.done > Assistant message 的整个输出项流式传输完成 response.audio.done > 音频生成完成 response.done > 响应完成 |

## **指令控制**

指令控制是一项高级语音合成功能，通过自然语言描述的方式精确控制语音的表达效果。您可以使用简单的文字描述，让合成语音呈现出特定的音调、语速、情感、音色特点，无需调整复杂的音频参数。

**支持的模型**：仅支持千问3-TTS-Instruct-Flash-Realtime系列模型。

**使用方式**：通过`instructions`参数指定指令内容，例如“语速较快，带有明显的上扬语调，适合介绍时尚产品”。

**支持语言**：描述文本仅支持中文和英文。

**长度限制**：长度不得超过 1600 Token。

**适用场景**：

-   有声书和广播剧配音
    
-   广告和宣传片配音
    
-   游戏角色和动画配音
    
-   情感化的智能语音助手
    
-   纪录片和新闻播报
    

**如何编写高质量的声音描述：**

-   核心原则：
    
    1.  具体而非模糊：使用能够描绘具体声音特质的词语，如“低沉”、“清脆”、“语速偏快”。避免使用“好听”、“普通”等主观且缺乏信息量的词汇。
        
    2.  多维而非单一：优秀的描述通常结合多个维度（如下文所述的音调、语速、情感等）。单一维度的描述（如仅“高音”）过于宽泛，难以生成特色鲜明的效果。
        
    3.  客观而非主观：专注于声音本身的物理和感知特征，而不是个人的喜好。例如，用“音调偏高，带有活力”代替“我最喜欢的声音”。
        
    4.  原创而非模仿：请描述声音的特质，而不是要求模仿特定人物（如名人、演员）。此类请求涉及版权风险且模型不支持直接模仿。
        
    5.  简洁而非冗余：确保每个词都有其意义。避免重复使用同义词或无意义的强调词（如“非常非常棒的声音”）。
        
-   描述维度参考：可以组合多个维度，创造更丰富的表达效果。
    
    | **维度** | **描述示例** |
    | --- | --- |
    | 音调  | 高音、中音、低音、偏高、偏低 |
    | 语速  | 快速、中速、缓慢、偏快、偏慢 |
    | 情感  | 开朗、沉稳、温柔、严肃、活泼、冷静、治愈 |
    | 特点  | 有磁性、清脆、沙哑、圆润、甜美、浑厚、有力 |
    | 用途  | 新闻播报、广告配音、有声书、动画角色、语音助手、纪录片解说 |
    
-   示例：
    
    -   标准播音风格：吐字清晰精准，字正腔圆
        
    -   情绪递进效果：音量由正常对话迅速增强至高喊，性格直率，情绪易激动且外露
        
    -   特殊情感状态：哭腔导致发音略微含糊，略显沙哑，带有明显哭腔的紧张感
        
    -   广告配音风格：音调偏高，语速中等，充满活力和感染力，适合广告配音
        
    -   温柔治愈风格：语速偏慢，音调温柔甜美，语气治愈温暖，像贴心朋友般关怀
        

## **API参考**

[实时语音合成-千问API参考](https://help.aliyun.com/zh/model-studio/qwen-tts-realtime-api-reference/)

[声音复刻-API参考](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-cloning)

[声音设计-API参考](https://help.aliyun.com/zh/model-studio/qwen-tts-voice-design)

## **模型功能特性对比**

| **功能/特性** | **千问3-TTS-Instruct-Flash-Realtime** | **千问3-TTS-VD-Realtime** | **千问3-TTS-VC-Realtime** | **千问3-TTS-Flash-Realtime** | **千问-TTS-Realtime** |
| --- | --- | --- | --- | --- | --- |
| **支持语言** | 中文（普通话）、英文、西班牙语、俄语、意大利语、法语、韩语、日语、德语、葡萄牙语 | 中文（普通话）、英文、西班牙语、俄语、意大利语、法语、韩语、日语、德语、葡萄牙语 |   | 中文（普通话、北京、上海、四川、南京、陕西、闽南、天津、粤语，因[音色](#422789c49bqqx)而异）、英文、西班牙语、俄语、意大利语、法语、韩语、日语、德语、葡萄牙语 | 中文、英文 |
| **音频格式** | pcm、wav、mp3、opus |   |   |   | pcm |
| **音频采样率** | 8kHz、16kHz、24kHz、48kHz |   |   |   | 24kHz |
| **声音复刻** | 不支持 |   | 支持  | 不支持 |   |
| **声音设计** | 不支持 | 支持  | 不支持 |   |   |
| **SSML** | 不支持 |   |   |   |   |
| **LaTeX** | 不支持 |   |   |   |   |
| **音量调节** | 支持  |   |   |   | 不支持 |
| **语速调节** | 支持  |   |   |   | 不支持 |
| **语调（音高）调节** | 支持  |   |   |   | 不支持 |
| **码率调节** | 支持  |   |   |   | 不支持 |
| **时间戳** | 不支持 |   |   |   |   |
| **指令控制（Instruct）** | 支持  | 不支持 |   |   |   |
| **流式输入** | 支持  |   |   |   |   |
| **流式输出** | 支持  |   |   |   |   |
| **限流** |     | 每分钟调用次数（RPM）：180 |   | qwen3-tts-flash-realtime、qwen3-tts-flash-realtime-2025-11-27每分钟调用次数（RPM）：180 qwen3-tts-flash-realtime-2025-09-18每分钟调用次数（RPM）：10 | 每分钟调用次数（RPM）：10 每分钟消耗Token数（TPM）：100,000 |
| **接入方式** | Java/Python SDK、WebSocket API |   |   |   |   |
| **价格** | 中国内地：1元/万字符 国际：1元/万字符 | 中国内地：1元/万字符 国际：0.954101元/万字符 | 中国内地：1元/万字符 国际：0.954101元/万字符 |   | 中国内地： - 输入成本：0.0024元/千Token - 输出成本：0.012元/千Token |
