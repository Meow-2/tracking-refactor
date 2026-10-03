package com.wisdri.tracking.infrastructure.repository.tracking;

import com.baomidou.mybatisplus.annotation.TableName;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.domain.model.tracking.shear.ShearResult;
import com.wisdri.tracking.infrastructure.dto.postgres.shear.QmShearLogEntity;
import com.wisdri.tracking.infrastructure.properties.TrackingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ShearTrackingResultRepositoryImplTest {
    @Test
    void mapsEntityToFinalDatabaseTableName() {
        assertThat(QmShearLogEntity.class.getAnnotation(TableName.class).value())
                .isEqualTo("qm_dc_shear_log");
    }

    @Test
    void mapsAllShearBusinessFields() {
        ShearTrackingResultRepositoryImpl repository = new ShearTrackingResultRepositoryImpl();
        Instant time = Instant.parse("2026-01-01T00:00:00Z");
        ShearResult result = ShearResult.builder()
                .unitCode("LINE-X").trackingType(TrackingType.SHEAR)
                .inMatNo("MAT-1").repeatProdNo("3")
                .shearType("711").shearTypeName("feed-device-x_head")
                .shearLength(new BigDecimal("2.5")).setNumber(4).shearTime(time)
                .deviceCode("feed-device-x")
                .inMatDeviceCode("por1").inMatDeviceColorNo("10")
                .inMatDeviceRemainLength(new BigDecimal("500"))
                .inMatDeviceMaxLength(new BigDecimal("1000"))
                .shearDeviceCoilNo("TR-1").shearDeviceColorNo("20")
                .shearDeviceRemainLength(new BigDecimal("200"))
                .shearDeviceMaxLength(new BigDecimal("800"))
                .cutNo(2).shearNo(1).build();

        QmShearLogEntity entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", result);

        assertThat(entity.getUnitCode()).isEqualTo("LINE-X");
        assertThat(entity.getInMatNo()).isEqualTo("MAT-1");
        assertThat(entity.getInMatRepeatProdNo()).isEqualTo("3");
        assertThat(entity.getShearType()).isEqualTo("711");
        assertThat(entity.getShearTypeName()).isEqualTo("feed-device-x_head");
        assertThat(entity.getShearLength()).isEqualByComparingTo("2.5");
        assertThat(entity.getSetNumber()).isEqualTo(4);
        assertThat(entity.getShearTime()).isEqualTo(time);
        assertThat(entity.getShearDeviceCode()).isEqualTo("feed-device-x");
        assertThat(entity.getInMatDeviceCode()).isEqualTo("por1");
        assertThat(entity.getInMatDeviceRemainLength()).isEqualByComparingTo("500");
        assertThat(entity.getShearDeviceCoilNo()).isEqualTo("TR-1");
        assertThat(entity.getCutNo()).isEqualTo(2);
        assertThat(entity.getShearNo()).isEqualTo(1);
    }

    @Test
    void truncatesOnlyCsl1InMatNoLongerThanElevenCharacters() {
        ShearTrackingResultRepositoryImpl repository = new ShearTrackingResultRepositoryImpl();
        String longCoilNo = "12345678901EXTRA";
        ShearResult csl1Result = ShearResult.builder()
                .unitCode("csl1").inMatNo(longCoilNo).build();

        QmShearLogEntity csl1Entity = ReflectionTestUtils.invokeMethod(repository, "toEntity", csl1Result);
        QmShearLogEntity otherUnitEntity = ReflectionTestUtils.invokeMethod(repository, "toEntity",
                ShearResult.builder().unitCode("CBL1").inMatNo(longCoilNo).build());
        QmShearLogEntity elevenCharacterEntity = ReflectionTestUtils.invokeMethod(repository, "toEntity",
                ShearResult.builder().unitCode("CSL1").inMatNo("12345678901").build());
        QmShearLogEntity nullCoilEntity = ReflectionTestUtils.invokeMethod(repository, "toEntity",
                ShearResult.builder().unitCode("CSL1").build());

        assertThat(csl1Entity.getInMatNo()).isEqualTo("12345678901");
        assertThat(csl1Result.getInMatNo()).isEqualTo(longCoilNo);
        assertThat(otherUnitEntity.getInMatNo()).isEqualTo(longCoilNo);
        assertThat(elevenCharacterEntity.getInMatNo()).isEqualTo("12345678901");
        assertThat(nullCoilEntity.getInMatNo()).isNull();
    }

    @Test
    void disabledShearStorageDoesNotOpenTransaction() {
        ShearTrackingResultRepositoryImpl repository = new ShearTrackingResultRepositoryImpl();
        TrackingProperties properties = new TrackingProperties();
        properties.getStorage().getShear().setEnabled(false);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(repository, "trackingProperties", properties);
        ReflectionTestUtils.setField(repository, "transactionTemplate", transactionTemplate);

        repository.save(Arrays.asList(ShearResult.builder()
                .trackingType(TrackingType.SHEAR).build()));

        verifyNoInteractions(transactionTemplate);
    }
}
