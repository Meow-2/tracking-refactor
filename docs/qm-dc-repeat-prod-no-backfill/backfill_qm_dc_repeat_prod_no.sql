-- PostgreSQL 一次性迁移：处理 public 下所有以 qm_dc_ 开头的普通表和分区表。
-- 有 in_mat_prod_no 的表新增同类型的 in_mat_repeat_prod_no，并以源列覆盖回填；
-- 有 unit_code 的表将现有值统一转为大写。没有对应源列的表不新增目标列。
-- 请在跟踪服务停止写入时执行；若大写后触发唯一约束冲突，事务整体回滚。
-- 不要与 docs/sql/rename_repeat_prod_no_pg_logs.sql 对同一批表重复执行：该脚本是原位改名方案。
-- 目标环境须尚未创建 qm_dc_repeat_prod_no_log；旧 qm_dc_repeat_prod_no 的改名另见 docs/sql/rename_qm_dc_repeat_prod_no_to_log.sql。
begin;

-- 复用 docs/sql/qm_dc_repeat_prod_no_log.sql 的表结构，供新程序逐次记录重复生产序号。
create table public.qm_dc_repeat_prod_no_log
(
    id                    bigint not null primary key,
    unit_code             varchar(50),
    in_mat_no             varchar(50),
    in_mat_repeat_prod_no integer,
    deleted               integer default 0,
    create_time           timestamp(6),
    create_org            bigint,
    create_user           bigint default 1831666618627928065,
    update_time           timestamp(6),
    update_user           bigint
);

comment on table public.qm_dc_repeat_prod_no_log is '钢卷每次重复生产各存一条记录';
comment on column public.qm_dc_repeat_prod_no_log.id is '主键；在线插入使用 MyBatis-Plus 雪花算法生成';
comment on column public.qm_dc_repeat_prod_no_log.unit_code is '机组编码；与入口卷号共同确定计数范围，入库统一大写';
comment on column public.qm_dc_repeat_prod_no_log.in_mat_no is '入口钢卷号；同一机组内的计数对象';
comment on column public.qm_dc_repeat_prod_no_log.in_mat_repeat_prod_no is '该机组该卷的重复生产序号，从1开始，每次新上卷加1';
comment on column public.qm_dc_repeat_prod_no_log.deleted is '删除标记，0表示有效；逐次记录不得删除或复用序号';
comment on column public.qm_dc_repeat_prod_no_log.create_time is '本次记录创建时间；在线写入为应用本地时间，历史回填应使用真实生产时间';
comment on column public.qm_dc_repeat_prod_no_log.create_org is '创建组织标识；当前在线流程无来源时为空';
comment on column public.qm_dc_repeat_prod_no_log.create_user is '固定创建人标识，默认1831666618627928065';
comment on column public.qm_dc_repeat_prod_no_log.update_time is '保留通用元数据字段；逐次记录在线流程不更新旧行';
comment on column public.qm_dc_repeat_prod_no_log.update_user is '保留通用元数据字段；逐次记录在线流程不更新旧行';

do $$
declare
    target_table record;
    source_type text;
    has_repeat_column boolean;
    has_unit_code boolean;
begin
    for target_table in
        select namespace.nspname as schema_name, relation.relname as table_name,
               relation.oid as table_oid
          from pg_class relation
          join pg_namespace namespace on namespace.oid = relation.relnamespace
         where namespace.nspname = 'public'
           and left(relation.relname, 6) = 'qm_dc_'
           and relation.relkind in ('r', 'p')
           and not relation.relispartition
         order by relation.relname
    loop
        -- 使用源列的数据库类型，避免不同 qm_dc 表的列类型不一致时发生截断。
        select format_type(attribute.atttypid, attribute.atttypmod)
          into source_type
          from pg_attribute attribute
         where attribute.attrelid = target_table.table_oid
           and attribute.attname = 'in_mat_prod_no'
           and attribute.attnum > 0
           and not attribute.attisdropped;

        select exists (
            select 1 from pg_attribute attribute
             where attribute.attrelid = target_table.table_oid
               and attribute.attname = 'in_mat_repeat_prod_no'
               and attribute.attnum > 0
               and not attribute.attisdropped
        ) into has_repeat_column;

        if source_type is not null then
            if not has_repeat_column then
                execute format('alter table %I.%I add column in_mat_repeat_prod_no %s',
                               target_table.schema_name, target_table.table_name, source_type);
                execute format('comment on column %I.%I.in_mat_repeat_prod_no is %L',
                               target_table.schema_name, target_table.table_name,
                               '入口物料重复生产号；历史值从 in_mat_prod_no 复制');
            end if;

            execute format('update %I.%I set in_mat_repeat_prod_no = in_mat_prod_no '
                           || 'where in_mat_repeat_prod_no is distinct from in_mat_prod_no',
                           target_table.schema_name, target_table.table_name);
        end if;

        select exists (
            select 1 from pg_attribute attribute
             where attribute.attrelid = target_table.table_oid
               and attribute.attname = 'unit_code'
               and attribute.attnum > 0
               and not attribute.attisdropped
        ) into has_unit_code;

        if has_unit_code then
            execute format('update %I.%I set unit_code = upper(unit_code) '
                           || 'where unit_code is distinct from upper(unit_code)',
                           target_table.schema_name, target_table.table_name);
        end if;
    end loop;
end;
$$;

commit;
