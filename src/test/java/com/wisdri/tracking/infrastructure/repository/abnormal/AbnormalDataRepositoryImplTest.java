package com.wisdri.tracking.infrastructure.repository.abnormal;

import com.wisdri.tracking.domain.model.abnormal.AbnormalData;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

class AbnormalDataRepositoryImplTest {

    @Test
    void skipsAbnormalSaveWhenDisabled() {
        AbnormalDataRepositoryImpl repository = new AbnormalDataRepositoryImpl();
        ReflectionTestUtils.setField(repository, "trackingProperties", abnormalDisabled());

        repository.save(Collections.singletonList(AbnormalData.builder().build()));
    }

    private TrackingProperties abnormalDisabled() {
        TrackingProperties properties = new TrackingProperties();
        TrackingProperties.StoreSwitch abnormal = new TrackingProperties.StoreSwitch();
        abnormal.setEnabled(false);
        properties.getStorage().setAbnormal(abnormal);
        return properties;
    }
}
