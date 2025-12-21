package com.aseubel.yusi.service.match.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import com.aseubel.yusi.pojo.entity.Diary;
import com.aseubel.yusi.pojo.entity.SoulMatch;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.DiaryRepository;
import com.aseubel.yusi.repository.SoulMatchRepository;
import com.aseubel.yusi.service.diary.Assistant;
import com.aseubel.yusi.service.match.MatchService;
import com.aseubel.yusi.service.user.UserService;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * @author Aseubel
 * @date 2025/12/21
 */
@Slf4j
@Service
public class MatchServiceImpl implements MatchService {

    @Autowired
    private UserService userService;

    @Autowired
    private SoulMatchRepository soulMatchRepository;

    @Autowired
    private DiaryRepository diaryRepository;

    @Autowired
    private Assistant diaryAssistant;

    @Override
    @Scheduled(cron = "0 0 2 * * ?") // Every day at 2 AM
    public void runDailyMatching() {
        log.info("Starting daily matching process...");
        List<User> candidates = userService.getMatchEnabledUsers();
        if (CollUtil.isEmpty(candidates) || candidates.size() < 2) {
            log.info("Not enough candidates for matching.");
            return;
        }

        // Shuffle to randomize
        Collections.shuffle(candidates);

        // Simple pair matching logic (greedy)
        // Note: Real logic should be more complex (avoid recent duplicates, check
        // intent compatibility, etc.)
        List<String> processedUserIds = new ArrayList<>();

        for (User userA : candidates) {
            if (processedUserIds.contains(userA.getUserId()))
                continue;

            // Find a partner
            User partner = null;
            for (User potential : candidates) {
                if (potential.getUserId().equals(userA.getUserId()))
                    continue;
                if (processedUserIds.contains(potential.getUserId()))
                    continue;

                // Check if already matched recently (omitted for MVP simplicity, or check DB)
                // if (alreadyMatched(userA, potential)) continue;

                partner = potential;
                break;
            }

            if (partner != null) {
                processedUserIds.add(userA.getUserId());
                processedUserIds.add(partner.getUserId());
                createMatch(userA, partner);
            }
        }
        log.info("Daily matching process completed.");
    }

    private void createMatch(User userA, User userB) {
        log.info("Creating match for {} and {}", userA.getUserName(), userB.getUserName());

        String profileA = getProfileSummary(userA.getUserId());
        String profileB = getProfileSummary(userB.getUserId());

        // Generate Letter A to B (User A sees this recommending B)
        // Wait, prompt says: "Recommend User B to User A".
        // So letterAtoB = generate(profileA, profileB)

        // Asynchronously generate letters
        CompletableFuture<String> futureA = generateLetter(profileA, profileB);
        CompletableFuture<String> futureB = generateLetter(profileB, profileA); // Recommend A to B

        try {
            CompletableFuture.allOf(futureA, futureB).join();

            SoulMatch match = SoulMatch.builder()
                    .userAId(userA.getUserId())
                    .userBId(userB.getUserId())
                    .letterAtoB(futureA.get())
                    .letterBtoA(futureB.get())
                    .statusA(0) // Pending
                    .statusB(0) // Pending
                    .isMatched(false)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            soulMatchRepository.save(match);
        } catch (Exception e) {
            log.error("Failed to create match", e);
        }
    }

    private String getProfileSummary(String userId) {
        List<Diary> diaries = diaryRepository.findTop3ByUserIdOrderByCreateTimeDesc(userId);
        if (CollUtil.isEmpty(diaries)) {
            return "该用户比较神秘，还没有留下太多日记。";
        }
        return diaries.stream()
                .map(d -> "日期: " + d.getEntryDate() + "\n内容: " + d.getContent())
                .collect(Collectors.joining("\n\n"));
    }

    private CompletableFuture<String> generateLetter(String myProfile, String partnerProfile) {
        CompletableFuture<String> future = new CompletableFuture<>();
        StringBuilder sb = new StringBuilder();
        try {
            diaryAssistant.generateRecommendationLetter(myProfile, partnerProfile)
                    .onPartialResponse(sb::append)
                    .onCompleteResponse(res -> future.complete(sb.toString()))
                    .onError(future::completeExceptionally)
                    .start();
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    @Override
    public List<SoulMatch> getMatches(String userId) {
        List<SoulMatch> matchesA = soulMatchRepository.findByUserAId(userId);
        List<SoulMatch> matchesB = soulMatchRepository.findByUserBId(userId);
        List<SoulMatch> all = new ArrayList<>();
        all.addAll(matchesA);
        all.addAll(matchesB);
        return all;
    }

    @Override
    public SoulMatch handleMatchAction(String userId, Long matchId, Integer action) {
        SoulMatch match = soulMatchRepository.findById(matchId).orElse(null);
        if (match == null)
            return null;

        if (userId.equals(match.getUserAId())) {
            match.setStatusA(action);
        } else if (userId.equals(match.getUserBId())) {
            match.setStatusB(action);
        } else {
            return null; // Not involved
        }

        // Check for match
        if (Integer.valueOf(1).equals(match.getStatusA()) && Integer.valueOf(1).equals(match.getStatusB())) {
            match.setIsMatched(true);
        }

        match.setUpdateTime(LocalDateTime.now());
        return soulMatchRepository.save(match);
    }
}
