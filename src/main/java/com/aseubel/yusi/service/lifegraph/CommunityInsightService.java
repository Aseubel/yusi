package com.aseubel.yusi.service.lifegraph;

import com.aseubel.yusi.service.lifegraph.dto.CommunityInsight;

import java.util.List;

public interface CommunityInsightService {

    List<CommunityInsight> detectCommunities(String userId);

    CommunityInsight getCommunityDetail(String userId, String communityId);
}
