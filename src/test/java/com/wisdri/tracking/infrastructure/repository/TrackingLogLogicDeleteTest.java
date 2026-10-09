package com.wisdri.tracking.infrastructure.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.injector.DefaultSqlInjector;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.wisdri.tracking.infrastructure.dto.postgres.coiler.QmCoilerLogEntity;
import com.wisdri.tracking.infrastructure.dto.postgres.product.QmDcRepeatProdNoLogEntity;
import com.wisdri.tracking.infrastructure.dto.postgres.shear.QmShearLogEntity;
import com.wisdri.tracking.infrastructure.dto.postgres.trimming.QmTrimmingLogEntity;
import com.wisdri.tracking.infrastructure.service.postgres.coiler.QmCoilerLogMapper;
import com.wisdri.tracking.infrastructure.service.postgres.product.RepeatProdNoMapper;
import com.wisdri.tracking.infrastructure.service.postgres.shear.QmShearLogMapper;
import com.wisdri.tracking.infrastructure.service.postgres.trimming.QmTrimmingLogMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证四张记录表的 MyBatis-Plus 自动 SQL 确实过滤逻辑删除行。 */
class TrackingLogLogicDeleteTest {
    @Test
    void generatedQueriesExcludeDeletedRows() {
        assertSelectFiltersDeleted(QmCoilerLogMapper.class, QmCoilerLogEntity.class);
        assertSelectFiltersDeleted(QmShearLogMapper.class, QmShearLogEntity.class);
        assertSelectFiltersDeleted(RepeatProdNoMapper.class, QmDcRepeatProdNoLogEntity.class);
        assertSelectFiltersDeleted(QmTrimmingLogMapper.class, QmTrimmingLogEntity.class);
    }

    @Test
    void generatedUpdatesOnlyChangeActiveRows() {
        MybatisConfiguration configuration = configuration(QmCoilerLogMapper.class, QmCoilerLogEntity.class);
        QmCoilerLogEntity coiler = new QmCoilerLogEntity();
        coiler.setId(1L);
        coiler.setCoilerMethod("99");
        assertThat(sql(configuration, QmCoilerLogMapper.class, "updateById",
                Collections.singletonMap("et", coiler)))
                .contains("deleted=0");

        configuration = configuration(QmTrimmingLogMapper.class, QmTrimmingLogEntity.class);
        QmTrimmingLogEntity trimming = new QmTrimmingLogEntity();
        trimming.setTrimmingLength("2.5");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("et", trimming);
        parameters.put("ew", Wrappers.<QmTrimmingLogEntity>lambdaUpdate()
                .eq(QmTrimmingLogEntity::getInMatNo, "C001"));
        assertThat(sql(configuration, QmTrimmingLogMapper.class, "update", parameters))
                .contains("deleted=0");
    }

    @Test
    void historicalMaximumQueryIncludesDeletedRows() throws NoSuchMethodException {
        Select select = RepeatProdNoMapper.class
                .getMethod("selectHistoricalMaxRepeatProdNo", String.class, String.class)
                .getAnnotation(Select.class);
        assertThat(String.join(" ", select.value()).toLowerCase())
                .contains("max(in_mat_repeat_prod_no)")
                .doesNotContain("deleted");
    }

    private void assertSelectFiltersDeleted(Class<?> mapper, Class<?> entity) {
        MybatisConfiguration configuration = configuration(mapper, entity);
        TableInfo tableInfo = TableInfoHelper.getTableInfo(entity);
        assertThat(tableInfo.isWithLogicDelete()).isTrue();
        assertThat(tableInfo.getLogicDeleteFieldInfo().getLogicNotDeleteValue()).isEqualTo("0");
        assertThat(tableInfo.getLogicDeleteFieldInfo().getLogicDeleteValue()).isEqualTo("1");
        assertThat(sql(configuration, mapper, "selectById", 1L)).contains("deleted=0");
        // BaseMapper.selectOne 委托生成的 selectList SQL 执行查询。
        assertThat(sql(configuration, mapper, "selectList", Collections.singletonMap("ew",
                Wrappers.query()))).contains("deleted=0");
        assertThat(sql(configuration, mapper, "selectCount", Collections.singletonMap("ew",
                Wrappers.query()))).contains("deleted=0");
    }

    private MybatisConfiguration configuration(Class<?> mapper, Class<?> entity) {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        assistant.setCurrentNamespace(mapper.getName());
        TableInfoHelper.initTableInfo(assistant, entity);
        new DefaultSqlInjector().inspectInject(assistant, mapper);
        return configuration;
    }

    private String sql(MybatisConfiguration configuration, Class<?> mapper, String method, Object parameter) {
        return configuration.getMappedStatement(mapper.getName() + "." + method)
                .getBoundSql(parameter).getSql().toLowerCase().replaceAll("\\s+", "");
    }
}
