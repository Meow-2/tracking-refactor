-- 钢卷生产次数计数表；首次 por 上卷插入 1，此后按机组和钢卷原子递增。
-- 本脚本只建空表，历史次数回填另行处理。
-- 业务字段由应用校验；联合唯一约束保留给 ON CONFLICT 原子递增使用。
create table public.qm_dc_product_no
(
    id                bigint not null primary key,
    unit_code         varchar(50),
    in_mat_no         varchar(50),
    in_mat_product_no integer,
    deleted           integer default 0,
    create_time       timestamp(6),
    create_org        bigint,
    create_user       bigint default 1831666618627928065,
    update_time       timestamp(6),
    update_user       bigint,
    constraint uq_qm_dc_product_no_unit_mat unique (unit_code, in_mat_no)
);

comment on table public.qm_dc_product_no is '钢卷生产次数计数表';
comment on column public.qm_dc_product_no.id is '主键，使用 MyBatis-Plus 雪花算法生成';
comment on column public.qm_dc_product_no.unit_code is '机组编码，与钢卷号共同确定计数对象';
comment on column public.qm_dc_product_no.in_mat_no is '入口钢卷号，在机组内确定计数对象';
comment on column public.qm_dc_product_no.in_mat_product_no is '已分配的当前生产次数，首次为1';
comment on column public.qm_dc_product_no.deleted is '删除标记，0表示有效；计数记录不应删除或重置';
comment on column public.qm_dc_product_no.create_time is '记录首次创建时间，数据库会话时区的本地时间';
comment on column public.qm_dc_product_no.create_org is '创建组织标识，当前取号流程无来源时为空';
comment on column public.qm_dc_product_no.create_user is '固定创建人标识，默认1831666618627928065';
comment on column public.qm_dc_product_no.update_time is '最近一次递增时间，数据库会话时区的本地时间';
comment on column public.qm_dc_product_no.update_user is '最近一次递增的固定更新人标识，值为1831666618627928065';
