package com.wisdri.tracking.infrastructure.repository.abnormal;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.postgres.abnormal.AbnormalDataEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import com.wisdri.tracking.infrastructure.service.postgres.abnormal.AbnormalDataMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AbnormalDataRepositoryImplTest {
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        assistant.setCurrentNamespace(AbnormalDataMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, AbnormalDataEntity.class);
    }

    @Test
    void storesUppercaseUnitCodeWithoutChangingDomainObject() {
        AbnormalDataRepositoryImpl repository = new AbnormalDataRepositoryImpl();
        AbnormalData data = AbnormalData.builder().unitCode("cp1").build();

        AbnormalDataEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", data);

        assertThat(entity.getUnitCode()).isEqualTo("CP1");
        assertThat(data.getUnitCode()).isEqualTo("cp1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void looksUpUppercaseUnitCode() {
        AbnormalDataRepositoryImpl repository = new AbnormalDataRepositoryImpl();
        AbnormalDataMapper mapper = mock(AbnormalDataMapper.class);
        ReflectionTestUtils.setField(repository, "abnormalDataMapper", mapper);
        when(mapper.selectList(any())).thenReturn(Collections.emptyList());

        repository.find("cp1", TrackingType.STATUS);

        ArgumentCaptor<LambdaQueryWrapper<AbnormalDataEntity>> query =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(mapper).selectList(query.capture());
        assertThat(query.getValue().getSqlSegment().toLowerCase())
                .contains("unit_code =", "tracking_type =");
        assertThat(query.getValue().getParamNameValuePairs().values())
                .contains("CP1", "status");
    }

    @Test
    void skipsAbnormalSaveWhenDisabled() {
        AbnormalDataRepositoryImpl repository = new AbnormalDataRepositoryImpl();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", abnormalDisabled());
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Collections.singletonList(AbnormalData.builder().build()));

        verifyNoInteractions(transactionTemplate);
    }

    @Test
    void skipsAbnormalSaveWhenDataIsEmpty() {
        AbnormalDataRepositoryImpl repository = new AbnormalDataRepositoryImpl();
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", abnormalEnabled());
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Collections.emptyList());

        verifyNoInteractions(transactionTemplate);
    }

    private TrackingProperties abnormalDisabled() {
        TrackingProperties properties = new TrackingProperties();
        TrackingProperties.StoreSwitch abnormal = new TrackingProperties.StoreSwitch();
        abnormal.setEnabled(false);
        properties.getStorage().setAbnormal(abnormal);
        return properties;
    }

    private TrackingProperties abnormalEnabled() {
        TrackingProperties properties = new TrackingProperties();
        TrackingProperties.StoreSwitch abnormal = new TrackingProperties.StoreSwitch();
        abnormal.setEnabled(true);
        properties.getStorage().setAbnormal(abnormal);
        return properties;
    }
}
