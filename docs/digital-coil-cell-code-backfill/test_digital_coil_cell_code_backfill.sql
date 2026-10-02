-- 安阳生产环境 TDengine / digital_coil 的小范围补全测试。
-- 前置条件：这两张表均已添加 cell_code VARCHAR(255) 普通列。
-- 时间段来自 2026-10-03 的只读查询：当时每段各有 10 行。
-- 先查看待补行数，再仅在需要实际测试写入时执行 INSERT，最后查询结果。
-- 本文件只保存到仓库，未在远程数据库执行写入。

-- cp1_process_sf：样本的 pass_no 为 NULL，预期 cell_code 为 CP1001。
SELECT COUNT(*) AS pending_rows
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-10-02 16:42:27.000'
  AND ts < '2026-10-02 16:42:37.000'
  AND (cell_code IS NULL OR cell_code = '');

INSERT INTO digital_coil.`cp1_process_sf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'CP1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-10-02 16:42:27.000'
  AND ts < '2026-10-02 16:42:37.000'
  AND (cell_code IS NULL OR cell_code = '');

SELECT ts, pass_no, cell_code
FROM digital_coil.`cp1_process_sf`
WHERE ts >= '2026-10-02 16:42:27.000'
  AND ts < '2026-10-02 16:42:37.000'
ORDER BY ts;

-- zrm1_process_default：样本的 pass_no 为 6，预期 cell_code 为 ZRM1006。
SELECT COUNT(*) AS pending_rows
FROM digital_coil.`zrm1_process_default`
WHERE ts >= '2026-10-02 16:42:04.000'
  AND ts < '2026-10-02 16:42:14.000'
  AND (cell_code IS NULL OR cell_code = '');

INSERT INTO digital_coil.`zrm1_process_default` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'ZRM1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`zrm1_process_default`
WHERE ts >= '2026-10-02 16:42:04.000'
  AND ts < '2026-10-02 16:42:14.000'
  AND (cell_code IS NULL OR cell_code = '');

SELECT ts, pass_no, cell_code
FROM digital_coil.`zrm1_process_default`
WHERE ts >= '2026-10-02 16:42:04.000'
  AND ts < '2026-10-02 16:42:14.000'
ORDER BY ts;
