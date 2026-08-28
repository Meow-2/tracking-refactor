package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.coiler.CoilerResult;
import com.wisdri.tracking.infrastructure.dto.postgres.coiler.QmCoilerLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.coiler.QmCoilerLogMapper;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CoilerTrackingResultRepositoryImplTest {
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(QmCoilerLogMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, QmCoilerLogEntity.class);
    }

    @Test
    void mapsAllCoilerBusinessFields() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        Instant time = Instant.parse("2026-08-27T01:00:00Z");
        CoilerResult result = CoilerResult.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER).receivedAt(time)
                .inMatNo("COIL-1").inMatNoProdNo(3)
                .coilerMethod("99").coilerMethodName("下卷取")
                .deviceCode("tr1").deviceName("1#卷取机")
                .maxLength(new BigDecimal("800.500")).build();

        QmCoilerLogEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", result);

        assertThat(entity.getUnitCode()).isEqualTo("CP1");
        assertThat(entity.getInMatNo()).isEqualTo("COIL-1");
        assertThat(entity.getInMatNoProdNo()).isEqualTo("3");
        assertThat(entity.getPassNo()).isNull();
        assertThat(entity.getCoilerMethod()).isEqualTo("99");
        assertThat(entity.getCoilerMethodName()).isEqualTo("下卷取");
        assertThat(entity.getUncoilerMethod()).isNull();
        assertThat(entity.getUncoilerMethodName()).isNull();
        assertThat(entity.getDeviceCode()).isEqualTo("tr1");
        assertThat(entity.getDeviceName()).isEqualTo("1#卷取机");
        assertThat(entity.getMaxLength()).isEqualTo("800.500");
        assertThat(entity.getCreateTime()).isEqualTo(time);
    }

    @Test
    void mapsUncoilerMethodToUncoilerFields() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        CoilerResult result = CoilerResult.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER)
                .inMatNo("COIL-1")
                .uncoilerMethod("11").uncoilerMethodName("上开卷")
                .deviceCode("por1").deviceName("1#开卷机")
                .build();

        QmCoilerLogEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", result);

        assertThat(entity.getUncoilerMethod()).isEqualTo("11");
        assertThat(entity.getUncoilerMethodName()).isEqualTo("上开卷");
        assertThat(entity.getCoilerMethod()).isNull();
        assertThat(entity.getCoilerMethodName()).isNull();
    }

    @Test
    void updatesExistingCoilAndKeepsMethodFromOtherSide() {
        CoilerTrackingResultRepositoryImpl repository = enabledRepository();
        QmCoilerLogMapper mapper = mapper(repository);
        QmCoilerLogEntity existing = new QmCoilerLogEntity();
        existing.setId(10L);
        existing.setInMatNo("COIL-1");
        existing.setPassNo(2);
        existing.setUncoilerMethod("11");
        existing.setUncoilerMethodName("上开卷");
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<QmCoilerLogEntity> query = invocation.getArgument(0);
            String sql = query.getSqlSegment().toLowerCase();
            assertThat(sql).contains("in_mat_no =", "in_mat_no_prod_no =", "pass_no =");
            assertThat(query.getParamNameValuePairs())
                    .containsValue("COIL-1").containsValue("3").containsValue(2);
            return existing;
        });

        repository.save(Collections.singletonList(CoilerResult.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER)
                .inMatNo("COIL-1").inMatNoProdNo(3).passNo(2)
                .coilerMethod("99").coilerMethodName("下卷取")
                .deviceCode("tr1").deviceName("1#卷取机")
                .build()));

        ArgumentCaptor<QmCoilerLogEntity> captor = ArgumentCaptor.forClass(QmCoilerLogEntity.class);
        verify(mapper).updateById(captor.capture());
        verify(mapper, never()).insert(any(QmCoilerLogEntity.class));
        QmCoilerLogEntity updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(10L);
        assertThat(updated.getPassNo()).isEqualTo(2);
        assertThat(updated.getUncoilerMethod()).isEqualTo("11");
        assertThat(updated.getUncoilerMethodName()).isEqualTo("上开卷");
        assertThat(updated.getCoilerMethod()).isEqualTo("99");
        assertThat(updated.getCoilerMethodName()).isEqualTo("下卷取");
    }

    @Test
    void insertsCoilWhenNoExistingRecordIsFound() {
        CoilerTrackingResultRepositoryImpl repository = enabledRepository();
        QmCoilerLogMapper mapper = mapper(repository);
        when(mapper.selectOne(any())).thenAnswer(invocation -> {
            LambdaQueryWrapper<QmCoilerLogEntity> query = invocation.getArgument(0);
            String sql = query.getSqlSegment().toLowerCase();
            assertThat(sql).contains("in_mat_no =", "in_mat_no_prod_no is null", "pass_no is null");
            assertThat(query.getParamNameValuePairs()).containsValue("COIL-2");
            return null;
        });

        repository.save(Collections.singletonList(CoilerResult.builder()
                .unitCode("CP1").trackingType(TrackingType.COILER)
                .inMatNo("COIL-2")
                .uncoilerMethod("91").uncoilerMethodName("下开卷")
                .deviceCode("por2").deviceName("2#开卷机")
                .build()));

        ArgumentCaptor<QmCoilerLogEntity> captor = ArgumentCaptor.forClass(QmCoilerLogEntity.class);
        verify(mapper).insert(captor.capture());
        verify(mapper, never()).updateById(any(QmCoilerLogEntity.class));
        assertThat(captor.getValue().getInMatNo()).isEqualTo("COIL-2");
        assertThat(captor.getValue().getInMatNoProdNo()).isNull();
        assertThat(captor.getValue().getPassNo()).isNull();
        assertThat(captor.getValue().getUncoilerMethod()).isEqualTo("91");
        assertThat(captor.getValue().getCoilerMethod()).isNull();
    }

    @Test
    void disabledCoilerStorageDoesNotOpenTransaction() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getCoiler().setEnabled(false);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", properties);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Collections.singletonList(CoilerResult.builder()
                .trackingType(TrackingType.COILER).build()));

        verifyNoInteractions(transactionTemplate);
    }

    private CoilerTrackingResultRepositoryImpl enabledRepository() {
        CoilerTrackingResultRepositoryImpl repository = new CoilerTrackingResultRepositoryImpl();
        QmCoilerLogMapper mapper = mock(QmCoilerLogMapper.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        }).when(transactionTemplate).execute(any());
        ReflectionTestUtils.setField(repository, "baseMapper", mapper);
        ReflectionTestUtils.setField(repository, "trackingProperties", new TrackingProperties());
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);
        return repository;
    }

    private QmCoilerLogMapper mapper(CoilerTrackingResultRepositoryImpl repository) {
        return (QmCoilerLogMapper) ReflectionTestUtils.getField(repository, "baseMapper");
    }
}
