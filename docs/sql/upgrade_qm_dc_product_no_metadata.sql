-- 仅用于将此前的三列 qm_dc_product_no 升级为带通用元数据字段的结构。
-- 保留原有生产次数；旧记录的 create_user 使用固定标识，其他时间及人员字段保持为空。
begin;

alter table public.qm_dc_product_no add column id bigint;
with numbered as (
    select unit_code, in_mat_no,
           row_number() over (order by unit_code, in_mat_no) as generated_id
      from public.qm_dc_product_no
)
update public.qm_dc_product_no target
   set id = numbered.generated_id
  from numbered
 where target.unit_code = numbered.unit_code
   and target.in_mat_no = numbered.in_mat_no;

alter table public.qm_dc_product_no
    add column deleted integer default 0,
    add column create_time timestamp(6),
    add column create_org bigint,
    add column create_user bigint default 1831666618627928065,
    add column update_time timestamp(6),
    add column update_user bigint;

alter table public.qm_dc_product_no alter column id set not null;
alter table public.qm_dc_product_no drop constraint qm_dc_product_no_pkey;
alter table public.qm_dc_product_no add constraint qm_dc_product_no_pkey primary key (id);
alter table public.qm_dc_product_no alter column unit_code drop not null;
alter table public.qm_dc_product_no alter column in_mat_no drop not null;
alter table public.qm_dc_product_no alter column in_mat_product_no drop not null;
alter table public.qm_dc_product_no
    drop constraint if exists qm_dc_product_no_in_mat_product_no_check;
alter table public.qm_dc_product_no
    add constraint uq_qm_dc_product_no_unit_mat unique (unit_code, in_mat_no);

comment on column public.qm_dc_product_no.id is '主键；旧记录迁移分配，新增记录使用 MyBatis-Plus 雪花算法生成';
comment on column public.qm_dc_product_no.deleted is '删除标记，0表示有效；计数记录不应删除或重置';
comment on column public.qm_dc_product_no.create_time is '记录首次创建时间，数据库会话时区的本地时间';
comment on column public.qm_dc_product_no.create_org is '创建组织标识，当前取号流程无来源时为空';
comment on column public.qm_dc_product_no.create_user is '固定创建人标识，默认1831666618627928065';
comment on column public.qm_dc_product_no.update_time is '最近一次递增时间，数据库会话时区的本地时间';
comment on column public.qm_dc_product_no.update_user is '最近一次递增的固定更新人标识，值为1831666618627928065';

commit;
