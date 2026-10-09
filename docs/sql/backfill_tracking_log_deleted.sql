-- 上线启用 MyBatis-Plus 逻辑删除前执行：历史 NULL 行原本代表有效记录。
-- 只修正 NULL，不改变已明确标记为 0 或 1 的记录。
begin;

update public.qm_dc_coiler_log set deleted = 0 where deleted is null;
update public.qm_dc_shear_log set deleted = 0 where deleted is null;
update public.qm_dc_trimming_log set deleted = 0 where deleted is null;

commit;
