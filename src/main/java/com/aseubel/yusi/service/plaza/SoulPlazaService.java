package com.aseubel.yusi.service.plaza;

import com.aseubel.yusi.pojo.contant.CardType;
import com.aseubel.yusi.pojo.contant.ResonanceType;
import com.aseubel.yusi.pojo.entity.SoulCard;
import com.aseubel.yusi.pojo.entity.SoulResonance;
import org.springframework.data.domain.Page;

public interface SoulPlazaService {

    SoulCard submitToPlaza(String userId, String content, String originId, CardType type);

    Page<SoulCard> getFeed(String userId, int page, int size, String emotion);

    SoulResonance resonate(String userId, Long cardId, ResonanceType type);

    /**
     * 获取用户自己的卡片列表
     */
    Page<SoulCard> getMyCards(String userId, int page, int size);

    /**
     * 更新卡片内容（重新分析情绪）
     */
    SoulCard updateCard(String userId, Long cardId, String content);

    /**
     * 删除卡片
     */
    void deleteCard(String userId, Long cardId);
}
