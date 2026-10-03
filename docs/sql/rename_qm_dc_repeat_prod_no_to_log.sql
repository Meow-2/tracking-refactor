-- 仅适用于已创建旧名表的环境；停写后执行，原位改名并保留历史行。
-- 尚未建表的环境直接执行 qm_dc_repeat_prod_no_log.sql，不执行此脚本。
begin;
alter table public.qm_dc_repeat_prod_no rename to qm_dc_repeat_prod_no_log;
alter table public.qm_dc_repeat_prod_no_log
    rename constraint uq_qm_dc_repeat_prod_no_unit_mat_repeat
    to uq_qm_dc_repeat_prod_no_log_unit_mat_repeat;
commit;
