-- 安阳生产环境 TDengine / digital_coil 的 cell_code 历史数据补全脚本。
-- 表名来自生产环境元数据快照；只包含 93 张已存在的普通表。
-- 前置条件：先为清单中的每张表添加 cell_code VARCHAR(255) 普通列；本文件不执行 ALTER TABLE。
-- 只补 cell_code 为 NULL 或空字符串的行。pass_no 为 NULL 时按 1；非负道次补足至少三位。
-- 使用原 ts 写回目标表。本文件没有时间上界，不能直接整文件执行。
-- 先按操作手册在新程序上线后确定每张表的最早时间与固定截止时间，再补上时间条件并分批执行。
-- 此脚本仅保存到仓库，未在远程数据库执行。

INSERT INTO digital_coil.`baf1_batch` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'BAF1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`baf1_batch`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cbl1_process_default` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'CBL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`cbl1_process_default`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_air_drying1` (ts, cell_code)
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
FROM digital_coil.`cp1_process_air_drying1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_air_drying2` (ts, cell_code)
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
FROM digital_coil.`cp1_process_air_drying2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_furnace_throat` (ts, cell_code)
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
FROM digital_coil.`cp1_process_furnace_throat`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_nof` (ts, cell_code)
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
FROM digital_coil.`cp1_process_nof`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_picking_tank1` (ts, cell_code)
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
FROM digital_coil.`cp1_process_picking_tank1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_picking_tank2` (ts, cell_code)
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
FROM digital_coil.`cp1_process_picking_tank2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_picking_tank3` (ts, cell_code)
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
FROM digital_coil.`cp1_process_picking_tank3`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_rcf1` (ts, cell_code)
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
FROM digital_coil.`cp1_process_rcf1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_rcf2` (ts, cell_code)
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
FROM digital_coil.`cp1_process_rcf2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_rinsing` (ts, cell_code)
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
FROM digital_coil.`cp1_process_rinsing`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_rtf` (ts, cell_code)
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
FROM digital_coil.`cp1_process_rtf`
WHERE cell_code IS NULL OR cell_code = '';

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
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_silicon_removal` (ts, cell_code)
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
FROM digital_coil.`cp1_process_silicon_removal`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`cp1_process_water_spray` (ts, cell_code)
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
FROM digital_coil.`cp1_process_water_spray`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`csl1_process_default` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'CSL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`csl1_process_default`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_alkali_spray` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_alkali_spray`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_atc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_atc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_coater1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_coater1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_coater2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_coater2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc10` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc10`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc11` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc11`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc3` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc3`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc4` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc4`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc5` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc5`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc6` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc6`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc7` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc7`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc8` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc8`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_cpc9` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_cpc9`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_df` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_df`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_dsc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_dsc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_elec_ultrasonic` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_elec_ultrasonic`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_epc1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_epc1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_epc2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_epc2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_esc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_esc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_furnace_entry` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_furnace_entry`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_hef` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_hef`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_hot_air_driver` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_hot_air_driver`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_hot_water_spray` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_hot_water_spray`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_if` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_if`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_rjc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_rjc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_rtf1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_rtf1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_rtf2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_rtf2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sep1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sep1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sep2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sep2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sep3` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sep3`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sep4` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sep4`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sep5` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sep5`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sf1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sf1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sf2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sf2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sf3` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sf3`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_sjc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_sjc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`dcl1_process_water_brush` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'DCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`dcl1_process_water_brush`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_air_dry2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_air_dry2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_br3br4` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_br3br4`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_coater1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_coater1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_coater2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_coater2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc10` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc10`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc11` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc11`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc12` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc12`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc13` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc13`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc3` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc3`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc4` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc4`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc5` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc5`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc6` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc6`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc7` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc7`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc7a` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc7a`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc8` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc8`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc9` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc9`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_cpc_phf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_cpc_phf`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_ctf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_ctf`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_dsc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_dsc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_dust_removal` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_dust_removal`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_epc1` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_epc1`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_epc2` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_epc2`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_esc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_esc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_fjc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_fjc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_furnace_entry` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_furnace_entry`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_iron_loss` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_iron_loss`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_phf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_phf`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_pickling_tank` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_pickling_tank`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_rinsing` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_rinsing`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_rjc` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_rjc`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_rtf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_rtf`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_sf` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_sf`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_thickness` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_thickness`
WHERE cell_code IS NULL OR cell_code = '';

INSERT INTO digital_coil.`fcl1_process_water_scrub_tank` (ts, cell_code)
SELECT
    ts,
    CONCAT(
        'FCL1',
        CASE
            WHEN pass_no IS NULL THEN '001'
            WHEN pass_no >= 0 AND pass_no < 10 THEN CONCAT('00', CAST(pass_no AS VARCHAR(16)))
            WHEN pass_no >= 10 AND pass_no < 100 THEN CONCAT('0', CAST(pass_no AS VARCHAR(16)))
            ELSE CAST(pass_no AS VARCHAR(16))
        END
    )
FROM digital_coil.`fcl1_process_water_scrub_tank`
WHERE cell_code IS NULL OR cell_code = '';

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
WHERE cell_code IS NULL OR cell_code = '';
