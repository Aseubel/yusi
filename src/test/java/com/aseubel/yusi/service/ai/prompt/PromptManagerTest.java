package com.aseubel.yusi.service.ai.prompt;

import com.aseubel.yusi.common.constant.PromptDefaults;
import com.aseubel.yusi.pojo.entity.PromptTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromptManagerTest {

    @Mock
    private PromptService promptService;

    @Test
    void snapshotUsesActiveDatabaseTemplateVersionAndLocale() {
        PromptTemplate template = PromptTemplate.builder()
                .name("chat")
                .template("database prompt")
                .version("v7")
                .locale("zh-CN")
                .build();
        when(promptService.getPromptTemplate("chat", PromptDefaults.LOCALE)).thenReturn(template);

        PromptManager manager = new PromptManager(promptService);
        manager.loadPrompt("chat");

        PromptSnapshot snapshot = manager.getSnapshot("chat");
        assertEquals("chat", snapshot.key());
        assertEquals("v7", snapshot.version());
        assertEquals("zh-CN", snapshot.locale());
        assertEquals("database prompt", snapshot.template());
        assertEquals("database prompt", manager.getPrompt("chat"));
    }

    @Test
    void fallbackSnapshotHasDefaultVersionWhenDatabaseTemplateIsMissing() {
        when(promptService.getPromptTemplate("chat", PromptDefaults.LOCALE)).thenReturn(null);

        PromptManager manager = new PromptManager(promptService);
        manager.loadPrompt("chat");

        assertEquals(PromptDefaults.VERSION, manager.getSnapshot("chat").version());
        assertEquals(manager.getSnapshot("chat").template(), manager.getPrompt("chat"));
    }

    @Test
    void missingVersionIsNotReplacedWithPromptBodyOrSyntheticHash() {
        PromptTemplate template = PromptTemplate.builder()
                .name("chat")
                .template("database prompt")
                .version(null)
                .locale("zh-CN")
                .build();
        when(promptService.getPromptTemplate("chat", PromptDefaults.LOCALE)).thenReturn(template);

        PromptManager manager = new PromptManager(promptService);
        manager.loadPrompt("chat");

        assertNull(manager.getSnapshot("chat").version());
    }
}
