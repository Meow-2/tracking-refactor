-- 数字钢卷剪切算法 3.0 结果表的全新建表脚本。
-- 本脚本仅适用于尚未创建 qm_dc_shear_log 的数据库，不迁移旧表，也不删除任何数据。
create table public.qm_dc_shear_log
(
    id                          bigint not null primary key,
    unit_code                   varchar(50),
    cell_code                   varchar(50),
    in_mat_no                   varchar(50),
    in_mat_repeat_prod_no              varchar(50),
    shear_type                  varchar(50),
    shear_type_name             varchar(50),
    shear_length                numeric(12, 5),
    set_number                  integer,
    shear_time                  timestamp,
    shear_device_code           varchar(50),
    in_mat_device_code          varchar(50),
    in_mat_device_color_no      varchar(50),
    in_mat_device_remain_length numeric(12, 5),
    in_mat_device_max_length    numeric(12, 5),
    shear_device_coil_no        varchar(50),
    shear_device_color_no       varchar(50),
    shear_device_remain_length  numeric(12, 5),
    shear_device_max_length     numeric(12, 5),
    cut_no                      integer,
    shear_no                    integer,
    create_user                 bigint default '1831666618627928065'::bigint,
    update_user                 bigint,
    create_time                 timestamp,
    update_time                 timestamp,
    create_org                  bigint,
    deleted                     integer default 0
);

comment on table public.qm_dc_shear_log is '数字钢卷剪切过程记录';
comment on column public.qm_dc_shear_log.id is '剪切记录主键';
comment on column public.qm_dc_shear_log.unit_code is '产生剪切事件的机组代码';
comment on column public.qm_dc_shear_log.cell_code is '加工单元代码：大写机组代码加三位道次或默认序号';
comment on column public.qm_dc_shear_log.in_mat_no is '剪切归属物料钢卷号';
comment on column public.qm_dc_shear_log.in_mat_repeat_prod_no is '剪切归属物料钢卷的生产次数';
comment on column public.qm_dc_shear_log.shear_type is '当前剪切配置的类型编码';
comment on column public.qm_dc_shear_log.shear_type_name is '触发设备代码与 head、slice 或 tail 的组合名称';
comment on column public.qm_dc_shear_log.shear_length is '本刀剪切长度，单位与机组长度点位一致';
comment on column public.qm_dc_shear_log.set_number is '切头或切尾设定刀数/片数，分切记录为空';
comment on column public.qm_dc_shear_log.shear_time is '触发剪切事件的帧时间';
comment on column public.qm_dc_shear_log.shear_device_code is '触发剪切事件的设备代码';
comment on column public.qm_dc_shear_log.in_mat_device_code is '剪切归属物料设备代码';
comment on column public.qm_dc_shear_log.in_mat_device_color_no is '剪切归属物料设备颜色号';
comment on column public.qm_dc_shear_log.in_mat_device_remain_length is '剪切归属物料设备剩余长度';
comment on column public.qm_dc_shear_log.in_mat_device_max_length is '剪切归属物料设备最大长度';
comment on column public.qm_dc_shear_log.shear_device_coil_no is '触发剪切设备当前钢卷号';
comment on column public.qm_dc_shear_log.shear_device_color_no is '触发剪切设备当前颜色号';
comment on column public.qm_dc_shear_log.shear_device_remain_length is '触发剪切设备剩余长度';
comment on column public.qm_dc_shear_log.shear_device_max_length is '触发剪切设备最大长度';
comment on column public.qm_dc_shear_log.cut_no is '当前剪切组内刀次';
comment on column public.qm_dc_shear_log.shear_no is '当前物料对应的剪切组号';
comment on column public.qm_dc_shear_log.create_user is '创建记录的用户标识';
comment on column public.qm_dc_shear_log.update_user is '最后更新记录的用户标识';
comment on column public.qm_dc_shear_log.create_time is '记录创建时间';
comment on column public.qm_dc_shear_log.update_time is '记录最后更新时间';
comment on column public.qm_dc_shear_log.create_org is '创建记录的组织标识';
comment on column public.qm_dc_shear_log.deleted is '逻辑删除标记，0 表示有效';
