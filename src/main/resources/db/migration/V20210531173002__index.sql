create unique index uk_document_sid_sn on document (sid, sn);

create index idx_document_sid on document (id);

create index idx_document_poll_created on document (poll_created);

create index idx_document_response_trade on document using gin (data jsonb_path_ops);

create unique index uk_platform_job_pid_jid on platform_job (pid, jid);

create index idx_store_job_sid_jid on store_job (sid, jid);

create index idx_document_retry on document_retry (did, retries);