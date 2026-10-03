-- 钢卷重复生产次数逐次记录表；旧 qm_dc_product_no 表保留，不再供跟踪程序读写。
-- 部署新程序前由历史跟踪数据填充已有卷，程序只负责后续新上卷的逐次插入。
create table public.qm_dc_repeat_prod_no_log
(
    id                    bigint not null primary key,
    unit_code             varchar(50) not null,
    in_mat_no             varchar(50) not null,
    in_mat_repeat_prod_no integer not null check (in_mat_repeat_prod_no > 0),
    deleted               integer not null default 0,
    create_time           timestamp(6),
    create_org            bigint,
    create_user           bigint default 1831666618627928065,
    update_time           timestamp(6),
    update_user           bigint,
    constraint uq_qm_dc_repeat_prod_no_log_unit_mat_repeat
        unique (unit_code, in_mat_no, in_mat_repeat_prod_no)
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
