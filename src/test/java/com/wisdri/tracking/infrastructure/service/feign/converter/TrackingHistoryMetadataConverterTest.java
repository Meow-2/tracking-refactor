package com.wisdri.tracking.infrastructure.service.feign.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wisdri.tracking.domain.model.config.process.ProcessTrackingConfig;
import com.wisdri.tracking.domain.model.config.status.StatusTrackingConfig;
import com.wisdri.tracking.domain.model.history.PointHistoryMetadata;
import com.wisdri.tracking.domain.model.history.TrackingHistoryMetadata;
import com.wisdri.tracking.domain.model.tracking.TrackingType;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeNode;
import com.wisdri.tracking.infrastructure.dto.feign.cube.CubeApiTreeResponse;
import com.wisdri.tracking.infrastructure.service.feign.converter.process.ProcessCubeApiTrackingConfigConverter;
import com.wisdri.tracking.infrastructure.service.feign.converter.status.StatusCubeApiTrackingConfigConverter;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TrackingHistoryMetadataConverterTest {
    private static final String PREFIX = "/aygg_tracking/zrm1/status/tracking/";
    private final ObjectMapper mapper = new ObjectMapper();
    private final TrackingHistoryMetadataConverter converter = new TrackingHistoryMetadataConverter();

    TrackingHistoryMetadataConverterTest() {
        ReflectionTestUtils.setField(converter, "converters", Arrays.asList(
                new StatusCubeApiTrackingConfigConverter(), new ProcessCubeApiTrackingConfigConverter()));
        ReflectionTestUtils.setField(converter, "pointMetadataConverter", new PointHistoryMetadataConverter());
    }

    @Test
    void mapsSubtreePointsUsingPhysicalCodesAndPreservesConfiguration() throws Exception {
        CubeApiTreeResponse tree = tree();
        CubeApiTreeNode thickness = points(tree).getChildren().get("exit_thickness_pv");
        thickness.setCode("actual_thickness");
        thickness.setCubeKey("zrm1_plc_actual_thickness");
        thickness.setValueType("double");
        thickness.setUnit("mm");
        TrackingHistoryMetadata historyMetadata = convert(tree);

        assertThat(historyMetadata.getTrackingConfig().getUnitCode()).isEqualTo("zrm1");
        assertThat(statusConfig(historyMetadata).getTracking().getRolling().getQualityMinSpeed()).isEqualByComparingTo("10");
        assertThat(statusConfig(historyMetadata).getTracking().getRolling().getDirectReverse()).isFalse();
        assertThat(historyMetadata.getPointMetadata()).hasSize(10);
        PointHistoryMetadata metadata = historyMetadata.getPointMetadata().get(PREFIX + "exit_thickness_pv");
        assertThat(metadata.getDatabase()).isEqualTo("cube");
        assertThat(metadata.getObjectName()).isEqualTo("zrm1_plc");
        assertThat(metadata.getCode()).isEqualTo("actual_thickness");
        assertThat(metadata.getValueType()).isEqualTo("double");
        assertThat(metadata.getUnit()).isEqualTo("mm");
        assertThat(metadata.getValid()).isTrue();
    }

    @Test
    void selectsUnitAndStatusIgnoringCaseAndRejectsDuplicateCandidates() throws Exception {
        CubeApiTreeResponse tree = tree();
        CubeApiTreeNode unit = tree.getRoots().remove("zrm1");
        unit.getChildren().put("STATUS", unit.getChildren().remove("status"));
        tree.getRoots().put("ZRM1", unit);
        assertThat(convert(tree).getPointMetadata()).hasSize(10);
        tree.getRoots().put("zrm1", unit);
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("机组节点重复");
    }

    @Test
    void reportsMissingUnitTypeAndBaseConfiguration() throws Exception {
        CubeApiTreeResponse tree = tree();
        assertThatThrownBy(() -> converter.convert(tree, "cp1", TrackingType.STATUS, "/aygg_tracking"))
                .hasMessageContaining("机组节点不存在");
        tree.getRoots().get("zrm1").getChildren().remove("status");
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("跟踪类型节点不存在");
        CubeApiTreeResponse withoutConfig = tree();
        status(withoutConfig).setData(null);
        assertThatThrownBy(() -> convert(withoutConfig)).hasMessageContaining("缺少 status 基础配置");
    }

    @Test
    void followsSubtreePointListAndRejectsDuplicatedAndInvalidMappings() throws Exception {
        CubeApiTreeResponse tree = tree();
        CubeApiTreeNode pass = points(tree).getChildren().remove("pass_no_pv");
        assertThat(convert(tree).getPointMetadata()).hasSize(9);
        points(tree).getChildren().put("pass_no_pv", pass);
        points(tree).getChildren().put("duplicate", pass);
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("历史点位路径重复");
        points(tree).getChildren().remove("duplicate");
        pass.setValid(false);
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("Cube 中无效");
        pass.setValid(true);
        pass.setCubeKey("wrong_suffix");
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("cubeKey 与 code 不匹配");
        pass.setCubeKey("bad.table_pass_no_pv");
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("对象或字段名无效");
    }

    @Test
    void collectsAllPointsIncludingUnreferencedPointsAndPreservesNestedValueType() throws Exception {
        CubeApiTreeResponse tree = tree();
        CubeApiTreeNode direction = points(tree).getChildren().get("rolling_direction");
        direction.setPath(null);
        direction.setValueType(null);
        direction.setData(mapper.readTree("{\"valueType\":\"bool\"}"));
        CubeApiTreeNode extra = new CubeApiTreeNode();
        extra.setItemType(2);
        extra.setCode("extra_value");
        extra.setCubeKey("zrm1_plc_extra_value");
        points(tree).getChildren().put("extra", extra);
        TrackingHistoryMetadata metadata = convert(tree);
        assertThat(metadata.getPointMetadata()).hasSize(11);
        assertThat(metadata.getPointMetadata().get(PREFIX + "rolling_direction").getValueType()).isEqualTo("bool");
        assertThat(metadata.getPointMetadata().get(PREFIX + "extra").getCode()).isEqualTo("extra_value");
    }

    @Test
    void keepsEachMetadataIndependentAndWorksWhenRealtimeOutputIsDisabled() throws Exception {
        CubeApiTreeResponse tree = tree();
        ((com.fasterxml.jackson.databind.node.ObjectNode) status(tree).getData().path("default")).put("enable", false);
        ((com.fasterxml.jackson.databind.node.ObjectNode) status(tree).getData().path("default")
                .path("tracking").path("rolling")).put("quality_output_enabled", false);
        TrackingHistoryMetadata first = convert(tree);
        statusConfig(first).getTracking().getRolling().setDirectReverse(true);
        points(tree).getChildren().get("pass_no_pv").setCubeKey("changed_pass_no_pv");
        TrackingHistoryMetadata second = convert(tree);
        assertThat(statusConfig(second).getTracking().getRolling().getDirectReverse()).isFalse();
        assertThat(first.getPointMetadata().get(PREFIX + "pass_no_pv").getObjectName()).isEqualTo("zrm1_plc");
        assertThat(second.getPointMetadata().get(PREFIX + "pass_no_pv").getObjectName()).isEqualTo("changed");
        assertThatThrownBy(() -> first.getPointMetadata().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void metadataLoadingDoesNotRequireStatusPassCompletionFields() throws Exception {
        CubeApiTreeResponse tree = tree();
        com.fasterxml.jackson.databind.node.ObjectNode rolling = (com.fasterxml.jackson.databind.node.ObjectNode)
                status(tree).getData().path("default").path("tracking").path("rolling");
        rolling.put("quality_output_enabled", false);
        rolling.remove("out_thickness_point");
        rolling.remove("quality_min_speed");
        assertThat(convert(tree).getPointMetadata()).hasSize(10);
    }

    private StatusTrackingConfig statusConfig(TrackingHistoryMetadata metadata) {
        return (StatusTrackingConfig) metadata.getTrackingConfig();
    }

    @Test
    void convertsProcessConfigurationAndIncludesItsSegmentPointsOnly() {
        CubeApiTreeNode process = configuredNode();
        CubeApiTreeNode tech = new CubeApiTreeNode();
        tech.setItemType(1);
        tech.setCode("tech_directory");
        CubeApiTreeNode segment = configuredNode();
        ((com.fasterxml.jackson.databind.node.ObjectNode) segment.getData().path("default")).remove("enable");
        ((com.fasterxml.jackson.databind.node.ObjectNode) segment.getData().path("default")).put("code", "sf");
        segment.getChildren().put("speed", physicalPoint("speed"));
        tech.getChildren().put("sf", segment);
        process.getChildren().put("tech", tech);
        CubeApiTreeResponse tree = processTree(process);
        tree.getRoots().get("cp1").getChildren().put("status", physicalPoint("unrelated"));

        TrackingHistoryMetadata metadata = converter.convert(tree, "CP1", TrackingType.PROCESS, "/plant");
        ProcessTrackingConfig config = (ProcessTrackingConfig) metadata.getTrackingConfig();
        assertThat(config.getTrackingType()).isEqualTo(TrackingType.PROCESS);
        assertThat(config.getSegments()).hasSize(1);
        assertThat(config.getSegments().get(0).getPoints()).hasSize(1);
        assertThat(metadata.getPointMetadata()).containsOnlyKeys("/plant/cp1/process/tech/sf/speed");
    }

    @Test
    void locatesNestedConfigurationAndExcludesPointsOutsideItsSubtree() {
        CubeApiTreeNode type = new CubeApiTreeNode();
        CubeApiTreeNode config = configuredNode();
        config.setPath("/actual/config");
        config.getChildren().put("speed", physicalPoint("speed"));
        type.getChildren().put("settings", config);
        type.getChildren().put("outside", physicalPoint("outside"));
        TrackingHistoryMetadata metadata = converter.convert(
                processTree(type), "cp1", TrackingType.PROCESS, "/plant");
        assertThat(metadata.getPointMetadata()).containsOnlyKeys("/actual/config/speed");
    }

    @Test
    void rejectsAmbiguousNestedConfigurationsAndUnsupportedTypes() {
        CubeApiTreeNode type = new CubeApiTreeNode();
        type.getChildren().put("first", configuredNode());
        type.getChildren().put("second", configuredNode());
        assertThatThrownBy(() -> converter.convert(processTree(type), "cp1", TrackingType.PROCESS, "/plant"))
                .hasMessageContaining("配置节点重复");

        CubeApiTreeResponse tree = processTree(configuredNode());
        tree.getRoots().get("cp1").getChildren().put("coiler", configuredNode());
        assertThatThrownBy(() -> converter.convert(tree, "cp1", TrackingType.COILER, "/plant"))
                .hasMessageContaining("未注册");
    }

    @Test
    void rejectsMalformedConfigurationAndPropagatesTypeConverterValidation() throws Exception {
        CubeApiTreeNode malformed = new CubeApiTreeNode();
        malformed.setData(mapper.createObjectNode().put("default", "invalid"));
        assertThatThrownBy(() -> converter.convert(processTree(malformed), "cp1", TrackingType.PROCESS, "/plant"))
                .hasMessageContaining("必须是对象");
        CubeApiTreeResponse tree = tree();
        ((com.fasterxml.jackson.databind.node.ObjectNode) status(tree).getData().path("default")
                .path("tracking")).put("sample_count", 1);
        assertThatThrownBy(() -> convert(tree)).hasMessageContaining("sample_count");
    }

    private CubeApiTreeNode configuredNode() {
        CubeApiTreeNode node = new CubeApiTreeNode();
        node.setItemType(1);
        node.setData(mapper.createObjectNode().set("default", mapper.createObjectNode().put("enable", true)));
        return node;
    }

    private CubeApiTreeNode physicalPoint(String code) {
        CubeApiTreeNode point = new CubeApiTreeNode();
        point.setItemType(2);
        point.setCode(code);
        point.setCubeKey("cp1_plc_" + code);
        point.setValueType("float");
        return point;
    }

    private CubeApiTreeResponse processTree(CubeApiTreeNode process) {
        CubeApiTreeNode unit = new CubeApiTreeNode();
        unit.getChildren().put("process", process);
        CubeApiTreeResponse tree = new CubeApiTreeResponse();
        tree.getRoots().put("cp1", unit);
        return tree;
    }

    private TrackingHistoryMetadata convert(CubeApiTreeResponse tree) {
        return converter.convert(tree, "ZRM1", TrackingType.STATUS, "/aygg_tracking");
    }

    private CubeApiTreeNode status(CubeApiTreeResponse tree) {
        return tree.getRoots().get("zrm1").getChildren().get("status");
    }

    private CubeApiTreeNode points(CubeApiTreeResponse tree) {
        return status(tree).getChildren().get("tracking");
    }

    private CubeApiTreeResponse tree() throws Exception {
        JsonNode config = mapper.readTree(Files.readAllBytes(Paths.get("docs/config/ZRM1/status.json")));
        CubeApiTreeNode status = new CubeApiTreeNode();
        status.setData(mapper.createObjectNode().set("default", config));
        CubeApiTreeNode tracking = new CubeApiTreeNode();
        for (String name : Arrays.asList("pass_no_pv", "mill_speed_pv", "rolling_direction", "exit_thickness_pv",
                "por_coil_no", "por_coil_length", "entry_tr_coil_no", "entry_right_tr_coil_length",
                "exit_tr_coil_no", "exit_left_tr_coil_length")) {
            CubeApiTreeNode point = new CubeApiTreeNode();
            point.setPath(PREFIX + name);
            point.setName(name);
            point.setCode(name);
            point.setCubeKey("zrm1_plc_" + name);
            point.setValueType(name.contains("coil_no") ? "string" : name.equals("rolling_direction") ? "bool" : "float");
            point.setValid(true);
            tracking.getChildren().put(name, point);
        }
        status.getChildren().put("tracking", tracking);
        CubeApiTreeNode unit = new CubeApiTreeNode();
        unit.getChildren().put("status", status);
        CubeApiTreeResponse tree = new CubeApiTreeResponse();
        tree.getRoots().put("zrm1", unit);
        return tree;
    }
}
