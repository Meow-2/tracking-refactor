package com.wisdri.tracking.infrastructure.repository.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AbnormalDataRepositoryImplTest {

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
