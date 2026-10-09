-- 为剪切、切边、开卷卷取日志增加加工单元代码，并一次性回填各表历史记录。
-- 三张表在同一事务内迁移；仅首次增列时回填，重复执行不会覆盖新写入的空值。
-- 历史剪切和切边表没有道次列，使用默认序号 001；卷取表使用 0～999 的道次，空道次按 1 处理。
begin;

do $$
begin
    if not exists (select 1 from information_schema.columns
                   where table_schema = 'public'
                     and table_name = 'qm_dc_shear_log'
                     and column_name = 'cell_code') then
        alter table public.qm_dc_shear_log add column cell_code varchar(50);
        update public.qm_dc_shear_log
        set cell_code = upper(unit_code) || '001'
        where nullif(btrim(unit_code), '') is not null;
    end if;

    if not exists (select 1 from information_schema.columns
                   where table_schema = 'public'
                     and table_name = 'qm_dc_trimming_log'
                     and column_name = 'cell_code') then
        alter table public.qm_dc_trimming_log add column cell_code varchar(50);
        update public.qm_dc_trimming_log
        set cell_code = upper(unit_code) || '001'
        where nullif(btrim(unit_code), '') is not null;
    end if;

    if not exists (select 1 from information_schema.columns
                   where table_schema = 'public'
                     and table_name = 'qm_dc_coiler_log'
                     and column_name = 'cell_code') then
        alter table public.qm_dc_coiler_log add column cell_code varchar(50);
        update public.qm_dc_coiler_log
        set cell_code = upper(unit_code) || lpad(coalesce(pass_no, 1)::text, 3, '0')
        where nullif(btrim(unit_code), '') is not null
          and coalesce(pass_no, 1) between 0 and 999;
    end if;
end $$;

comment on column public.qm_dc_shear_log.cell_code is '加工单元代码：大写机组代码加三位道次或默认序号';
comment on column public.qm_dc_trimming_log.cell_code is '加工单元代码：大写机组代码加三位道次或默认序号';
comment on column public.qm_dc_coiler_log.cell_code is '加工单元代码：大写机组代码加三位道次或默认序号';

commit;
