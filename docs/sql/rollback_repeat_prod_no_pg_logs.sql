-- 仅用于新程序停止写入后的字段名回退；不会删除或覆盖历史记录。
begin;
alter table public.qm_dc_coiler_log rename column in_mat_repeat_prod_no to in_mat_prod_no;
alter table public.qm_dc_shear_log rename column in_mat_repeat_prod_no to in_mat_prod_no;
alter table public.qm_dc_trimming_log rename column in_mat_repeat_prod_no to in_mat_prod_no;
commit;
