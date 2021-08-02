create table if not exists document
(
    id            bigserial primary key,
    sid           varchar,
    sn            varchar,
    rsn           varchar,
    data          jsonb,
    modified      timestamp,
    poll_created  timestamp,
    poll_modified timestamp
);

comment on table document is '单据报文';
comment on column document.sid is '门店编码';
comment on column document.sn is '单据编号';
comment on column document.sn is '关联单据编号';
comment on column document.data is '单据信息';
comment on column document.modified is '单据的更新时间，依赖这个时间更新数据';

create table if not exists retried_document
(
    id      bigserial primary key,
    did     bigint,
    retries int2 default 0,
    created timestamp
);

comment on table retried_document is 'Doc重试表，重试3次后将发送无法处理数据的异常';
comment on column retried_document.did is 'Document ID';
comment on column retried_document.retries is '重试次数';
comment on column retried_document.created is '创建时间';

create table if not exists job
(
    id      serial primary key,
    name    varchar,
    enabled bool
);

comment on table job is '任务信息';
comment on column job.name is '任务名称';

create table if not exists platform_job
(
    id        serial primary key,
    pid       varchar,
    jid       int,
    job_class varchar,
    enabled   bool,
    props     jsonb
);

comment on table platform_job is '平台任务信息';
comment on column platform_job.pid is '平台编码';
comment on column platform_job.jid is '任务编码';
comment on column platform_job.job_class is '任务类名';
comment on column platform_job.props is '任务配置';

create table if not exists store_job
(
    id       serial primary key,
    sid      varchar,
    jid      int,
    end_time timestamp,
    enabled  bool,
    props    jsonb
);

comment on table store_job is '门店任务信息';
comment on column store_job.sid is '门店编码';
comment on column store_job.jid is '任务编码';
comment on column store_job.end_time is '任务最后一次执行时间';
comment on column store_job.props is '任务配置';

create table if not exists akc_activity
(
    id         varchar primary key,
    name       varchar,
    sid        varchar,
    begin_time timestamp,
    end_time   timestamp,
    data       jsonb,
    completed  bool
);

comment on table akc_activity is '爱库存活动信息';
comment on column akc_activity.name is '活动名称';
comment on column akc_activity.sid is '门店编码';
comment on column akc_activity.begin_time is '活动开始时间';
comment on column akc_activity.end_time is '活动结束时间';
comment on column akc_activity.data is '活动详情';
comment on column akc_activity.completed is '同步是否完成';

create table if not exists kafka_offset
(
    id        serial primary key,
    topic     varchar not null,
    partition int     not null,
    "offset"  bigint  not null
)