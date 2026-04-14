package com.aseubel.yusi.service.match;

import com.aseubel.yusi.pojo.entity.MatchProfile;

public interface MatchProfileAssembler {

    MatchProfile refreshProfile(String userId);

    MatchProfile ensureProfile(String userId);
}
