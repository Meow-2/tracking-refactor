package com.wisdri.tracking.infrastructure.repository.product;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wisdri.tracking.infrastructure.dto.postgres.product.QmDcRepeatProdNoLogEntity;
import com.wisdri.tracking.infrastructure.service.postgres.product.RepeatProdNoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 验证逐次取号顺序和 MyBatis-Plus 实体读写。 */
class RepeatProdNoRepositoryImplTest {
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(RepeatProdNoMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, QmDcRepeatProdNoLogEntity.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void allocatesAfterLatestWithoutUpdatingPreviousRows() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        QmDcRepeatProdNoLogEntity previous = record(2);
        when(mapper.selectOne(any())).thenReturn(previous);
        when(mapper.insert(any())).thenReturn(1);

        assertThat(repository.allocateNext("cp1", "COIL-A")).isEqualTo(3);

        ArgumentCaptor<LambdaQueryWrapper<QmDcRepeatProdNoLogEntity>> query =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        ArgumentCaptor<QmDcRepeatProdNoLogEntity> inserted =
                ArgumentCaptor.forClass(QmDcRepeatProdNoLogEntity.class);
        org.mockito.InOrder calls = inOrder(mapper);
        calls.verify(mapper).selectOne(query.capture());
        calls.verify(mapper).insert(inserted.capture());
        calls.verifyNoMoreInteractions();
        assertThat(query.getValue().getSqlSegment().toLowerCase())
                .contains("unit_code =", "in_mat_no =", "in_mat_repeat_prod_no desc", "limit 1");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("CP1", "COIL-A");
        assertThat(inserted.getValue().getUnitCode()).isEqualTo("CP1");
        assertThat(inserted.getValue().getInMatNo()).isEqualTo("COIL-A");
        assertThat(inserted.getValue().getInMatRepeatProdNo()).isEqualTo(3);
        assertThat(inserted.getValue().getDeleted()).isZero();
        assertThat(inserted.getValue().getCreateTime()).isNotNull();
        assertThat(previous.getInMatRepeatProdNo()).isEqualTo(2);
    }

    @Test
    void consecutiveAllocationsAppendOneAndTwo() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        List<QmDcRepeatProdNoLogEntity> persisted = new ArrayList<>();
        when(mapper.selectOne(any())).thenAnswer(invocation ->
                persisted.isEmpty() ? null : persisted.get(persisted.size() - 1));
        when(mapper.insert(any())).thenAnswer(invocation -> {
            persisted.add(invocation.getArgument(0));
            return 1;
        });

        assertThat(repository.allocateNext("CP1", "COIL-A")).isEqualTo(1);
        assertThat(repository.allocateNext("CP1", "COIL-A")).isEqualTo(2);
        assertThat(persisted).extracting(QmDcRepeatProdNoLogEntity::getInMatRepeatProdNo)
                .containsExactly(1, 2);
    }

    @Test
    void findLatestOnlyReadsMaximum() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.selectOne(any())).thenReturn(record(4));

        assertThat(repository.findLatest("cp1", "COIL-A")).isEqualTo(4);
        verify(mapper).selectOne(any());
    }

    @Test
    void findLatestReturnsOneWithoutInsertingWhenRecordIsMissing() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);

        assertThat(repository.findLatest("cp1", "COIL-UNKNOWN")).isEqualTo(1);
        verify(mapper).selectOne(any());
        org.mockito.Mockito.verifyNoMoreInteractions(mapper);
    }

    @Test
    void findLatestOrAllocateReusesExistingRecord() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.selectOne(any())).thenReturn(record(4));

        assertThat(repository.findLatestOrAllocate("cp1", "COIL-A")).isEqualTo(4);
        verify(mapper).selectOne(any());
        org.mockito.Mockito.verifyNoMoreInteractions(mapper);
    }

    @Test
    void findLatestOrAllocateInsertsFirstRecordWhenMissing() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.insert(any())).thenReturn(1);

        assertThat(repository.findLatestOrAllocate("cp1", "COIL-A")).isEqualTo(1);

        ArgumentCaptor<QmDcRepeatProdNoLogEntity> inserted =
                ArgumentCaptor.forClass(QmDcRepeatProdNoLogEntity.class);
        verify(mapper).insert(inserted.capture());
        assertThat(inserted.getValue().getUnitCode()).isEqualTo("CP1");
        assertThat(inserted.getValue().getInMatNo()).isEqualTo("COIL-A");
        assertThat(inserted.getValue().getInMatRepeatProdNo()).isEqualTo(1);
    }

    @Test
    void rejectsBlankBusinessKeyBeforeQuerying() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);

        assertThatThrownBy(() -> repository.allocateNext(" ", "COIL-A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findLatest("CP1", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void failsAllocationWhenInsertAffectsNoRow() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);

        assertThatThrownBy(() -> repository.allocateNext("CP1", "COIL-A"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("保存钢卷重复生产次数失败");
    }

    private QmDcRepeatProdNoLogEntity record(int repeatProdNo) {
        QmDcRepeatProdNoLogEntity entity = new QmDcRepeatProdNoLogEntity();
        entity.setInMatRepeatProdNo(repeatProdNo);
        return entity;
    }

    private RepeatProdNoRepositoryImpl repository(RepeatProdNoMapper mapper) {
        RepeatProdNoRepositoryImpl repository = new RepeatProdNoRepositoryImpl();
        ReflectionTestUtils.setField(repository, "baseMapper", mapper);
        return repository;
    }
}
