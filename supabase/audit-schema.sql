-- Read-only inventory for Naam Jap Phase 3 planning.
-- Run in the Supabase SQL Editor. This returns schema metadata only, never row data.

-- Public tables, including any existing profile table that may have another name.
select table_name
from information_schema.tables
where table_schema = 'public'
  and table_type = 'BASE TABLE'
order by table_name;

-- Columns for the existing practice tables and likely profile tables.
select table_name, ordinal_position, column_name, data_type, udt_name,
       is_nullable, column_default, character_maximum_length
from information_schema.columns
where table_schema = 'public'
  and (
    table_name in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or table_name ilike '%profile%'
    or table_name in ('accounts', 'user_accounts')
  )
order by table_name, ordinal_position;

-- Primary keys, unique constraints, foreign keys, and check constraints.
select tc.table_name, tc.constraint_name, tc.constraint_type,
       kcu.column_name, ccu.table_name as referenced_table,
       ccu.column_name as referenced_column,
       rc.update_rule, rc.delete_rule,
       cc.check_clause
from information_schema.table_constraints tc
left join information_schema.key_column_usage kcu
  on tc.constraint_catalog = kcu.constraint_catalog
 and tc.constraint_schema = kcu.constraint_schema
 and tc.constraint_name = kcu.constraint_name
 and tc.table_name = kcu.table_name
left join information_schema.constraint_column_usage ccu
  on tc.constraint_catalog = ccu.constraint_catalog
 and tc.constraint_schema = ccu.constraint_schema
 and tc.constraint_name = ccu.constraint_name
left join information_schema.referential_constraints rc
  on tc.constraint_catalog = rc.constraint_catalog
 and tc.constraint_schema = rc.constraint_schema
 and tc.constraint_name = rc.constraint_name
left join information_schema.check_constraints cc
  on tc.constraint_catalog = cc.constraint_catalog
 and tc.constraint_schema = cc.constraint_schema
 and tc.constraint_name = cc.constraint_name
where tc.constraint_schema = 'public'
  and (
    tc.table_name in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or tc.table_name ilike '%profile%'
    or tc.table_name in ('accounts', 'user_accounts')
  )
order by tc.table_name, tc.constraint_name, kcu.ordinal_position;

-- Index definitions, including unique indexes not represented as constraints.
select schemaname, tablename, indexname, indexdef
from pg_indexes
where schemaname = 'public'
  and (
    tablename in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or tablename ilike '%profile%'
    or tablename in ('accounts', 'user_accounts')
  )
order by tablename, indexname;

-- RLS enablement and forced-RLS status.
select n.nspname as schema_name, c.relname as table_name,
       c.relrowsecurity as rls_enabled,
       c.relforcerowsecurity as rls_forced
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relkind in ('r', 'p')
  and (
    c.relname in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or c.relname ilike '%profile%'
    or c.relname in ('accounts', 'user_accounts')
  )
order by c.relname;

-- Existing RLS policies; retain the USING and WITH CHECK expressions.
select schemaname, tablename, policyname, permissive, roles,
       cmd, qual as using_expression, with_check
from pg_policies
where schemaname = 'public'
  and (
    tablename in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or tablename ilike '%profile%'
    or tablename in ('accounts', 'user_accounts')
  )
order by tablename, policyname;

-- Table grants relevant to client roles.
select table_name, grantee, privilege_type, is_grantable
from information_schema.role_table_grants
where table_schema = 'public'
  and grantee in ('anon', 'authenticated', 'service_role')
  and (
    table_name in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
    or table_name ilike '%profile%'
    or table_name in ('accounts', 'user_accounts')
  )
order by table_name, grantee, privilege_type;

-- Trigger inventory for public application tables and auth.users.
-- Function bodies are intentionally omitted; inspect any trigger function separately if needed.
select trigger_schema, event_object_schema, event_object_table,
       trigger_name, action_timing, event_manipulation,
       action_orientation, action_statement
from information_schema.triggers
where (event_object_schema = 'public'
       and (event_object_table in ('naam_types', 'jap_sessions', 'jap_records', 'daily_goals')
            or event_object_table ilike '%profile%'
            or event_object_table in ('accounts', 'user_accounts')))
   or (event_object_schema = 'auth' and event_object_table = 'users')
order by event_object_schema, event_object_table, trigger_name, event_manipulation;
