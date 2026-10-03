# 重复生产次数与字段命名切换

## 1. 新计数表与历史校验

尚未建表时执行 [`sql/qm_dc_repeat_prod_no_log.sql`](sql/qm_dc_repeat_prod_no_log.sql) 创建 `qm_dc_repeat_prod_no_log`；已创建旧名 `qm_dc_repeat_prod_no` 的环境，在停写后执行[原位改名脚本](sql/rename_qm_dc_repeat_prod_no_to_log.sql)，保留历史行。旧 `qm_dc_product_no` 保留供核对，程序不再读写。上线前按跟踪历史逐次补入新表，确保每个 `(unit_code, in_mat_no)` 的次数从 1 连续到真实最大值。历史卷的识别口径和补全由部署方确认，不可只把旧表当前最大值复制为一行，否则会丢失逐次记录。工作区另有未跟踪的 `docs/qm-dc-repeat-prod-no-backfill/backfill_qm_dc_repeat_prod_no.sql`，其中包含 `unit_code` 大写处理，与本次范围不符，本流程不可直接执行该脚本。

在 PostgreSQL 校验缺号和重复号：

```sql
select unit_code, in_mat_no, count(*) as records,
       min(in_mat_repeat_prod_no) as first_no,
       max(in_mat_repeat_prod_no) as latest_no
from public.qm_dc_repeat_prod_no_log
group by unit_code, in_mat_no
having min(in_mat_repeat_prod_no) <> 1
    or count(*) <> max(in_mat_repeat_prod_no);
```

结果须为空；另与已确认的历史跟踪卷清单逐卷核对最大次数。唯一约束阻止同一机组、同一卷、同一次数的重复行。历史补全时 `unit_code` 保持来源值，本次不做大小写变更。

## 2. PostgreSQL 跟踪记录字段

停止相关写入后，记录三张表旧列的非空数与抽样值，执行 [`sql/rename_repeat_prod_no_pg_logs.sql`](sql/rename_repeat_prod_no_pg_logs.sql)。原位改名保留列的数据类型和值。执行后逐表检查 `in_mat_repeat_prod_no` 的非空数与抽样值，与切换前一致；确认旧列已消失。回退字段名可使用 [`sql/rollback_repeat_prod_no_pg_logs.sql`](sql/rollback_repeat_prod_no_pg_logs.sql)，须先停用新程序。

## 3. TDengine 过程与铁损表

本仓库通过时序网关写入 `digital_coil` 中的 `*_process_*` 和 `*_ironloss_*` 表。上线前重新列出实际表并检查每张表的类型、`in_mat_prod_no`、`repeat_prod_no` 与时间戳列：

```sql
select table_name, table_type, col_name, col_type
from information_schema.ins_columns
where db_name = 'digital_coil'
  and (table_name like '%_process_%' or table_name like '%_ironloss_%')
  and col_name in ('ts', 'in_mat_prod_no', 'repeat_prod_no')
order by table_name, col_name;
```

2026-10-03 只读核对安阳生产环境 TDengine `3.3.8.21` 时，符合上述表名与旧列条件的 **92 张均为普通表**，旧列类型 `INT`，目标列尚不存在。这是一次快照，执行时须重新核对表清单并确认没有不同命名的过程或铁损表。逐表保存旧列非空数、最早及最晚时间戳，并导出含 `ts` 和所有业务列的历史值供回退与逐行核对。

先在同版本测试环境验证 `ALTER TABLE digital_coil.<表名> RENAME COLUMN in_mat_prod_no repeat_prod_no;`。目标环境支持且重新核对清单后，在停止相关写入期间逐条执行 [`sql/rename_repeat_prod_no_tdengine.sql`](sql/rename_repeat_prod_no_tdengine.sql)，记录每条执行结果，再逐表核对行数、非空数及抽样 `(ts, 值)`。若现场表清单有变化，先修订脚本。TDengine [表结构文档](https://docs.tdengine.com/tdengine-sql/ddl/table/)列出了普通表改列名语法；不能仅凭文档代替目标版本验证。回退时在停止新写入后反向改名。

若目标版本或目标表不支持原位改名，先为每张表添加 `repeat_prod_no INT`，保留旧列。按 `ts` 分批读取**完整原行**，将旧列值填入新列，并以原时间戳回写完整行；不使用当前时间戳，也不只写新列。每批核对 `ts`、旧值和新值，最后逐表核对非空数、值不一致数和总行数。确认全部历史值一致后再切换程序。旧列待观察期结束后另行清理，不在本次切换时删除。

## 4. 切换顺序

1. 完成新计数表历史补全与最大次数核对，并准备 PG/TDengine 备份与回退数据。
2. 停止 status、coiler、shear、trimming、process、ironloss 的相关写入；确认消息消费已暂停。
3. 执行并核对 PG 字段改名和 TDengine 字段迁移。
4. 一起部署两个计划的程序改动，恢复消息消费与写入；抽查新卷逐次记录、旧 Redis 状态和在途消息读取，以及 PG/TDengine 新字段写入。

旧 `qm_dc_product_no` 在切换后仍保留，但不再参与取号。PG 取号和 Redis 状态保存不在同一事务内；进程在分配成功后、状态保存前退出，重启可能对同一物理上卷再次分配，现场需要按跟踪事件核对该异常。
