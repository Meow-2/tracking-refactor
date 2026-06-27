# JsonUtils 上海时区时间展示实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 让 `JsonUtils.toPrettyJson(Object)` 将 `Instant` 按 `Asia/Shanghai` 输出带 `+08:00` 偏移量的 ISO-8601 字符串。

**架构：** 保持通用 `decimalPreservingMapperBuilder()` 和调用方传入的 `ObjectMapper` 行为不变，只为 `toPrettyJson(Object)` 创建的默认 mapper 注册上海时区 `Instant` 序列化器。内部时间和 Unix 毫秒时间戳不变。

**技术栈：** Java 8、Jackson Java Time、JUnit 5、AssertJ、Maven

---

## 文件结构

- 创建：`src/test/java/com/wisdri/tracking/common/utils/JsonUtilsTest.java`，验证默认 pretty JSON 的 `Instant` 展示时区。
- 修改：`src/main/java/com/wisdri/tracking/common/utils/JsonUtils.java`，注册上海时区 `Instant` 序列化器。

### 任务 1：按上海时区展示 Instant

**文件：**
- 创建：`src/test/java/com/wisdri/tracking/common/utils/JsonUtilsTest.java`
- 修改：`src/main/java/com/wisdri/tracking/common/utils/JsonUtils.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.wisdri.tracking.common.utils;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class JsonUtilsTest {
    @Test
    void prettyJsonDisplaysInstantInAsiaShanghai() {
        String json = JsonUtils.toPrettyJson(Collections.singletonMap(
                "receivedAt",
                Instant.parse("2026-06-27T00:29:40.323Z")
        ));

        assertThat(json).contains("\"receivedAt\" : \"2026-06-27T08:29:40.323+08:00\"");
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行：

```bash
mvn -Dtest=JsonUtilsTest test
```

预期：FAIL；实际输出仍包含 `2026-06-27T00:29:40.323Z`。

- [ ] **步骤 3：编写最少实现代码**

在 `JsonUtils` 中增加固定时区和默认展示 mapper：

```java
private static final ZoneId DISPLAY_ZONE = ZoneId.of("Asia/Shanghai");
private static final DateTimeFormatter DISPLAY_TIME_FORMATTER =
        DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(DISPLAY_ZONE);

private static ObjectMapper prettyJsonMapper() {
    JavaTimeModule javaTimeModule = new JavaTimeModule();
    javaTimeModule.addSerializer(Instant.class, new InstantSerializer(
            InstantSerializer.INSTANCE,
            false,
            false,
            DISPLAY_TIME_FORMATTER
    ));
    return decimalPreservingMapperBuilder()
            .addModule(javaTimeModule)
            .build();
}
```

并将：

```java
return toPrettyJson(decimalPreservingMapper(), value);
```

改为：

```java
return toPrettyJson(prettyJsonMapper(), value);
```

- [ ] **步骤 4：运行目标测试验证通过**

运行：

```bash
mvn -Dtest=JsonUtilsTest test
```

预期：1 项测试通过，0 failures。

- [ ] **步骤 5：运行回归验证**

运行：

```bash
mvn test
```

预期：新增测试通过；如果 Feign 超时配置的两项既有断言仍失败，记录其准确结果，不将其归因于本次时间格式修改。

- [ ] **步骤 6：提交实现**

```bash
git add src/main/java/com/wisdri/tracking/common/utils/JsonUtils.java src/test/java/com/wisdri/tracking/common/utils/JsonUtilsTest.java
git commit -m "fix(common):按上海时区展示JSON时间"
```
