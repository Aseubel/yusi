package com.aseubel.yusi.service.ai.model;

import com.aseubel.yusi.pojo.dto.model.ModelCallTraceQuery;
import com.aseubel.yusi.pojo.dto.model.ModelMetricAggregate;
import com.aseubel.yusi.pojo.dto.model.ModelMetricSummary;
import com.aseubel.yusi.pojo.entity.ModelCallTrace;
import com.aseubel.yusi.repository.ModelCallTraceRepository;
import com.aseubel.yusi.repository.ModelCallTraceMetricsRepository;
import com.aseubel.yusi.service.security.SecurityAuditService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelManagementServiceTest {

    @Mock
    private ModelStateCenter modelStateCenter;

    @Mock
    private ModelConfigCenter modelConfigCenter;

    @Mock
    private ModelRouterService modelRouterService;

    @Mock
    private ModelInstanceRegistry modelInstanceRegistry;

    @Mock
    private ModelCallTraceRepository modelCallTraceRepository;

    @Mock
    private ModelCallTraceMetricsRepository metricsRepository;

    @Mock
    private SecurityAuditService securityAuditService;

    @InjectMocks
    private ModelManagementService service;

    @Test
    void filtersModelAttemptsByUserAndAgentRun() {
        when(modelCallTraceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        service.queryAttempts(ModelCallTraceQuery.builder()
                .userId("user-1")
                .runId("run-1")
                .build());

        ArgumentCaptor<Specification<ModelCallTrace>> specificationCaptor =
                ArgumentCaptor.forClass(Specification.class);
        verify(modelCallTraceRepository).findAll(specificationCaptor.capture(), any(Pageable.class));

        Root<ModelCallTrace> root = mock(Root.class);
        CriteriaQuery<?> criteriaQuery = mock(CriteriaQuery.class);
        CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
        Path<String> userPath = mock(Path.class);
        Path<String> runPath = mock(Path.class);
        Expression<String> userLower = mock(Expression.class);
        Expression<String> runLower = mock(Expression.class);
        when(root.<String>get("userId")).thenReturn(userPath);
        when(root.<String>get("runId")).thenReturn(runPath);
        when(criteriaBuilder.lower(userPath)).thenReturn(userLower);
        when(criteriaBuilder.lower(runPath)).thenReturn(runLower);

        specificationCaptor.getValue().toPredicate(root, criteriaQuery, criteriaBuilder);

        verify(root).get("userId");
        verify(root).get("runId");
        verify(criteriaBuilder).equal(userLower, "user-1");
        verify(criteriaBuilder).equal(runLower, "run-1");
    }

    @Test
    void mapsDatabaseAggregateWithoutLoadingAllTraces() {
        when(metricsRepository.aggregate(any())).thenReturn(new ModelMetricAggregate(
                42L, 5L, 0.119D, 0.952D, 180D, 420D,
                2L, 2L, 120_000L, 30_000L, new BigDecimal("1.25"), 3L));

        ModelMetricSummary result = service.getMetrics(ModelCallTraceQuery.builder().provider("openai").build());

        org.assertj.core.api.Assertions.assertThat(result.getCallCount()).isEqualTo(42L);
        org.assertj.core.api.Assertions.assertThat(result.getTotalTokens()).isEqualTo(150_000L);
        org.assertj.core.api.Assertions.assertThat(result.getInputTokens()).isEqualTo(120_000L);
        org.assertj.core.api.Assertions.assertThat(result.getOutputTokens()).isEqualTo(30_000L);
        org.assertj.core.api.Assertions.assertThat(result.getKnownCost()).isEqualByComparingTo("1.25");
        verify(metricsRepository).aggregate(any());
        verify(modelCallTraceRepository, never()).findAll(any(Specification.class));
    }
}
