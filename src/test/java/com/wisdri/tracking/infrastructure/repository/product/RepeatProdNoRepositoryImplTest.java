package com.wisdri.tracking.infrastructure.repository.product;

import com.wisdri.tracking.infrastructure.service.postgres.product.RepeatProdNoMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RepeatProdNoRepositoryImplTest {
    @Test
    void allocateNextInsertsOneAfterLatestWithoutUpdatingPreviousRows() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.findLatest("CP1", "COIL-A")).thenReturn(2);
        when(mapper.insert(anyLong(), eq("CP1"), eq("COIL-A"), eq(3))).thenReturn(3);

        assertThat(repository.allocateNext("cp1", "COIL-A")).isEqualTo(3);
        org.mockito.InOrder calls = inOrder(mapper);
        calls.verify(mapper).lockCoil("CP1", "COIL-A");
        calls.verify(mapper).findLatest("CP1", "COIL-A");
        calls.verify(mapper).insert(anyLong(), eq("CP1"), eq("COIL-A"), eq(3));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void allocateNextStartsAtOneWhenNoHistoryExists() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.insert(anyLong(), eq("CP1"), eq("COIL-A"), eq(1))).thenReturn(1);

        assertThat(repository.allocateNext("CP1", "COIL-A")).isEqualTo(1);
        verify(mapper).lockCoil("CP1", "COIL-A");
        verify(mapper).findLatest("CP1", "COIL-A");
        verify(mapper).insert(anyLong(), eq("CP1"), eq("COIL-A"), eq(1));
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void consecutiveAllocationsAppendOneAndTwo() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        List<Integer> persisted = new ArrayList<>();
        when(mapper.findLatest("CP1", "COIL-A"))
                .thenAnswer(invocation -> persisted.isEmpty() ? null : persisted.get(persisted.size() - 1));
        when(mapper.insert(anyLong(), eq("CP1"), eq("COIL-A"),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(invocation -> {
                    Integer allocated = invocation.getArgument(3);
                    persisted.add(allocated);
                    return allocated;
                });

        assertThat(repository.allocateNext("CP1", "COIL-A")).isEqualTo(1);
        assertThat(repository.allocateNext("CP1", "COIL-A")).isEqualTo(2);
        assertThat(persisted).containsExactly(1, 2);
        verify(mapper, org.mockito.Mockito.times(2)).lockCoil("CP1", "COIL-A");
    }

    @Test
    void findLatestDoesNotAllocateWhenRecordIsMissing() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);
        when(mapper.findLatest("CP1", "COIL-UNKNOWN")).thenReturn(null);

        assertThat(repository.findLatest("cp1", "COIL-UNKNOWN")).isNull();
        verify(mapper).findLatest("CP1", "COIL-UNKNOWN");
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void rejectsBlankBusinessKeyBeforeAccessingDatabase() {
        RepeatProdNoMapper mapper = mock(RepeatProdNoMapper.class);
        RepeatProdNoRepositoryImpl repository = repository(mapper);

        assertThatThrownBy(() -> repository.allocateNext(" ", "COIL-A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repository.findLatest("CP1", null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    private RepeatProdNoRepositoryImpl repository(RepeatProdNoMapper mapper) {
        RepeatProdNoRepositoryImpl repository = new RepeatProdNoRepositoryImpl();
        ReflectionTestUtils.setField(repository, "repeatProdNoMapper", mapper);
        return repository;
    }
}
