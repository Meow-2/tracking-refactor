-- 跟踪服务停写后执行：将 public 下带 unit_code 的 qm_dc_ 表及 abnormal_data 的历史值统一大写。
-- 仅改 unit_code；不创建列、不改卷号或重复生产次数。任一表触发约束冲突时事务整体回滚。
begin;

do $$
declare
    target_table record;
    collision_count bigint;
    updated_rows bigint;
    remaining_count bigint;
begin
    for target_table in
        select namespace.nspname as schema_name, relation.relname as table_name
          from pg_class relation
          join pg_namespace namespace on namespace.oid = relation.relnamespace
          join pg_attribute column_info on column_info.attrelid = relation.oid
         where namespace.nspname = 'public'
           and (left(relation.relname, 6) = 'qm_dc_' or relation.relname = 'abnormal_data')
           and relation.relkind in ('r', 'p')
           and not relation.relispartition
           and column_info.attname = 'unit_code'
           and column_info.attnum > 0
           and not column_info.attisdropped
         order by relation.relname
    loop
        -- 两张计数表以机组代码为唯一键的一部分；大小写合并前先阻止业务键碰撞。
        if target_table.table_name in ('qm_dc_product_no', 'qm_dc_repeat_prod_no',
                                       'qm_dc_repeat_prod_no_log') then
            if target_table.table_name = 'qm_dc_product_no' then
                execute format('select count(*) from ('
                               || 'select upper(unit_code), in_mat_no from %I.%I '
                               || 'where unit_code is not null and in_mat_no is not null '
                               || 'group by upper(unit_code), in_mat_no having count(*) > 1) duplicates',
                               target_table.schema_name, target_table.table_name)
                    into collision_count;
            else
                execute format('select count(*) from ('
                               || 'select upper(unit_code), in_mat_no, in_mat_repeat_prod_no from %I.%I '
                               || 'where unit_code is not null and in_mat_no is not null '
                               || 'group by upper(unit_code), in_mat_no, in_mat_repeat_prod_no '
                               || 'having count(*) > 1) duplicates',
                               target_table.schema_name, target_table.table_name)
                    into collision_count;
            end if;
            if collision_count > 0 then
                raise exception 'unit_code 大写后存在重复业务键：%.%，冲突组数=%',
                    target_table.schema_name, target_table.table_name, collision_count;
            end if;
        end if;

        execute format('update %I.%I set unit_code = upper(unit_code) '
                       || 'where unit_code is not null and unit_code <> upper(unit_code)',
                       target_table.schema_name, target_table.table_name);
        get diagnostics updated_rows = row_count;
        execute format('select count(*) from %I.%I '
                       || 'where unit_code is not null and unit_code <> upper(unit_code)',
                       target_table.schema_name, target_table.table_name)
            into remaining_count;
        if remaining_count > 0 then
            raise exception 'unit_code 大写校验失败：%.%，剩余行数=%',
                target_table.schema_name, target_table.table_name, remaining_count;
        end if;
        raise notice 'unit_code 大写完成：%.%，更新行数=%',
            target_table.schema_name, target_table.table_name, updated_rows;
    end loop;
end $$;

commit;
