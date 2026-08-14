package com.aseubel.yusi.service.cognition.impl;

import com.aseubel.yusi.common.constant.PromptKey;
import com.aseubel.yusi.service.ai.prompt.PromptManager;
import com.aseubel.yusi.service.ai.prompt.PromptSnapshot;
import com.aseubel.yusi.service.oss.OssService;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LangChainImageUnderstandingServiceTest {

    @Test
    void sendsTheManagedImageUnderstandingPrompt() {
        ChatModel chatModel = mock(ChatModel.class);
        OssService ossService = mock(OssService.class);
        PromptManager promptManager = mock(PromptManager.class);
        when(promptManager.getSnapshot(PromptKey.IMAGE_UNDERSTANDING))
                .thenReturn(new PromptSnapshot("image-understanding", "test", "zh-CN", "managed image prompt"));
        when(ossService.generateOwnedUrl("diary/image.png", "user-1"))
                .thenReturn("https://example.com/diary/image.png");
        when(chatModel.chat(any(UserMessage.class)))
                .thenReturn(ChatResponse.builder().aiMessage(AiMessage.from("image description")).build());

        LangChainImageUnderstandingService service =
                new LangChainImageUnderstandingService(chatModel, ossService, promptManager);

        assertThat(service.describe("user-1", List.of("diary/image.png")))
                .isEqualTo("image description");

        ArgumentCaptor<UserMessage> request = ArgumentCaptor.forClass(UserMessage.class);
        verify(chatModel).chat(request.capture());
        List<Content> contents = request.getValue().contents();
        assertThat(contents).hasSize(2);
        assertThat(contents.getFirst()).isInstanceOf(TextContent.class);
        assertThat(((TextContent) contents.getFirst()).text()).isEqualTo("managed image prompt");
        assertThat(contents.get(1)).isInstanceOf(ImageContent.class);
        verify(promptManager).getSnapshot(PromptKey.IMAGE_UNDERSTANDING);
    }
}
