package com.wisdri.tracking.infrastructure.repository.product;

import com.wisdri.tracking.infrastructure.service.postgres.product.ProductNoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductNoRepositoryImplTest {
    @Test
    void incrementAndGetReturnsAllocatedNumber() {
        ProductNoMapper mapper = mock(ProductNoMapper.class);
        ProductNoRepositoryImpl repository = repository(mapper);
        when(mapper.incrementAndGet(anyLong(), eq("CP1"), eq("COIL-A"))).thenReturn(3);

        assertThat(repository.incrementAndGet("CP1", "COIL-A")).isEqualTo(3);
        verify(mapper).incrementAndGet(anyLong(), eq("CP1"), eq("COIL-A"));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void findCurrentDoesNotAllocateWhenRecordIsMissing() {
        ProductNoMapper mapper = mock(ProductNoMapper.class);
        ProductNoRepositoryImpl repository = repository(mapper);
        when(mapper.findCurrent("CP1", "COIL-UNKNOWN")).thenReturn(null);

        assertThat(repository.findCurrent("CP1", "COIL-UNKNOWN")).isNull();
        verify(mapper).findCurrent("CP1", "COIL-UNKNOWN");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void rejectsBlankBusinessKeyBeforeAccessingDatabase() {
        ProductNoMapper mapper = mock(ProductNoMapper.class);
        ProductNoRepositoryImpl repository = repository(mapper);

        assertThatThrownBy(() -> repository.incrementAndGet(" ", "COIL-A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findCurrent("CP1", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    private ProductNoRepositoryImpl repository(ProductNoMapper mapper) {
        ProductNoRepositoryImpl repository = new ProductNoRepositoryImpl();
        ReflectionTestUtils.setField(repository, "productNoMapper", mapper);
        return repository;
    }
}
