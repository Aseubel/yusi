package com.aseubel.yusi.service.notification;

import com.aseubel.yusi.common.exception.BusinessException;
import com.aseubel.yusi.pojo.dto.notification.AnnouncementResponse;
import com.aseubel.yusi.pojo.dto.notification.PublishAnnouncementRequest;
import com.aseubel.yusi.pojo.entity.NotificationAnnouncement;
import com.aseubel.yusi.pojo.entity.ProductEvent;
import com.aseubel.yusi.pojo.entity.UserNotification;
import com.aseubel.yusi.repository.NotificationAnnouncementRepository;
import com.aseubel.yusi.repository.UserNotificationRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.event.ProductEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private UserNotificationRepository notificationRepository;

    @Mock
    private NotificationAnnouncementRepository announcementRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProductEventService productEventService;

    @InjectMocks
    private NotificationService notificationService;

    @org.junit.jupiter.api.BeforeEach
    void stubProductEvents() {
        lenient().when(productEventService.record(any())).thenReturn(ProductEvent.builder()
                .eventId("event-notification-1")
                .build());
    }

    @Test
    void notificationTypeContractAcceptsCanonicalCaseInsensitiveValues() {
        assertThat(UserNotification.NotificationType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "MERGE_SUGGESTION",
                        "SYSTEM",
                        "REMINDER",
                        "ANNOUNCEMENT",
                        "SOUL_WEEKLY_REPORT",
                        "AGENT_GREETING",
                        "RESONANCE_SIGNAL",
                        "MUTUAL_RESONANCE");
        assertThat(UserNotification.NotificationType.fromValue(" announcement "))
                .isEqualTo(UserNotification.NotificationType.ANNOUNCEMENT);
    }

    @Test
    void createNotificationPersistsSourceEventId() {
        when(notificationRepository.save(any(UserNotification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        notificationService.createNotification("user-1", UserNotification.NotificationType.SYSTEM,
                "Title", "Content", null, null, null, "event-system-1");

        ArgumentCaptor<UserNotification> captor = ArgumentCaptor.forClass(UserNotification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getSourceEventId()).isEqualTo("event-system-1");
    }

    @Test
    void publishesAnnouncementSourceAndOneInboxItemPerDistinctUser() {
        when(userRepository.findAllUserIds()).thenReturn(List.of("user-1", "", "user-1", "user-2"));
        when(announcementRepository.save(any(NotificationAnnouncement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PublishAnnouncementRequest request = new PublishAnnouncementRequest();
        request.setTitle("  Service update  ");
        request.setContent("  A new feature is available.  ");
        request.setAudience("all");

        AnnouncementResponse response = notificationService.publishAnnouncement(request, "admin-1");

        ArgumentCaptor<NotificationAnnouncement> announcementCaptor =
                ArgumentCaptor.forClass(NotificationAnnouncement.class);
        verify(announcementRepository).save(announcementCaptor.capture());
        NotificationAnnouncement announcement = announcementCaptor.getValue();
        assertThat(announcement.getTitle()).isEqualTo("Service update");
        assertThat(announcement.getContent()).isEqualTo("A new feature is available.");
        assertThat(announcement.getRecipientCount()).isEqualTo(2L);
        assertThat(response.getAnnouncementId()).isEqualTo(announcement.getAnnouncementId());
        assertThat(response.getRecipientCount()).isEqualTo(2L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<UserNotification>> inboxCaptor = (ArgumentCaptor<List<UserNotification>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(inboxCaptor.capture());
        List<UserNotification> inboxItems = inboxCaptor.getValue();
        assertThat(inboxItems).hasSize(2).allSatisfy(item -> {
            assertThat(item.getType()).isEqualTo(UserNotification.NotificationType.ANNOUNCEMENT.name());
            assertThat(item.getRefType()).isEqualTo(UserNotification.RefType.ANNOUNCEMENT.name());
            assertThat(item.getRefId()).isEqualTo(announcement.getAnnouncementId());
            assertThat(item.getAnnouncementId()).isEqualTo(announcement.getAnnouncementId());
            assertThat(item.getIsRead()).isFalse();
        });
    }

    @Test
    void rejectsUnsupportedAudienceBeforeCreatingPublication() {
        PublishAnnouncementRequest request = new PublishAnnouncementRequest();
        request.setTitle("Title");
        request.setContent("Content");
        request.setAudience("USER_GROUP");

        assertThatThrownBy(() -> notificationService.publishAnnouncement(request, "admin-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("受众");
        verify(userRepository, never()).findAllUserIds();
        verify(announcementRepository, never()).save(any(NotificationAnnouncement.class));
    }

    @Test
    void normalizesTypeFilterBeforeQueryingInbox() {
        when(notificationRepository.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                "user-1", "ANNOUNCEMENT", PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of()));

        notificationService.getNotifications("user-1", 0, 20, " announcement ");

        verify(notificationRepository).findByUserIdAndTypeOrderByCreatedAtDescIdDesc(
                "user-1", "ANNOUNCEMENT", PageRequest.of(0, 20));
    }
}
