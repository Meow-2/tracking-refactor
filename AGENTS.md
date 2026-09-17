# Repository Guidelines

## Project Structure & Module Organization

This repository contains a Java 8, Spring Boot 2.7 service built with Maven. Production code is under `src/main/java/com/wisdri/tracking` and follows layered packages:

- `controller`: HTTP endpoints.
- `application/usecase`: application orchestration and runners.
- `domain`: models, repository contracts, and tracking services.
- `infrastructure`: database, Redis, MQTT, RocketMQ, and Feign adapters.
- `common`: shared responses, exceptions, and utilities.

Runtime configuration and line-specific tracking JSON live in `src/main/resources`. Tests mirror production packages under `src/test/java`. Design notes and reference configuration are stored in `doc/`; generated build output belongs in `target/`.

## Build, Test, and Development Commands

- `mvn clean test` — compile the project and run the complete test suite.
- `mvn test -Dtest=ProcessTrackingAlgorithmImplTest` — run one test class.
- `mvn clean package` — run tests and build the executable JAR in `target/`.
- `mvn spring-boot:run -Plocal` — start the service with `application-local.yaml`.

Local startup requires the external services referenced by the selected profile, including PostgreSQL, Redis, MQTT, RocketMQ, and configured Feign endpoints.

## Coding Style & Naming Conventions

Use four-space indentation, UTF-8, and Java 8-compatible syntax. Keep classes in the existing layer and package boundaries. Name types in `PascalCase`, methods and fields in `camelCase`, constants in `UPPER_SNAKE_CASE`, and implementations with an `Impl` suffix where the repository already follows that pattern. Prefer small domain interfaces with infrastructure implementations, constructor/builders for model setup, and concise Chinese Javadoc or comments consistent with nearby code. No formatter or linter is configured; match surrounding imports and layout.

## Testing Guidelines

Tests use JUnit 5, AssertJ, Mockito, and Spring test utilities. Name test classes `*Test` and test methods after observable behavior, for example `calculateWritesSegmentCodeAndDisplayNameSeparately`. Add focused unit tests for domain behavior and adapter conversions. There is no configured coverage threshold; every behavior change should include regression coverage.

## Commit & Pull Request Guidelines

Follow the repository’s Conventional Commit pattern with a scope and concise Chinese summary, such as `fix(common):按上海时区展示JSON时间` or `feat(process):支持类型化点位配置`. Keep commits focused. Pull requests should explain the change, affected modules/configuration, and verification commands; link relevant issues and include request/response examples for API changes.

## Security & Configuration

Do not commit credentials or environment-specific endpoints. Put deploy-specific values in profile configuration or external environment settings, and keep `doc/config` examples synchronized with runtime configuration when schemas change.

## 外部环境访问约定

- 数据库连接和只读查询可通过 `dbx mcp` 完成。涉及 tracking 点位历史数据时，先通过 Cube 定位机组、跟踪类型和点位，确认 `cubeKey`、`code` 及 TDengine 对象名，再使用 DBX MCP 查询；仅执行 schema 读取和查询，不执行写入、更新或删除。
- SSH 连接可通过 `ssh mcp server` 完成。需要访问 Kubernetes 或 NFS 上的线上 tracking 服务日志时，使用已配置的 SSH MCP Server，并遵循只读原则，不删除、移动、覆盖或清理远端文件。
- [tracking-service-log-reader](.codex/skills/tracking-service-log-reader/SKILL.md) 用于按机组、跟踪类型和日期定位并读取线上 tracking 服务日志；查询前应确认 `unitCode`、`trackingType`、日志流和日期，并按上海时区处理日志归档文件。
- [tracking-point-history-reader](.codex/skills/tracking-point-history-reader/SKILL.md) 用于通过 Cube 定位 tracking 点位，并使用 DBX MCP 查询 TDengine 历史数据；查询时间范围使用左闭右开区间，大结果集必须区分分页结果和全量统计。
- 线上数据访问默认只读；不得在日志、查询结果或提交内容中输出密码、私钥、令牌及其他敏感凭据。
