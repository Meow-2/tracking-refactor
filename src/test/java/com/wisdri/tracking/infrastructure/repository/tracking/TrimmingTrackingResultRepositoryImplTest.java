package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.trimming.TrimmingResult;
import com.wisdri.tracking.infrastructure.dto.postgres.trimming.QmTrimmingLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.trimming.QmTrimmingLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrimmingTrackingResultRepositoryImplTest {
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(QmTrimmingLogMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, QmTrimmingLogEntity.class);
    }

    @Test
    void insertsFirstRecordWithDecimalTextFields() {
        TrimmingTrackingResultRepositoryImpl repository = repository();
        QmTrimmingLogMapper mapper = mapper(repository);

        repository.save(Collections.singletonList(result(false)));

        ArgumentCaptor<QmTrimmingLogEntity> entity = ArgumentCaptor.forClass(QmTrimmingLogEntity.class);
        verify(mapper).insert(entity.capture());
        verify(mapper).selectCount(any());
        verify(mapper, never()).update(any(), any());
        assertThat(entity.getValue().getUnitCode()).isEqualTo("CP1");
        assertThat(entity.getValue().getInMatNo()).isEqualTo("C001");
        assertThat(entity.getValue().getInMatRepeatProdNo()).isEqualTo("2");
        assertThat(entity.getValue().getCoilWidthPv()).isEqualTo("1005.0");
        assertThat(entity.getValue().getCoilWidthSv()).isEqualTo("1000");
        assertThat(entity.getValue().getTrimmingLength()).isEqualTo("2.50");
        assertThat(entity.getValue().getCreateTime()).isEqualTo(Instant.parse("2026-08-29T01:00:00Z"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesDatabaseRecordWhenRuntimeTreatsItAsNew() {
        TrimmingTrackingResultRepositoryImpl repository = repository();
        QmTrimmingLogMapper mapper = mapper(repository);
        when(mapper.selectCount(any())).thenReturn(1L);
        when(mapper.update(any(), any())).thenReturn(1);

        repository.save(Collections.singletonList(result(false)));

        ArgumentCaptor<LambdaQueryWrapper<QmTrimmingLogEntity>> query =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectCount(query.capture());
        assertThat(query.getValue().getSqlSegment().toLowerCase())
                .contains("unit_code =", "in_mat_no =", "repeat_prod_no =");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .contains("CP1", "C001", "2");
        verify(mapper).update(any(), any());
        verify(mapper, never()).insert(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void updatesZeroRecordByBusinessKeyWithoutSelecting() {
        TrimmingTrackingResultRepositoryImpl repository = repository();
        QmTrimmingLogMapper mapper = mapper(repository);
        when(mapper.update(any(), any())).thenReturn(1);

        repository.save(Collections.singletonList(result(true)));

        ArgumentCaptor<QmTrimmingLogEntity> entity = ArgumentCaptor.forClass(QmTrimmingLogEntity.class);
        ArgumentCaptor<LambdaUpdateWrapper<QmTrimmingLogEntity>> wrapper =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(mapper).update(entity.capture(), wrapper.capture());
        verify(mapper, never()).selectCount(any());
        verify(mapper, never()).insert(any());
        assertThat(wrapper.getValue().getSqlSegment().toLowerCase())
                .contains("unit_code =", "in_mat_no =", "repeat_prod_no =");
        assertThat(wrapper.getValue().getParamNameValuePairs().values())
                .contains("CP1", "C001", "2");
        assertThat(entity.getValue().getCreateTime()).isNull();
        assertThat(entity.getValue().getUpdateTime()).isEqualTo(Instant.parse("2026-08-29T01:00:00Z"));
    }

    private TrimmingResult result(boolean update) {
        return TrimmingResult.builder()
                .unitCode("cp1").trackingType(TrackingType.TRIMMING)
                .receivedAt(Instant.parse("2026-08-29T01:00:00Z"))
                .inMatNo("C001").repeatProdNo(2)
                .coilWidthPv(new BigDecimal("1005.0"))
                .coilWidthSv(new BigDecimal("1000"))
                .trimmingLength(new BigDecimal("2.50"))
                .updateExisting(update).build();
    }

    private TrimmingTrackingResultRepositoryImpl repository() {
        TrimmingTrackingResultRepositoryImpl repository = new TrimmingTrackingResultRepositoryImpl();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
        ReflectionTestUtils.setField(repository, "baseMapper", mock(QmTrimmingLogMapper.class));
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);
        return repository;
    }

    private QmTrimmingLogMapper mapper(TrimmingTrackingResultRepositoryImpl repository) {
        return (QmTrimmingLogMapper) ReflectionTestUtils.getField(repository, "baseMapper");
    }
}
