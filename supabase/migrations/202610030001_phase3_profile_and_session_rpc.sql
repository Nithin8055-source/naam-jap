-- Naam Jap Phase 3 additive migration.
-- Based on the audited public tables:
--   naam_types(id, user_id, name, is_default, created_at)
--   jap_sessions(id, user_id, naam_id, count, started_at, ended_at, created_at)
--   jap_records(id, user_id, naam_id, count, record_date, notes, created_at, updated_at)
--   daily_goals(id, user_id, target_count, created_at)
-- Existing tables and their RLS policies are not altered here.
-- Back up the Supabase project first. This migration is transactional; an error
-- before COMMIT rolls back its changes. After COMMIT, use a forward repair or
-- restore from the project backup; do not drop the new ledger rows/tables.

begin;

-- Fail closed before changing anything if the verified live schema is different.
-- jap_sessions.id must be the single-column primary key for event/record FKs.
do $$
declare
    v_id_attnum smallint;
begin
    select a.attnum into v_id_attnum
    from pg_attribute a
    where a.attrelid = 'public.jap_sessions'::regclass
      and a.attname = 'id' and not a.attisdropped;
    if v_id_attnum is null or not exists (
        select 1 from pg_constraint c
        where c.conrelid = 'public.jap_sessions'::regclass
          and c.contype = 'p'
          and cardinality(c.conkey) = 1
          and (select k.attnum from unnest(c.conkey) as k(attnum)) = v_id_attnum
    ) then
        raise exception 'Preflight failed: public.jap_sessions.id must be its single-column primary key. No Phase 3 changes were applied.';
    end if;

    if exists (
        select 1 from public.naam_types
        where is_default
        group by user_id having count(*) > 1
    ) then
        raise exception 'Preflight failed: multiple default naam rows need explicit review before creating the unique-default index. No rows were changed.';
    end if;

    if exists (
        select 1 from public.jap_sessions
        where ended_at is null
        group by user_id having count(*) > 1
    ) then
        raise exception 'Preflight failed: users with multiple active sessions need explicit review before creating the one-active-session index. No rows were changed.';
    end if;

    if exists (
        select 1 from public.jap_records group by id having count(*) > 1
    ) then
        raise exception 'Preflight failed: duplicate jap_records.id values prevent safe retry keys. No rows were changed.';
    end if;

end
$$;

-- Ensure deleted auth users cannot leave orphaned app rows or recreate rows
-- with a still-unexpired JWT. Existing orphans stop the migration for review.
do $$
declare
    v_table text;
    v_rel regclass;
    v_has_orphan boolean;
    v_user_attnum smallint;
    v_auth_id_attnum smallint;
    v_constraint record;
    v_expected_name text;
begin
    select attnum into v_auth_id_attnum from pg_attribute
    where attrelid = 'auth.users'::regclass and attname = 'id' and not attisdropped;
    if v_auth_id_attnum is null or not exists (
        select 1 from pg_constraint c
        where c.conrelid = 'auth.users'::regclass and c.contype = 'p'
          and cardinality(c.conkey) = 1
          and (select k.attnum from unnest(c.conkey) as k(attnum)) = v_auth_id_attnum
    ) then
        raise exception 'Preflight failed: auth.users.id must be its single-column primary key. No Phase 3 changes were applied.';
    end if;
    foreach v_table in array array['naam_types', 'jap_sessions', 'jap_records', 'daily_goals'] loop
        execute format(
            'select exists (select 1 from public.%I t left join auth.users u on u.id = t.user_id where u.id is null)',
            v_table
        ) into v_has_orphan;
        if v_has_orphan then
            raise exception 'Preflight failed: public.% has user_id values with no auth.users row. Review them; no rows were changed.', v_table;
        end if;

        select attnum into v_user_attnum from pg_attribute
        where attrelid = format('public.%I', v_table)::regclass
          and attname = 'user_id' and not attisdropped;
        v_rel := format('public.%I', v_table)::regclass;
        v_expected_name := v_table || '_user_id_auth_users_fkey';
        select c.contype, c.confrelid, c.conkey, c.confkey, c.confdeltype,
               c.confupdtype, c.condeferrable, c.convalidated
          into v_constraint
          from pg_constraint c
         where c.conrelid = v_rel and c.conname = v_expected_name;
        if found then
            if v_constraint.contype <> 'f'
               or v_constraint.confrelid <> 'auth.users'::regclass
               or v_constraint.conkey <> array[v_user_attnum]::smallint[]
               or v_constraint.confkey <> array[v_auth_id_attnum]::smallint[]
               or v_constraint.confdeltype <> 'c'
               or v_constraint.confupdtype <> 'a'
               or v_constraint.condeferrable
               or not v_constraint.convalidated then
                raise exception 'Preflight failed: constraint public.% exists with a definition other than user_id REFERENCES auth.users(id) ON DELETE CASCADE. No changes were applied.', v_expected_name;
            end if;
        else
            select c.contype, c.confrelid, c.conkey, c.confkey, c.confdeltype,
                   c.confupdtype, c.condeferrable, c.convalidated
              into v_constraint
              from pg_constraint c
             where c.conrelid = v_rel and c.contype = 'f'
               and c.confrelid = 'auth.users'::regclass
               and c.conkey = array[v_user_attnum]::smallint[]
               and c.confkey = array[v_auth_id_attnum]::smallint[]
             limit 1;
            if found then
                if v_constraint.confdeltype <> 'c' or v_constraint.confupdtype <> 'a'
                   or v_constraint.condeferrable or not v_constraint.convalidated then
                    raise exception 'Preflight failed: public.% has an equivalent user_id/auth.users foreign key with incompatible behavior.', v_table;
                end if;
            else
                execute format(
                    'alter table public.%I add constraint %I foreign key (user_id) references auth.users(id) on delete cascade',
                    v_table, v_expected_name
                );
            end if;
        end if;
    end loop;
end
$$;

-- Parent key supports composite ownership FKs on linked children.
create unique index if not exists jap_sessions_id_user_id_unique
    on public.jap_sessions(id, user_id);
create unique index if not exists jap_sessions_one_active_per_user
    on public.jap_sessions(user_id) where ended_at is null;

create table if not exists public.profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null default '',
    email text,
    avatar_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'profiles'
          and policyname = 'Users read their own profile'
    ) then
        create policy "Users read their own profile" on public.profiles
            for select to authenticated using (auth.uid() = id);
    end if;
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'profiles'
          and policyname = 'Users update their own profile'
    ) then
        create policy "Users update their own profile" on public.profiles
            for update to authenticated
            using (auth.uid() = id) with check (auth.uid() = id);
    end if;
end
$$;

revoke all on public.profiles from anon, authenticated;
grant select on public.profiles to authenticated;
grant update (display_name, avatar_url) on public.profiles to authenticated;
grant select on public.naam_types, public.jap_sessions, public.jap_records, public.daily_goals to authenticated;

create or replace function public.naamjap_phase3_touch_profile_updated_at()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

do $$
begin
    if exists (select 1 from pg_trigger where tgrelid = 'public.profiles'::regclass
               and tgname = 'profiles_touch_updated_at' and not tgisinternal
               and tgfoid <> 'public.naamjap_phase3_touch_profile_updated_at()'::regprocedure) then
        raise exception 'Trigger name profiles_touch_updated_at is already used by another function.';
    end if;
    if not exists (select 1 from pg_trigger where tgrelid = 'public.profiles'::regclass
                   and tgname = 'profiles_touch_updated_at' and not tgisinternal) then
        execute 'create trigger profiles_touch_updated_at before update on public.profiles for each row execute function public.naamjap_phase3_touch_profile_updated_at()';
    end if;
end
$$;

create or replace function public.naamjap_phase3_create_profile_for_auth_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    insert into public.profiles (id, display_name, email, created_at, updated_at)
    values (
        new.id,
        coalesce(new.raw_user_meta_data ->> 'display_name', ''),
        new.email,
        coalesce(new.created_at, now()),
        now()
    )
    on conflict (id) do update
        set email = excluded.email,
            updated_at = now();
    return new;
end;
$$;

do $$
begin
    if exists (select 1 from pg_trigger where tgrelid = 'auth.users'::regclass
               and tgname = 'naamjap_phase3_auth_user_created_profile' and not tgisinternal
               and tgfoid <> 'public.naamjap_phase3_create_profile_for_auth_user()'::regprocedure) then
        raise exception 'Auth user profile trigger name is already used by another function.';
    end if;
    if not exists (select 1 from pg_trigger where tgrelid = 'auth.users'::regclass
                   and tgname = 'naamjap_phase3_auth_user_created_profile' and not tgisinternal) then
        execute 'create trigger naamjap_phase3_auth_user_created_profile after insert on auth.users for each row execute function public.naamjap_phase3_create_profile_for_auth_user()';
    end if;
    if exists (select 1 from pg_trigger where tgrelid = 'auth.users'::regclass
               and tgname = 'naamjap_phase3_auth_user_email_profile' and not tgisinternal
               and tgfoid <> 'public.naamjap_phase3_create_profile_for_auth_user()'::regprocedure) then
        raise exception 'Auth email profile trigger name is already used by another function.';
    end if;
    if not exists (select 1 from pg_trigger where tgrelid = 'auth.users'::regclass
                   and tgname = 'naamjap_phase3_auth_user_email_profile' and not tgisinternal) then
        execute 'create trigger naamjap_phase3_auth_user_email_profile after update of email on auth.users for each row execute function public.naamjap_phase3_create_profile_for_auth_user()';
    end if;
end
$$;

insert into public.profiles (id, display_name, email, created_at, updated_at)
select id, coalesce(raw_user_meta_data ->> 'display_name', ''), email,
       coalesce(created_at, now()), now()
from auth.users
on conflict (id) do update set email = excluded.email, updated_at = now();

-- Append-only operation receipts make repeated mobile retries idempotent and
-- provide pause/resume timestamps without adding columns to jap_sessions.
create table if not exists public.jap_session_events (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null,
    session_id uuid not null,
    operation_id uuid not null,
    sequence_no bigint not null,
    event_type text not null check (event_type in ('start', 'increment', 'undo', 'pause', 'resume', 'finish')),
    count_after bigint not null check (count_after >= 0),
    delta_count integer not null default 0 check (delta_count between -1 and 1),
    occurred_at timestamptz not null default now(),
    constraint jap_session_events_user_operation_unique unique (user_id, operation_id),
    constraint jap_session_events_session_sequence_unique unique (session_id, sequence_no)
);

-- Add the nullable ledger link before validating the session foreign keys.
alter table public.jap_records add column if not exists session_id uuid;

do $$
declare
    v_table text;
    v_rel regclass;
    v_constraint_name text;
    v_session_att smallint;
    v_user_att smallint;
    v_parent_id_att smallint;
    v_parent_user_att smallint;
    v_constraint record;
begin
    select attnum into v_parent_id_att from pg_attribute
     where attrelid = 'public.jap_sessions'::regclass and attname = 'id' and not attisdropped;
    select attnum into v_parent_user_att from pg_attribute
     where attrelid = 'public.jap_sessions'::regclass and attname = 'user_id' and not attisdropped;

    foreach v_table in array array['jap_session_events', 'jap_records'] loop
        v_rel := format('public.%I', v_table)::regclass;
        v_constraint_name := v_table || '_session_id_fkey';
        select attnum into v_session_att from pg_attribute
         where attrelid = v_rel and attname = 'session_id' and not attisdropped;
        select attnum into v_user_att from pg_attribute
         where attrelid = v_rel and attname = 'user_id' and not attisdropped;
        if v_session_att is null or v_user_att is null then
            raise exception 'Preflight failed: public.% is missing session_id or user_id; cannot safely add its session FK.', v_table;
        end if;

        select c.contype, c.confrelid, c.conkey, c.confkey, c.confdeltype,
               c.confupdtype, c.condeferrable, c.convalidated
          into v_constraint from pg_constraint c
         where c.conrelid = v_rel and c.conname = v_constraint_name;
        if found then
            if v_constraint.contype <> 'f'
               or v_constraint.confrelid <> 'public.jap_sessions'::regclass
               or v_constraint.conkey <> array[v_session_att, v_user_att]::smallint[]
               or v_constraint.confkey <> array[v_parent_id_att, v_parent_user_att]::smallint[]
               or v_constraint.confdeltype <> 'r'
               or v_constraint.confupdtype <> 'a'
               or v_constraint.condeferrable
               or not v_constraint.convalidated then
                raise exception 'Preflight failed: constraint public.% has an unexpected definition; expected FOREIGN KEY (session_id, user_id) REFERENCES public.jap_sessions(id, user_id) ON DELETE RESTRICT. No changes were applied.', v_constraint_name;
            end if;
        else
            select c.confdeltype, c.confupdtype, c.condeferrable, c.convalidated
              into v_constraint from pg_constraint c
             where c.conrelid = v_rel and c.contype = 'f'
               and c.confrelid = 'public.jap_sessions'::regclass
               and c.conkey = array[v_session_att, v_user_att]::smallint[]
               and c.confkey = array[v_parent_id_att, v_parent_user_att]::smallint[]
             limit 1;
            if found then
                if v_constraint.confdeltype <> 'r' or v_constraint.confupdtype <> 'a'
                   or v_constraint.condeferrable or not v_constraint.convalidated then
                    raise exception 'Preflight failed: public.% has an equivalent session ownership FK with incompatible behavior.', v_table;
                end if;
            else
                execute format('alter table %s add constraint %I foreign key (session_id, user_id) references public.jap_sessions(id, user_id) on delete restrict', v_rel, v_constraint_name);
            end if;
        end if;
    end loop;
end
$$;

create index if not exists jap_session_events_user_session_sequence_idx
    on public.jap_session_events (user_id, session_id, sequence_no);

-- jap_records is the canonical count ledger. A session is copied there once
-- at completion. session.count remains operational metadata, not a dashboard
-- count source.
create unique index if not exists jap_records_id_unique on public.jap_records(id);

create unique index if not exists jap_records_session_id_unique
    on public.jap_records(session_id) where session_id is not null;

-- Existing completed sessions are deliberately not auto-imported: the old
-- schema has no reliable session-to-record key, so doing so could duplicate a
-- manually saved record. Use the reviewed reconciliation template separately.

-- Fail-closed preflight above protects existing duplicate defaults; this index
-- enforces one default per account for all later writes.
create unique index if not exists naam_types_one_default_per_user
    on public.naam_types(user_id) where is_default;

alter table public.jap_session_events enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public' and tablename = 'jap_session_events'
          and policyname = 'Users read their own session events'
    ) then
        create policy "Users read their own session events" on public.jap_session_events
            for select to authenticated using (auth.uid() = user_id);
    end if;
end
$$;

revoke all on public.jap_session_events from anon, authenticated;
grant select on public.jap_session_events to authenticated;

create or replace function public.ensure_default_naam_type()
returns setof public.naam_types
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_naam public.naam_types%rowtype;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));

    select * into v_naam
    from public.naam_types
    where user_id = v_user_id and is_default
    order by created_at, id
    limit 1;

    if not found then
        insert into public.naam_types (user_id, name, is_default)
        values (v_user_id, 'Om Namah Shivaya', true)
        returning * into v_naam;
    end if;

    return next v_naam;
end;
$$;

create or replace function public.create_naam_type(p_id uuid, p_name text)
returns setof public.naam_types
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_naam public.naam_types%rowtype;
    v_name text := btrim(coalesce(p_name, ''));
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_id is null or length(v_name) < 2 or length(v_name) > 80 then
        raise exception 'Naam type is invalid.' using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));
    select * into v_naam from public.naam_types where id = p_id and user_id = v_user_id;
    if found then
        if v_naam.name is distinct from v_name then
            raise exception 'Naam type ID is already used by this account for a different name.' using errcode = '22023';
        end if;
        return next v_naam;
        return;
    end if;
    insert into public.naam_types (id, user_id, name, is_default)
    values (p_id, v_user_id, v_name, false)
    returning * into v_naam;
    return next v_naam;
end;
$$;

create or replace function public.set_default_naam_type(p_naam_id uuid)
returns setof public.naam_types
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_naam public.naam_types%rowtype;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_naam_id is null then raise exception 'Naam type is invalid.' using errcode = '22023'; end if;
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));
    select * into v_naam from public.naam_types where id = p_naam_id and user_id = v_user_id for update;
    if not found then raise exception 'Naam type is unavailable.' using errcode = '23503'; end if;
    update public.naam_types set is_default = false where user_id = v_user_id and is_default;
    update public.naam_types set is_default = true where id = p_naam_id and user_id = v_user_id returning * into v_naam;
    return next v_naam;
end;
$$;

create or replace function public.start_jap_session(p_operation_id uuid, p_naam_id uuid)
returns setof public.jap_sessions
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_session public.jap_sessions%rowtype;
    v_event public.jap_session_events%rowtype;
    v_sequence bigint;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_operation_id is null or p_naam_id is null then raise exception 'Session request is invalid.' using errcode = '22023'; end if;
    -- One lock per caller serializes active-session creation and event sequences.
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));

    select * into v_event from public.jap_session_events
    where user_id = v_user_id and operation_id = p_operation_id;
    if found then
        if v_event.event_type <> 'start' then
            raise exception 'Operation id was already used for a different action.' using errcode = '22023';
        end if;
        select * into v_session from public.jap_sessions
        where id = v_event.session_id and user_id = v_user_id;
        if found and v_session.naam_id = p_naam_id then return next v_session; return; end if;
        raise exception 'Session operation could not be restored.' using errcode = 'P0002';
    end if;

    perform 1 from public.naam_types where id = p_naam_id and user_id = v_user_id;
    if not found then raise exception 'Naam type is unavailable.' using errcode = '23503'; end if;

    select * into v_session from public.jap_sessions
    where user_id = v_user_id and ended_at is null
    order by started_at desc, created_at desc
    limit 1
    for update;

    if not found then
        insert into public.jap_sessions (user_id, naam_id, count, started_at)
        values (v_user_id, p_naam_id, 0, now())
        returning * into v_session;
    elsif v_session.naam_id <> p_naam_id then
        raise exception 'An active session already exists for a different naam. Finish it before starting another.' using errcode = '55000';
    end if;

    select coalesce(max(sequence_no), 0) + 1 into v_sequence
    from public.jap_session_events
    where user_id = v_user_id and session_id = v_session.id;

    insert into public.jap_session_events
        (user_id, session_id, operation_id, sequence_no, event_type, count_after, delta_count)
    values (v_user_id, v_session.id, p_operation_id, v_sequence, 'start', v_session.count, 0);

    return next v_session;
end;
$$;

create or replace function public.apply_jap_session_action(
    p_session_id uuid,
    p_operation_id uuid,
    p_action text,
    p_timezone text
)
returns setof public.jap_sessions
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_session public.jap_sessions%rowtype;
    v_event public.jap_session_events%rowtype;
    v_last_event text;
    v_sequence bigint;
    v_delta integer := 0;
    v_previous_count bigint;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_session_id is null or p_operation_id is null
       or p_action is null
       or p_action not in ('increment', 'undo', 'pause', 'resume', 'finish') then
        raise exception 'Session action is invalid.' using errcode = '22023';
    end if;

    if p_timezone is null or not exists (
        select 1 from pg_timezone_names where name = p_timezone
    ) then raise exception 'Timezone is invalid.' using errcode = '22023'; end if;

    -- Same lock order as start_jap_session prevents event sequence races.
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));

    select * into v_session from public.jap_sessions
    where id = p_session_id and user_id = v_user_id
    for update;
    if not found then raise exception 'Session not found.' using errcode = 'P0002'; end if;

    select * into v_event from public.jap_session_events
    where user_id = v_user_id and operation_id = p_operation_id;
    if found then
        if v_event.session_id <> p_session_id or v_event.event_type <> p_action then
            raise exception 'Operation id was already used for a different session or action.' using errcode = '22023';
        end if;
        return next v_session;
        return;
    end if;

    if v_session.ended_at is not null then raise exception 'Session is already finished.' using errcode = '55000'; end if;

    select event_type into v_last_event from public.jap_session_events
    where user_id = v_user_id and session_id = p_session_id
      and event_type in ('pause', 'resume')
    order by sequence_no desc limit 1;

    if p_action = 'increment' then
        if v_last_event = 'pause' then raise exception 'Resume the session before counting.' using errcode = '55000'; end if;
        update public.jap_sessions set count = count + 1
        where id = p_session_id and user_id = v_user_id returning * into v_session;
        v_delta := 1;
    elsif p_action = 'undo' then
        if v_last_event = 'pause' then raise exception 'Resume the session before changing the count.' using errcode = '55000'; end if;
        v_previous_count := v_session.count;
        update public.jap_sessions set count = greatest(count - 1, 0)
        where id = p_session_id and user_id = v_user_id returning * into v_session;
        if v_previous_count > 0 then
            v_delta := -1;
        end if;
    elsif p_action = 'pause' then
        if v_last_event = 'pause' then raise exception 'Session is already paused.' using errcode = '55000'; end if;
    elsif p_action = 'resume' then
        if v_last_event is distinct from 'pause' then raise exception 'Session is not paused.' using errcode = '55000'; end if;
    elsif p_action = 'finish' then
        update public.jap_sessions set ended_at = now()
        where id = p_session_id and user_id = v_user_id returning * into v_session;
    end if;

    select coalesce(max(sequence_no), 0) + 1 into v_sequence
    from public.jap_session_events
    where user_id = v_user_id and session_id = p_session_id;

    insert into public.jap_session_events
        (user_id, session_id, operation_id, sequence_no, event_type, count_after, delta_count)
    values (v_user_id, p_session_id, p_operation_id, v_sequence, p_action, v_session.count, v_delta);

    if p_action = 'finish' and v_session.count > 0 then
        insert into public.jap_records
            (user_id, naam_id, count, record_date, notes, session_id)
        values (
            v_user_id, v_session.naam_id, v_session.count,
            (now() at time zone p_timezone)::date, null, v_session.id
        );
    end if;

    return next v_session;
end;
$$;

create or replace function public.create_jap_record(
    p_id uuid,
    p_naam_id uuid,
    p_count bigint,
    p_record_date date,
    p_notes text
)
returns setof public.jap_records
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_record public.jap_records%rowtype;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_id is null or p_naam_id is null or p_count < 1 or p_record_date is null
       or length(coalesce(p_notes, '')) > 400 then
        raise exception 'Practice record is invalid.' using errcode = '22023';
    end if;
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));

    select * into v_record from public.jap_records where id = p_id and user_id = v_user_id;
    if found then
        if v_record.naam_id <> p_naam_id or v_record.count <> p_count
           or v_record.record_date <> p_record_date
           or v_record.notes is distinct from nullif(p_notes, '') then
            raise exception 'Record id was already used for different record data.' using errcode = '22023';
        end if;
        return next v_record;
        return;
    end if;

    perform 1 from public.naam_types where id = p_naam_id and user_id = v_user_id;
    if not found then raise exception 'Naam type is unavailable.' using errcode = '23503'; end if;

    insert into public.jap_records (id, user_id, naam_id, count, record_date, notes)
    values (p_id, v_user_id, p_naam_id, p_count, p_record_date, nullif(p_notes, ''))
    returning * into v_record;
    return next v_record;
end;
$$;

create or replace function public.get_active_jap_session()
returns setof public.jap_sessions
language sql
security definer
set search_path = ''
as $$
    select s.* from public.jap_sessions s
    where s.user_id = auth.uid() and s.ended_at is null
    order by s.started_at desc, s.created_at desc
    limit 1;
$$;

create or replace function public.get_jap_session_durations(p_session_ids uuid[])
returns table (session_id uuid, duration_seconds bigint, is_paused boolean)
language sql
security definer
set search_path = ''
as $$
    with owned_sessions as (
        select s.id, s.started_at, s.ended_at
        from public.jap_sessions s
        where s.user_id = auth.uid() and s.id = any(coalesce(p_session_ids, array[]::uuid[]))
    ), ordered_events as (
        select e.session_id, e.event_type, e.occurred_at,
               lead(e.event_type) over (partition by e.session_id order by e.sequence_no) as next_type,
               lead(e.occurred_at) over (partition by e.session_id order by e.sequence_no) as next_at
        from public.jap_session_events e
        join owned_sessions s on s.id = e.session_id
        where e.user_id = auth.uid()
          and e.event_type in ('pause', 'resume', 'finish')
    ), pause_totals as (
        select e.session_id,
               sum(extract(epoch from (coalesce(e.next_at, s.ended_at, now()) - e.occurred_at)))::bigint as paused_seconds
        from ordered_events e
        join owned_sessions s on s.id = e.session_id
        where e.event_type = 'pause' and (e.next_type in ('resume', 'finish') or e.next_type is null)
        group by e.session_id
    ), last_state as (
        select distinct on (e.session_id) e.session_id, e.event_type
        from public.jap_session_events e
        join owned_sessions s on s.id = e.session_id
        where e.user_id = auth.uid()
          and e.event_type in ('pause', 'resume')
        order by e.session_id, e.sequence_no desc
    )
    select s.id,
           greatest(extract(epoch from (coalesce(s.ended_at, now()) - s.started_at))::bigint
                    - coalesce(p.paused_seconds, 0), 0)::bigint,
           (s.ended_at is null and l.event_type = 'pause')
    from owned_sessions s
    left join pause_totals p on p.session_id = s.id
    left join last_state l on l.session_id = s.id;
$$;

create or replace function public.save_daily_goal(p_target_count bigint)
returns setof public.daily_goals
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_goal public.daily_goals%rowtype;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_target_count < 1 or p_target_count > 1000000000 then raise exception 'Daily goal is invalid.' using errcode = '22023'; end if;
    perform pg_advisory_xact_lock(hashtextextended(v_user_id::text, 0));

    select * into v_goal from public.daily_goals
    where user_id = v_user_id order by created_at desc, id desc limit 1 for update;
    if found then
        update public.daily_goals set target_count = p_target_count
        where id = v_goal.id and user_id = v_user_id returning * into v_goal;
    else
        insert into public.daily_goals (user_id, target_count)
        values (v_user_id, p_target_count) returning * into v_goal;
    end if;
    return next v_goal;
end;
$$;

create or replace function public.get_practice_dashboard(p_timezone text)
returns table (
    today_count bigint,
    lifetime_count bigint,
    sessions_today bigint,
    current_streak integer,
    daily_goal bigint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
    v_today date;
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    if p_timezone is null or not exists (
        select 1 from pg_timezone_names where name = p_timezone
    ) then raise exception 'Timezone is invalid.' using errcode = '22023'; end if;
    v_today := (now() at time zone p_timezone)::date;

    return query
    -- jap_records is the sole count ledger. Completed sessions are inserted
    -- into jap_records at finish; never add jap_sessions.count or event deltas.
    with activity as (
        select r.record_date as activity_date, sum(r.count)::bigint as amount
        from public.jap_records r
        where r.user_id = v_user_id
        group by r.record_date
    ), daily as (
        select a.activity_date, sum(a.amount)::bigint as amount
        from activity a
        group by a.activity_date
        having sum(a.amount) > 0
    ), streak_anchor as (
        -- Keep a streak alive through a day with no activity, but only when
        -- yesterday is the latest eligible streak endpoint.
        select case
                 when exists (select 1 from daily where activity_date = v_today) then v_today
                 when exists (select 1 from daily where activity_date = v_today - 1) then v_today - 1
                 else null::date
               end as anchor_date
    ), streak_days as (
        select d.activity_date,
               row_number() over (order by d.activity_date desc) - 1 as day_offset,
               a.anchor_date
        from daily d
        cross join streak_anchor a
        where d.activity_date <= a.anchor_date
    ), streak as (
        select count(*)::integer as count
        from streak_days
        where activity_date = anchor_date - day_offset::integer
    ),
    totals as (
        select
            coalesce((select amount from daily where activity_date = v_today), 0)::bigint as today_count,
            coalesce((select sum(r.count) from public.jap_records r where r.user_id = v_user_id), 0)::bigint as lifetime_count,
            (select count(*) from public.jap_sessions s
             where s.user_id = v_user_id and (s.started_at at time zone p_timezone)::date = v_today)::bigint as sessions_today,
            (select count from streak) as current_streak,
            coalesce((select g.target_count from public.daily_goals g
                      where g.user_id = v_user_id order by g.created_at desc, g.id desc limit 1), 1000)::bigint as daily_goal
    )
    select totals.today_count, totals.lifetime_count, totals.sessions_today,
           totals.current_streak, totals.daily_goal
    from totals;
end;
$$;

create or replace function public.delete_my_account()
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then raise exception 'Authentication required.' using errcode = '28000'; end if;
    -- Delete only rows owned by auth.uid(). Any trigger/FK error aborts this
    -- function's statement and rolls the transaction back. Supabase Auth or
    -- project-specific triggers may have additional effects/dependencies.
    -- Storage objects are not removed here; Storage cleanup requires a
    -- separate, verified Storage API/service workflow before deleting auth.users.
    delete from public.jap_session_events where user_id = v_user_id;
    delete from public.jap_records where user_id = v_user_id;
    delete from public.jap_sessions where user_id = v_user_id;
    delete from public.daily_goals where user_id = v_user_id;
    delete from public.naam_types where user_id = v_user_id;
    delete from public.profiles where id = v_user_id;
    delete from auth.users where id = v_user_id;
end;
$$;

revoke all on function public.naamjap_phase3_touch_profile_updated_at() from public, anon, authenticated;
revoke all on function public.naamjap_phase3_create_profile_for_auth_user() from public, anon, authenticated;
revoke all on function public.ensure_default_naam_type() from public, anon;
revoke all on function public.create_naam_type(uuid, text) from public, anon;
revoke all on function public.set_default_naam_type(uuid) from public, anon;
revoke all on function public.start_jap_session(uuid, uuid) from public, anon;
revoke all on function public.apply_jap_session_action(uuid, uuid, text, text) from public, anon;
revoke all on function public.create_jap_record(uuid, uuid, bigint, date, text) from public, anon;
revoke all on function public.get_active_jap_session() from public, anon;
revoke all on function public.get_jap_session_durations(uuid[]) from public, anon;
revoke all on function public.save_daily_goal(bigint) from public, anon;
revoke all on function public.get_practice_dashboard(text) from public, anon;
revoke all on function public.delete_my_account() from public, anon;
grant execute on function public.ensure_default_naam_type() to authenticated;
grant execute on function public.create_naam_type(uuid, text) to authenticated;
grant execute on function public.set_default_naam_type(uuid) to authenticated;
grant execute on function public.start_jap_session(uuid, uuid) to authenticated;
grant execute on function public.apply_jap_session_action(uuid, uuid, text, text) to authenticated;
grant execute on function public.create_jap_record(uuid, uuid, bigint, date, text) to authenticated;
grant execute on function public.get_active_jap_session() to authenticated;
grant execute on function public.get_jap_session_durations(uuid[]) to authenticated;
grant execute on function public.save_daily_goal(bigint) to authenticated;
grant execute on function public.get_practice_dashboard(text) to authenticated;
grant execute on function public.delete_my_account() to authenticated;

notify pgrst, 'reload schema';
commit;
