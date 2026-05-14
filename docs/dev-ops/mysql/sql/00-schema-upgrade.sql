use agent_group;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column source_type varchar(32) not null default ''INIT_DATA'' comment ''来源类型'' after knowledge_version',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'source_type'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column source_name varchar(128) not null default ''初始化数据'' comment ''来源名称'' after source_type',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'source_name'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_document add column document_status varchar(32) not null default ''ENABLED'' comment ''文档状态'' after source_name',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_document'
    and column_name = 'document_status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
  select if(count(*) = 0,
    'alter table knowledge_fragment add column fragment_status varchar(32) not null default ''ENABLED'' comment ''片段状态'' after rank_no',
    'select 1')
  from information_schema.columns
  where table_schema = database()
    and table_name = 'knowledge_fragment'
    and column_name = 'fragment_status'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
