package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.PointConfig;
import com.wisdri.tracking.domain.model.config.PointDataType;
import com.wisdri.tracking.infrastructure.dto.history.PointHistoryMetadata;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.service.feign.converter.history.PointHistoryMetadataConverter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointHistoryMetadataConverterTest {
    private final PointHistoryMetadataConverter converter = new PointHistoryMetadataConverter();

    @Test
    void resolvesOtherTrackingTypesWithCustomDatabaseAndIgnoresUnrequestedPoints() {
        CubeApiTreeNode root = new CubeApiTreeNode();
        CubeApiTreeNode entry = new CubeApiTreeNode();
        CubeApiTreeNode exit = new CubeApiTreeNode();
        entry.getChildren().put("speed", point("entry_speed_pv", "double"));
        exit.getChildren().put("speed", point("exit_speed_pv", "float"));
        root.getChildren().put("entry", entry);
        root.getChildren().put("exit", exit);
        root.getChildren().put("unused", new CubeApiTreeNode());

        Map<String, PointHistoryMetadata> result = converter.convert(root, "/plant/cp1/process",
                "/plant/cp1/process/", Arrays.asList(
                        new PointConfig("entry/speed", PointDataType.FLOAT),
                        new PointConfig("/plant/cp1/process/exit/speed", PointDataType.FLOAT)), "archive");

        assertThat(result).containsOnlyKeys("/plant/cp1/process/entry/speed", "/plant/cp1/process/exit/speed");
        PointHistoryMetadata metadata = result.get("/plant/cp1/process/entry/speed");
        assertThat(metadata.getPath()).isEqualTo("/plant/cp1/process/entry/speed");
        assertThat(metadata.getDatabase()).isEqualTo("archive");
        assertThat(metadata.getObjectName()).isEqualTo("cp1_plc");
        assertThat(metadata.getCode()).isEqualTo("entry_speed_pv");
        assertThat(result.get("/plant/cp1/process/exit/speed").getCode()).isEqualTo("exit_speed_pv");
    }

    @Test
    void preservesExplicitPathsAndUsesNestedTypeWithoutGuessingMissingMetadata() throws Exception {
        CubeApiTreeNode root = new CubeApiTreeNode();
        CubeApiTreeNode flag = point("run_flag", null);
        flag.setPath("/external/flag");
        flag.setData(new ObjectMapper().readTree("{\"valueType\":\"bool\"}"));
        CubeApiTreeNode unknown = point("unknown", null);
        root.getChildren().put("flag", flag);
        root.getChildren().put("unknown", unknown);

        Map<String, PointHistoryMetadata> result = converter.convert(root, "/plant/cp1/process",
                "/plant/cp1/process/", Arrays.asList(
                        new PointConfig("/EXTERNAL/FLAG", PointDataType.BOOLEAN),
                        new PointConfig("unknown", PointDataType.FLOAT)), "cube");

        assertThat(result.get("/EXTERNAL/FLAG").getPath()).isEqualTo("/external/flag");
        assertThat(result.get("/EXTERNAL/FLAG").getValueType()).isEqualTo("bool");
        assertThat(result.get("/plant/cp1/process/unknown").getValueType()).isNull();
        assertThat(result.get("/plant/cp1/process/unknown").getValid()).isNull();
    }

    @Test
    void preservesCubeValidityFlagWithoutRejectingHistoryMetadata() throws Exception {
        CubeApiTreeNode root = new CubeApiTreeNode();
        // Cube 仍可能返回 valid=false；保留原值，但不据此拒绝历史映射。
        CubeApiTreeNode speed = new ObjectMapper().readValue(
                "{\"code\":\"speed\",\"cubeKey\":\"cp1_plc_speed\",\"valid\":false}", CubeApiTreeNode.class);
        root.getChildren().put("speed", speed);

        Map<String, PointHistoryMetadata> result = converter.convert(root, "/plant/cp1/process",
                "/plant/cp1/process/", Collections.singletonList(
                        new PointConfig("speed", PointDataType.FLOAT)), "cube");

        assertThat(result.get("/plant/cp1/process/speed").getCode()).isEqualTo("speed");
        assertThat(result.get("/plant/cp1/process/speed").getValid()).isFalse();
    }

    @Test
    void rejectsInvalidDatabaseNames() {
        assertThatThrownBy(() -> converter.convert(new CubeApiTreeNode(), "/plant", "/plant/",
                Collections.emptyList(), "archive.table")).hasMessageContaining("数据库名无效");
    }

    private CubeApiTreeNode point(String code, String valueType) {
        CubeApiTreeNode node = new CubeApiTreeNode();
        node.setCode(code);
        node.setCubeKey("cp1_plc_" + code);
        node.setValueType(valueType);
        return node;
    }
}
