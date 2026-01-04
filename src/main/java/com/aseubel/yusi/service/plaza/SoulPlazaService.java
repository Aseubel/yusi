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
}
