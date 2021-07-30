-- 任务信息
insert into job(id, name, enabled)
values (1, '订单同步', true);
insert into job(id, name, enabled)
values (2, '退单同步', false);
insert into job(id, name, enabled)
values (3, '爱库存活动同步', true);

alter sequence job_id_seq restart with 4;

-- 平台任务关系
insert into platform_job(id, pid, jid, job_class, enabled)
values (1, 'taobao', 1, 'io.xxx.omni.oms.sync.impl.TbTradePorter', true);
insert into platform_job(id, pid, jid, job_class, enabled)
values (2, 'taobao', 2, 'io.xxx.omni.oms.sync.impl.TbRefundPorter', true);

insert into platform_job(id, pid, jid, job_class, enabled)
values (3, 'jingdong', 1, 'io.xxx.omni.oms.sync.impl.JdTradePorter', true);
insert into platform_job(id, pid, jid, job_class, enabled)
values (4, 'jingdong', 2, 'io.xxx.omni.oms.sync.impl.JdRefundPorter', true);

alter sequence platform_job_id_seq restart with 5;

-- 门店任务关系
insert into store_job(id, sid, jid, enabled, end_time)
values (1, '1001', 1, true, '2021-04-01 00:00:00');
insert into store_job(id, sid, jid, enabled, end_time)
values (2, '1001', 2, true, '2021-04-01 00:00:00');

insert into store_job(id, sid, jid, enabled, end_time)
values (3, '1002', 1, true, '2021-04-01 00:00:00');
insert into store_job(id, sid, jid, enabled, end_time)
values (4, '1002', 2, true, '2021-04-01 00:00:00');

insert into store_job(id, sid, jid, enabled, end_time)
values (5, '1003', 1, true, '2021-04-01 00:00:00');
insert into store_job(id, sid, jid, enabled, end_time)
values (6, '1003', 2, true, '2021-04-01 00:00:00');

insert into store_job(id, sid, jid, enabled, end_time)
values (7, '1004', 1, true, '2021-04-01 00:00:00');
insert into store_job(id, sid, jid, enabled, end_time)
values (8, '1004', 2, true, '2021-04-01 00:00:00');

alter sequence store_job_id_seq restart with 9;