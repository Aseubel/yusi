package com.aseubel.yusi.privacy;

import com.aseubel.yusi.TestInfrastructureConfig;
import com.aseubel.yusi.pojo.entity.AccountDeletionRequest;
import com.aseubel.yusi.pojo.entity.User;
import com.aseubel.yusi.repository.AccountDeletionRequestRepository;
import com.aseubel.yusi.repository.UserRepository;
import com.aseubel.yusi.service.privacy.AccountDeletionCoordinator;
import com.aseubel.yusi.service.privacy.AccountDeletionExternalPort;
import com.aseubel.yusi.service.privacy.DeletionResult;
import com.aseubel.yusi.service.security.SecurityAuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestInfrastructureConfig.class)
class AccountDeletionTransactionBoundaryTest {

    private static final String TARGET_USER = "fixture-transaction-delete-target";
    private static final String ADMIN_USER = "fixture-transaction-delete-admin";

    @Autowired
    private AccountDeletionCoordinator coordinator;
    @Autowired
    private AccountDeletionRequestRepository requestRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private AccountDeletionExternalPort externalPort;
    @MockBean
    private SecurityAuditService securityAuditService;

    @BeforeEach
    void setUp() {
        requestRepository.deleteAll();
        deleteUserIfPresent(TARGET_USER);
        deleteUserIfPresent(ADMIN_USER);
        userRepository.save(User.builder().userId(TARGET_USER).userName(TARGET_USER)
                .password("fixture-password").permissionLevel(0).build());
    }

    @AfterEach
    void tearDown() {
        requestRepository.deleteAll();
        deleteUserIfPresent(TARGET_USER);
        deleteUserIfPresent(ADMIN_USER);
    }

    @Test
    void failedDeletionKeepsRetryLedgerWhenCallerAlreadyHasTransaction() {
        doThrow(new IllegalStateException("fixture-external-failure"))
                .when(externalPort).deleteMilvus(any());

        DeletionResult result = new TransactionTemplate(transactionManager)
                .execute(status -> coordinator.requestDeletion(TARGET_USER, ADMIN_USER));

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(DeletionResult.Status.PENDING_RETRY);
        assertThat(requestRepository.findByRequestId(result.requestId()))
                .get()
                .satisfies(request -> {
                    assertThat(request.getStatus()).isEqualTo(
                            com.aseubel.yusi.pojo.entity.AccountDeletionRequest.Status.PENDING_RETRY);
                    assertThat(request.getFailureCategory()).isEqualTo("external_or_database");
                });
        assertThat(userRepository.findByUserId(TARGET_USER)).isNotNull();
    }

    @Test
    void completionLedgerMustNotCommitBeforeDeletionTransactionAndAudit() {
        AtomicReference<String> observedStatus = new AtomicReference<>();
        doAnswer(invocation -> {
            Map<String, String> details = invocation.getArgument(7);
            TransactionTemplate observer = new TransactionTemplate(transactionManager);
            observer.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            observedStatus.set(observer.execute(status -> jdbcTemplate.queryForObject(
                    "SELECT status FROM account_deletion_request WHERE request_id = ?",
                    String.class, details.get("requestId"))));
            throw new IllegalStateException("fixture-audit-failure");
        }).when(securityAuditService).recordAdmin(any(), any(), any(), any(), any(), any(), any(), any());

        DeletionResult result = coordinator.requestDeletion(TARGET_USER, ADMIN_USER);

        assertThat(result.status()).isEqualTo(DeletionResult.Status.PENDING_RETRY);
        assertThat(observedStatus).hasValue("RUNNING");
        assertThat(userRepository.findByUserId(TARGET_USER)).isNotNull();
    }

    @Test
    void successfulDeletionMustDeidentifyPriorPendingTargetReferences() {
        doThrow(new IllegalStateException("fixture-external-failure"))
                .doNothing()
                .when(externalPort).deleteMilvus(any());

        DeletionResult first = coordinator.requestDeletion(TARGET_USER, ADMIN_USER);
        assertThat(first.status()).isEqualTo(DeletionResult.Status.PENDING_RETRY);

        DeletionResult second = coordinator.requestDeletion(TARGET_USER, ADMIN_USER);

        assertThat(second.status()).isEqualTo(DeletionResult.Status.COMPLETED);
        assertThat(requestRepository.findAll())
                .allSatisfy(request -> assertThat(request.getTargetUserRef()).isNull());
        assertThat(requestRepository.findAll())
                .allSatisfy(request -> assertThat(request.getRequestedByRef()).isNull());
        assertThat(requestRepository.findByRequestId(first.requestId()))
                .get()
                .extracting(AccountDeletionRequest::getStatus)
                .isEqualTo(AccountDeletionRequest.Status.SUPERSEDED);
    }

    @Test
    void deletingRequesterMustDeidentifyExistingLedgerReferences() {
        requestRepository.save(AccountDeletionRequest.builder()
                .requestId("fixture-existing-request")
                .targetUserRef("fixture-unrelated-target")
                .requestedByRef(TARGET_USER)
                .status(AccountDeletionRequest.Status.PENDING_RETRY)
                .retryCount(1)
                .build());

        DeletionResult result = coordinator.requestDeletion(TARGET_USER, ADMIN_USER);

        assertThat(result.status()).isEqualTo(DeletionResult.Status.COMPLETED);
        assertThat(requestRepository.findByRequestId("fixture-existing-request"))
                .get()
                .satisfies(request -> assertThat(request.getRequestedByRef()).isNull());
    }

    private void deleteUserIfPresent(String userId) {
        User user = userRepository.findByUserId(userId);
        if (user != null) {
            userRepository.delete(user);
        }
    }
}
