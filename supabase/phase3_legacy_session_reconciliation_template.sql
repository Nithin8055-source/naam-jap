-- Optional: run AFTER the Phase 3 migration in a reviewed maintenance window.
-- Fill only session_id/record_id pairs verified by a human from source data.
-- This links a pre-existing record without changing its count/date/note.
-- Do not infer matches from equal counts alone.
begin;

create temporary table phase3_reviewed_session_record_map (
    session_id uuid primary key,
    record_id uuid not null unique
) on commit drop;

-- Example only; replace with reviewed UUIDs, or leave empty.
-- insert into phase3_reviewed_session_record_map values
-- ('<verified-session-uuid>', '<verified-record-uuid>');

do $$
begin
    if exists (
        select 1
        from phase3_reviewed_session_record_map m
        join public.jap_sessions s on s.id = m.session_id
        join public.jap_records r on r.id = m.record_id
        where r.user_id is distinct from s.user_id
           or r.naam_id is distinct from s.naam_id
           or r.count is distinct from s.count
           or r.session_id is not null
           or s.ended_at is null
    ) then
        raise exception 'Reviewed mapping does not match a completed session and an unlinked record with the same owner, naam and count.';
    end if;
    if exists (
        select 1 from phase3_reviewed_session_record_map m
        left join public.jap_sessions s on s.id = m.session_id
        left join public.jap_records r on r.id = m.record_id
        where s.id is null or r.id is null
    ) then
        raise exception 'Reviewed mapping contains a missing session or record.';
    end if;
end
$$;

update public.jap_records r
set session_id = m.session_id
from phase3_reviewed_session_record_map m
where r.id = m.record_id;

-- For sessions confirmed to have NO existing record, add them to the count
-- ledger. Choose record_date after reviewing history; timezone was not stored.
create temporary table phase3_reviewed_unrepresented_sessions (
    session_id uuid primary key,
    record_date date not null
) on commit drop;

-- Example only; replace with reviewed UUID/date rows, or leave empty.
-- insert into phase3_reviewed_unrepresented_sessions values
-- ('<verified-session-uuid>', date '2026-01-31');

do $$
begin
    if exists (
        select 1 from phase3_reviewed_unrepresented_sessions m
        left join public.jap_sessions s on s.id = m.session_id
        where s.id is null or s.ended_at is null or s.count < 1
           or exists (select 1 from public.jap_records r where r.session_id = s.id)
    ) then
        raise exception 'Unrepresented-session list contains a missing, active, empty, or already-linked session.';
    end if;
end
$$;

insert into public.jap_records (user_id, naam_id, count, record_date, notes, session_id)
select s.user_id, s.naam_id, s.count, m.record_date, null, s.id
from phase3_reviewed_unrepresented_sessions m
join public.jap_sessions s on s.id = m.session_id;

-- Inspect affected rows, then COMMIT only after review. Use ROLLBACK otherwise.
select s.id as session_id, r.id as record_id, r.user_id, r.naam_id,
       r.count, r.record_date, r.session_id
from phase3_reviewed_session_record_map m
join public.jap_sessions s on s.id = m.session_id
join public.jap_records r on r.id = m.record_id
union all
select s.id, r.id, r.user_id, r.naam_id, r.count, r.record_date, r.session_id
from phase3_reviewed_unrepresented_sessions m
join public.jap_sessions s on s.id = m.session_id
join public.jap_records r on r.session_id = s.id;

-- COMMIT;
rollback;
