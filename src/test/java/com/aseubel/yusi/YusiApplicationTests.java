package com.aseubel.yusi;

import com.aseubel.yusi.common.disruptor.DisruptorProducer;
import com.aseubel.yusi.common.disruptor.Element;
import com.aseubel.yusi.common.disruptor.EventType;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.ai.Assistant;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@SpringBootTest
class YusiApplicationTests {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Resource
    private DisruptorProducer disruptorProducer;

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private MilvusEmbeddingStore milvusEmbeddingStore;

    @Resource
    private Assistant diaryRAGAssistant;

    @Test
    void jpaTest() {
        User user = new User();
        user.setUserName("test");
        user.setPassword("password");
        user.generateUserId();
        User savedUser = userRepository.save(user);
        System.out.println(userRepository.findAll());
        System.out.println(userRepository.findById(user.getId()).orElseThrow());
        userRepository.delete(savedUser);
    }

    @Test
    void jpaSortTest() {
        Sort.TypedSort<Diary> sortType = Sort.sort(Diary.class);
        Sort sort = sortType.by(Diary::getEntryDate).descending().and(sortType.by(Diary::getId));
        Page<Diary> all = diaryRepository.findAll(PageRequest.of(1, 2 ,sort));
        System.out.println("total pages: " + all.getTotalPages());
        System.out.println("total elements: " + all.getSize());
        System.out.println("current page: " + all.getNumber());
        System.out.println("total elements: " + all.getTotalElements());
        System.out.println("content: " + all.getContent());
    }

    @Test
    void disruptorTest() {
        Diary diary = new Diary();
        diary.setUserId("0001");
        diary.setTitle("去玩了！");
        diary.setContent("""
                今天是个超棒的日子！我和朋友一起出去玩啦！早上我早早地起床，因为我知道我们要去游乐园！我超级激动，一颗心扑通扑通跳个不停，像小兔子一样乱撞。我赶紧洗漱完，然后跑到朋友家楼下等她，结果她已经在那里啦，还给我带了我最喜欢吃的棉花糖！耶！
                我们去了游乐园之后，先玩了旋转木马，我选了一个超级可爱的小马，它有着闪闪发光的鬃毛，跑起来的时候感觉像在飞一样！朋友就坐在我旁边，她选了一个小鹿，我们边笑边喊，超级开心！后来我们又去玩了摩天轮，从上面可以看到好多好多的风景，整个世界都变得好小呀！朋友说看到远方的云朵像棉花糖一样，我忍不住就幻想自己要是能飞上去咬一口那该多好。
                中午我们在游乐园附近的餐厅吃了饭，朋友帮我点了一份超好吃的薯条，因为我知道我喜欢吃。吃着吃着，我不小心把番茄酱抹到了鼻子上，朋友看到后哈哈大笑，还帮我擦干净了呢。我觉得有点小尴尬，但又很开心，因为朋友的笑声特别好听。
                下午我们又去玩了很多项目，朋友一直陪着我，帮我拿东西，还在我害怕的时候握住我的手。她总是那么照顾我，让我觉得好温暖呀。我还偷偷为她准备了一个小礼物，是一个我亲手做的小挂件，虽然有点歪歪扭扭的，但那是我的心意呀。她收到后特别开心，说要一直挂着呢。
                回家的时候，我们还一起分享了最后一根棉花糖，朋友说今天的游玩是“最棒的一天”，我也这么觉得呢！和朋友在一起的时候，总是充满了快乐和温暖。我希望我们能一直这样，永远是好朋友呀。
                晚安咯！今天真的太开心啦！
                Doro
                （今天的宝物：和朋友一起分享的棉花糖包装纸、摩天轮的纪念票根、朋友送的小礼物）""");
        diary.setEntryDate(LocalDate.of(2023, 11, 5));
        diary.generateId();
        disruptorProducer.publish(diary, EventType.DIARY_WRITE);
    }

    @Test
    void ragTest() {
        String userId1 = "0001";
        String userId2 = "0002";
        String query = "我有和朋友去玩吗？";
        System.out.println("user0001: " + diaryRAGAssistant.chat(userId1, query));
        // user0001: 是的，你确实和朋友一起去游乐园玩了！今天真是个超棒的日子，你们一起享受了旋转木马、摩天轮等各种游乐设施，还分享了美食，互相支持和照顾，让今天充满了快乐和温暖。听起来你们度过了美好的时光！
        System.out.println("user0002: " + diaryRAGAssistant.chat(userId2, query));
        // user0002: 当然可以！和朋友一起去玩是很好的放松方式。你们可以选择去商场、电影院、游乐园，或者户外活动，比如爬山、骑自行车等等。你们有什么具体的计划吗？
    }

    @Test
    void embeddingSearchTest() {
        Diary diary = new Diary();
        diary.setUserId("0001");
        diary.setTitle("去玩了！");
        diary.setContent("""
                今天是个超棒的日子！我和朋友一起出去玩啦！早上我早早地起床，因为我知道我们要去游乐园！我超级激动，一颗心扑通扑通跳个不停，像小兔子一样乱撞。我赶紧洗漱完，然后跑到朋友家楼下等她，结果她已经在那里啦，还给我带了我最喜欢吃的棉花糖！耶！
                我们去了游乐园之后，先玩了旋转木马，我选了一个超级可爱的小马，它有着闪闪发光的鬃毛，跑起来的时候感觉像在飞一样！朋友就坐在我旁边，她选了一个小鹿，我们边笑边喊，超级开心！后来我们又去玩了摩天轮，从上面可以看到好多好多的风景，整个世界都变得好小呀！朋友说看到远方的云朵像棉花糖一样，我忍不住就幻想自己要是能飞上去咬一口那该多好。
                中午我们在游乐园附近的餐厅吃了饭，朋友帮我点了一份超好吃的薯条，因为我知道我喜欢吃。吃着吃着，我不小心把番茄酱抹到了鼻子上，朋友看到后哈哈大笑，还帮我擦干净了呢。我觉得有点小尴尬，但又很开心，因为朋友的笑声特别好听。
                下午我们又去玩了很多项目，朋友一直陪着我，帮我拿东西，还在我害怕的时候握住我的手。她总是那么照顾我，让我觉得好温暖呀。我还偷偷为她准备了一个小礼物，是一个我亲手做的小挂件，虽然有点歪歪扭扭的，但那是我的心意呀。她收到后特别开心，说要一直挂着呢。
                回家的时候，我们还一起分享了最后一根棉花糖，朋友说今天的游玩是“最棒的一天”，我也这么觉得呢！和朋友在一起的时候，总是充满了快乐和温暖。我希望我们能一直这样，永远是好朋友呀。
                晚安咯！今天真的太开心啦！
                Doro
                （今天的宝物：和朋友一起分享的棉花糖包装纸、摩天轮的纪念票根、朋友送的小礼物）""");
        diary.setEntryDate(LocalDate.of(2023, 11, 5));
        diary.generateId();

        // 转换为文本段
        List<TextSegment> diaryTextSegments = Collections.singletonList(TextSegment.from(diary.getContent(), Metadata.metadata("userId", diary.getUserId())));
        // 移除旧的embedding
        milvusEmbeddingStore.remove(diary.getDiaryId());
        // 转换文本段为Embedding
        List<Embedding> embeddings = embeddingModel.embedAll(diaryTextSegments).content();
        milvusEmbeddingStore.addAll(Collections.singletonList(diary.getDiaryId()), embeddings, diaryTextSegments);
        // 生成查询向量
        Response<Embedding> searchEmbeddings = embeddingModel.embed("今天是个超棒的日子！我和朋友一起出去玩啦！");
        EmbeddingSearchRequest build = EmbeddingSearchRequest.builder()
                .queryEmbedding(searchEmbeddings.content())
                .build();
        EmbeddingSearchResult<TextSegment> results = milvusEmbeddingStore.search(build);
        for (EmbeddingMatch<TextSegment> result : results.matches()) {
            System.out.println(result.embedded().text() + " 分数为：" + result.score());
        }
    }

    @Test
    void embeddingCleanTest() {
        milvusEmbeddingStore.removeAll();
    }
}
