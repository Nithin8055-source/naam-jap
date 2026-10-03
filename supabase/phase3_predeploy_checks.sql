-- Read-only Phase 3 checks. They return schema and candidate row metadata but
-- do not modify database state.

-- Required: exactly one primary-key column, jap_sessions.id.
select c.conname as primary_key_name,
       array_agg(a.attname order by k.ordinality) as primary_key_columns
from pg_constraint c
cross join lateral unnest(c.conkey) with ordinality as k(attnum, ordinality)
join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
where c.conrelid = 'public.jap_sessions'::regclass and c.contype = 'p'
group by c.conname;

-- These results must be empty before creating invariant indexes.
select user_id, count(*) as default_rows
from public.naam_types where is_default
group by user_id having count(*) > 1;

select user_id, count(*) as active_sessions
from public.jap_sessions where ended_at is null
group by user_id having count(*) > 1;

select id, count(*) as duplicate_ids
from public.jap_records group by id having count(*) > 1;

-- Ambiguous legacy candidates, not automatic dedupe instructions. The
-- migration counts jap_records only, so these cannot cause a session+record
-- double count. Review them before manually linking legacy records to sessions.
select s.id as session_id, r.id as record_id, s.user_id, s.naam_id,
       s.count, s.ended_at, r.record_date
from public.jap_sessions s
join public.jap_records r
  on r.user_id = s.user_id
 and r.naam_id = s.naam_id
 and r.count = s.count
 and r.record_date between
     ((coalesce(s.ended_at, s.started_at) at time zone 'UTC')::date - 1)
     and ((coalesce(s.ended_at, s.started_at) at time zone 'UTC')::date + 1)
where s.ended_at is not null and s.count > 0
order by s.user_id, s.ended_at;

-- Completed sessions without a linked record are intentionally not auto-added
-- by the migration. Export these for the reviewed reconciliation template.
select s.id as session_id, s.user_id, s.naam_id, s.count,
       s.started_at, s.ended_at
from public.jap_sessions s
where s.ended_at is not null and s.count > 0
order by s.user_id, s.ended_at;

-- Multiple goal rows are preserved. Runtime logic uses the latest created_at,
-- then highest UUID, as the effective goal.
select user_id, count(*) as goal_rows, max(created_at) as latest_created_at
from public.daily_goals
group by user_id having count(*) > 1;
