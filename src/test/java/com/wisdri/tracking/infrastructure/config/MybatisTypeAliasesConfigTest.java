package com.wisdri.tracking.infrastructure.config;

import com.wisdri.tracking.infrastructure.dto.postgres.abnormal.AbnormalDataEntity;
import org.apache.ibatis.type.TypeAliasRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MybatisTypeAliasesConfigTest {

    @Test
    void scansOnlyPostgresEntitiesWithoutDomainAliasConflicts() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yaml"));
        Properties properties = yaml.getObject();
        String aliasesPackage = properties == null
                ? null
                : properties.getProperty("mybatis-plus.type-aliases-package");

        assertEquals("com.wisdri.tracking.infrastructure.dto.postgres", aliasesPackage);
        TypeAliasRegistry registry = new TypeAliasRegistry();
        assertDoesNotThrow(() -> registry.registerAliases(aliasesPackage));
        assertEquals(AbnormalDataEntity.class, registry.resolveAlias("AbnormalDataEntity"));
    }
}
