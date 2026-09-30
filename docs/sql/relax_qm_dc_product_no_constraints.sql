-- 已使用新表结构的环境，仅移除三个业务字段的非空及正数检查约束。
-- 保留 (unit_code, in_mat_no) 唯一约束，供原子递增的 ON CONFLICT 使用。
begin;

alter table public.qm_dc_product_no alter column unit_code drop not null;
alter table public.qm_dc_product_no alter column in_mat_no drop not null;
alter table public.qm_dc_product_no alter column in_mat_product_no drop not null;
alter table public.qm_dc_product_no
    drop constraint if exists qm_dc_product_no_in_mat_product_no_check;

commit;
