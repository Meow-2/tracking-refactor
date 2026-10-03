-- 在跟踪服务停止写入时执行；原位改名，保留三张 PostgreSQL 跟踪记录表的数据。
begin;
alter table public.qm_dc_coiler_log rename column in_mat_prod_no to in_mat_repeat_prod_no;
alter table public.qm_dc_shear_log rename column in_mat_prod_no to in_mat_repeat_prod_no;
alter table public.qm_dc_trimming_log rename column in_mat_prod_no to in_mat_repeat_prod_no;
commit;
