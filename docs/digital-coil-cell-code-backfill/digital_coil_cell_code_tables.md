# 安阳生产环境 digital_coil 的 cell_code 待加列表

## 核对范围

- 数据来源：DBX 的“安阳生产环境 / TDengine”连接，数据库 `digital_coil`；仅执行了元数据查询。
- 匹配规则：表名以 `cp1_`、`cbl1_`、`fcl1_`、`dcl1_`、`zrm1_`、`baf1_` 或 `csl1_` 开头。
- 查询结果：共 93 张普通表；每张表都有 `ts TIMESTAMP`、`pass_no INT`，均尚无 `cell_code` 普通列。
- 建议新增 `cell_code VARCHAR(255)` 普通列，与现有时序表的字符串列长度保持一致；它是逐行值，不应定义为 TAG。
- 本清单是本次查询时的快照。新增表或更改表结构后，须重新核对再执行补全脚本。

## 按机组统计

| 机组前缀 | 待加列表数 |
| --- | ---: |
| CP1 | 14 |
| CBL1 | 1 |
| FCL1 | 36 |
| DCL1 | 39 |
| ZRM1 | 1 |
| BAF1 | 1 |
| CSL1 | 1 |
| **合计** | **93** |

## 表名清单

### CP1（14 张）

- `cp1_process_air_drying1`
- `cp1_process_air_drying2`
- `cp1_process_furnace_throat`
- `cp1_process_nof`
- `cp1_process_picking_tank1`
- `cp1_process_picking_tank2`
- `cp1_process_picking_tank3`
- `cp1_process_rcf1`
- `cp1_process_rcf2`
- `cp1_process_rinsing`
- `cp1_process_rtf`
- `cp1_process_sf`
- `cp1_process_silicon_removal`
- `cp1_process_water_spray`

### CBL1（1 张）

- `cbl1_process_default`

### FCL1（36 张）

- `fcl1_process_air_dry2`
- `fcl1_process_br3br4`
- `fcl1_process_coater1`
- `fcl1_process_coater2`
- `fcl1_process_cpc1`
- `fcl1_process_cpc10`
- `fcl1_process_cpc11`
- `fcl1_process_cpc12`
- `fcl1_process_cpc13`
- `fcl1_process_cpc2`
- `fcl1_process_cpc3`
- `fcl1_process_cpc4`
- `fcl1_process_cpc5`
- `fcl1_process_cpc6`
- `fcl1_process_cpc7`
- `fcl1_process_cpc7a`
- `fcl1_process_cpc8`
- `fcl1_process_cpc9`
- `fcl1_process_cpc_phf`
- `fcl1_process_ctf`
- `fcl1_process_dsc`
- `fcl1_process_dust_removal`
- `fcl1_process_epc1`
- `fcl1_process_epc2`
- `fcl1_process_esc`
- `fcl1_process_fjc`
- `fcl1_process_furnace_entry`
- `fcl1_process_iron_loss`
- `fcl1_process_phf`
- `fcl1_process_pickling_tank`
- `fcl1_process_rinsing`
- `fcl1_process_rjc`
- `fcl1_process_rtf`
- `fcl1_process_sf`
- `fcl1_process_thickness`
- `fcl1_process_water_scrub_tank`

### DCL1（39 张）

- `dcl1_process_alkali_spray`
- `dcl1_process_atc`
- `dcl1_process_coater1`
- `dcl1_process_coater2`
- `dcl1_process_cpc1`
- `dcl1_process_cpc10`
- `dcl1_process_cpc11`
- `dcl1_process_cpc2`
- `dcl1_process_cpc3`
- `dcl1_process_cpc4`
- `dcl1_process_cpc5`
- `dcl1_process_cpc6`
- `dcl1_process_cpc7`
- `dcl1_process_cpc8`
- `dcl1_process_cpc9`
- `dcl1_process_df`
- `dcl1_process_dsc`
- `dcl1_process_elec_ultrasonic`
- `dcl1_process_epc1`
- `dcl1_process_epc2`
- `dcl1_process_esc`
- `dcl1_process_furnace_entry`
- `dcl1_process_hef`
- `dcl1_process_hot_air_driver`
- `dcl1_process_hot_water_spray`
- `dcl1_process_if`
- `dcl1_process_rjc`
- `dcl1_process_rtf1`
- `dcl1_process_rtf2`
- `dcl1_process_sep1`
- `dcl1_process_sep2`
- `dcl1_process_sep3`
- `dcl1_process_sep4`
- `dcl1_process_sep5`
- `dcl1_process_sf1`
- `dcl1_process_sf2`
- `dcl1_process_sf3`
- `dcl1_process_sjc`
- `dcl1_process_water_brush`

### ZRM1（1 张）

- `zrm1_process_default`

### BAF1（1 张）

- `baf1_batch`

### CSL1（1 张）

- `csl1_process_default`

## 补全前置条件

本清单记录的是当时查询到的 93 张表。当前[分段重算脚本](README-script.md)仅处理其中 `cp1_`、`cbl1_`、`dcl1_`、`fcl1_`、`zrm1_` 和 `csl1_` 开头的 92 张表，且要求每张表具有普通列 `coil_no`、`pass_no` 和 `cell_code`；`baf1_batch` 不参与。正式执行前须重新核对生产表结构并按脚本说明先做小范围验证。
