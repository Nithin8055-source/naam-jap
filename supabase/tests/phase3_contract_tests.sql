-- Run ONLY against a disposable Supabase staging project, as postgres.
-- Requires at least two disposable auth.users accounts. All row changes roll
-- back at the end; do not run concurrently with real app traffic.
begin;

do $$
declare
    v_delete_session regprocedure := to_regprocedure('public.delete_jap_session(uuid)');
begin
    if v_delete_session is null then
        raise exception 'Expected public.delete_jap_session(uuid) RPC is missing';
    end if;
    if not has_function_privilege('authenticated', v_delete_session, 'EXECUTE') then
        raise exception 'authenticated must be able to execute delete_jap_session(uuid)';
    end if;
    if has_function_privilege('anon', v_delete_session, 'EXECUTE') then
        raise exception 'anon/public must not be able to execute delete_jap_session(uuid)';
    end if;
end
$$;

create temporary table phase3_test_context (
    owner_id uuid not null,
    hidden_session_id uuid not null
);
grant select on phase3_test_context to authenticated;

-- Assert the migration installed the intended ownership/deletion constraints,
-- rather than relying only on their names.
do $$
declare
    v_child text;
    v_user_att smallint;
    v_session_att smallint;
    v_parent_id smallint;
    v_parent_user smallint;
    v_auth_id smallint;
begin
    select attnum into v_parent_id from pg_attribute
     where attrelid = 'public.jap_sessions'::regclass and attname = 'id' and not attisdropped;
    select attnum into v_parent_user from pg_attribute
     where attrelid = 'public.jap_sessions'::regclass and attname = 'user_id' and not attisdropped;
    select attnum into v_auth_id from pg_attribute
     where attrelid = 'auth.users'::regclass and attname = 'id' and not attisdropped;

    if not exists (
        select 1 from pg_constraint c
        where c.conrelid = 'public.jap_sessions'::regclass and c.contype = 'p'
          and c.conkey = array[v_parent_id]::smallint[]
    ) then raise exception 'Expected jap_sessions.id single-column primary key is missing'; end if;

    foreach v_child in array array['jap_session_events', 'jap_records'] loop
        select attnum into v_session_att from pg_attribute
         where attrelid = format('public.%I', v_child)::regclass and attname = 'session_id' and not attisdropped;
        select attnum into v_user_att from pg_attribute
         where attrelid = format('public.%I', v_child)::regclass and attname = 'user_id' and not attisdropped;
        if not exists (
            select 1 from pg_constraint c
            where c.conrelid = format('public.%I', v_child)::regclass and c.contype = 'f'
              and c.confrelid = 'public.jap_sessions'::regclass
              and c.conkey = array[v_session_att, v_user_att]::smallint[]
              and c.confkey = array[v_parent_id, v_parent_user]::smallint[]
              and c.confdeltype = 'r' and c.confupdtype = 'a'
              and not c.condeferrable and c.convalidated
        ) then raise exception 'Expected validated ownership FK with ON DELETE RESTRICT missing on public.%', v_child; end if;
    end loop;

    foreach v_child in array array['naam_types', 'jap_sessions', 'jap_records', 'daily_goals'] loop
        select attnum into v_user_att from pg_attribute
         where attrelid = format('public.%I', v_child)::regclass and attname = 'user_id' and not attisdropped;
        if not exists (
            select 1 from pg_constraint c
            where c.conrelid = format('public.%I', v_child)::regclass and c.contype = 'f'
              and c.confrelid = 'auth.users'::regclass
              and c.conkey = array[v_user_att]::smallint[]
              and c.confkey = array[v_auth_id]::smallint[]
              and c.confdeltype = 'c' and c.confupdtype = 'a'
              and not c.condeferrable and c.convalidated
        ) then raise exception 'Expected validated user_id FK with ON DELETE CASCADE missing on public.%', v_child; end if;
    end loop;

    if not exists (
        select 1 from pg_index i
        where i.indrelid = 'public.naam_types'::regclass and i.indisunique and i.indisvalid
          and pg_get_expr(i.indpred, i.indrelid) = 'is_default'
          and pg_get_indexdef(i.indexrelid) like '%(user_id)%'
    ) then raise exception 'Expected unique partial index for one default naam per user is missing'; end if;
end
$$;

do $$
declare
    v_users uuid[];
    v_owner uuid;
    v_other uuid;
    v_naam public.naam_types%rowtype;
    v_second_naam public.naam_types%rowtype;
    v_retry_naam public.naam_types%rowtype;
    v_other_naam public.naam_types%rowtype;
    v_session public.jap_sessions%rowtype;
    v_retry public.jap_sessions%rowtype;
    v_other_session public.jap_sessions%rowtype;
    v_goal1 public.daily_goals%rowtype;
    v_goal2 public.daily_goals%rowtype;
    v_before_lifetime bigint;
    v_before_today bigint;
    v_after_lifetime bigint;
    v_after_today bigint;
    v_streak integer;
    v_test_today date := (now() at time zone 'Pacific/Kiritimati')::date;
    v_operation uuid := gen_random_uuid();
    v_increment uuid := gen_random_uuid();
    v_pause uuid := gen_random_uuid();
    v_resume uuid := gen_random_uuid();
    v_finish uuid := gen_random_uuid();
begin
    select array_agg(id order by created_at)
      into v_users
      from (select id, created_at from auth.users order by created_at limit 2) u;
    if coalesce(array_length(v_users, 1), 0) < 2 then
        raise exception 'Create two disposable test users in staging before running this test.';
    end if;
    v_owner := v_users[1];
    v_other := v_users[2];

    if has_function_privilege('anon', 'public.delete_my_account()', 'EXECUTE') then
        raise exception 'anon unexpectedly has EXECUTE on delete_my_account';
    end if;
    if not has_function_privilege('authenticated', 'public.delete_my_account()', 'EXECUTE') then
        raise exception 'authenticated lacks EXECUTE on delete_my_account';
    end if;
    if exists (
        select 1 from pg_proc p,
             lateral aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) x
        where p.oid = 'public.delete_my_account()'::regprocedure
          and x.grantee = 0 and x.privilege_type = 'EXECUTE'
    ) then
        raise exception 'PUBLIC unexpectedly has EXECUTE on delete_my_account';
    end if;

    perform set_config('request.jwt.claim.sub', v_owner::text, true);
    perform set_config('request.jwt.claims', jsonb_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    select lifetime_count, today_count into v_before_lifetime, v_before_today
      from public.get_practice_dashboard('UTC');

    select * into v_naam from public.ensure_default_naam_type();
    if not v_naam.is_default then raise exception 'ensure_default_naam_type did not return a default'; end if;
    if (select count(*) from public.naam_types where user_id = v_owner and is_default) <> 1 then
        raise exception 'one-default-per-user invariant failed';
    end if;
    begin
        insert into public.naam_types (user_id, name, is_default)
        values (v_owner, 'Should be rejected', true);
        raise exception 'duplicate default naam unexpectedly succeeded';
    exception when unique_violation then null;
    end;
    select * into v_second_naam from public.create_naam_type(gen_random_uuid(), 'Test alternate');
    select * into v_retry_naam from public.create_naam_type(v_second_naam.id, '  Test alternate  ');
    if v_retry_naam.id <> v_second_naam.id or v_retry_naam.name <> 'Test alternate' then
        raise exception 'same-ID/same-normalized-name create retry did not return the existing row';
    end if;
    begin
        perform public.create_naam_type(v_second_naam.id, 'Different alternate');
        raise exception 'same-ID/different-name create retry unexpectedly succeeded';
    exception when sqlstate '22023' then null;
    end;

    select * into v_session from public.start_jap_session(v_operation, v_naam.id);
    select * into v_retry from public.start_jap_session(v_operation, v_naam.id);
    if v_session.id <> v_retry.id then raise exception 'start retry returned a different session'; end if;
    begin
        insert into public.jap_sessions (user_id, naam_id, count, started_at)
        values (v_owner, v_naam.id, 0, now());
        raise exception 'second active session unexpectedly succeeded';
    exception when unique_violation then null;
    end;
    begin
        perform public.start_jap_session(gen_random_uuid(), v_second_naam.id);
        raise exception 'start with a different naam silently reused the active session';
    exception when sqlstate '55000' then null;
    end;

    select * into v_session from public.apply_jap_session_action(v_session.id, v_increment, 'increment', 'UTC');
    select * into v_retry from public.apply_jap_session_action(v_session.id, v_increment, 'increment', 'UTC');
    if v_retry.count <> 1 then raise exception 'increment retry changed count more than once'; end if;
    if (select count(*) from public.jap_session_events where user_id = v_owner and operation_id = v_increment) <> 1 then
        raise exception 'increment retry created multiple event receipts';
    end if;

    perform public.apply_jap_session_action(v_session.id, v_pause, 'pause', 'UTC');
    perform public.apply_jap_session_action(v_session.id, v_pause, 'pause', 'UTC');
    begin
        perform public.apply_jap_session_action(v_session.id, gen_random_uuid(), 'increment', 'UTC');
        raise exception 'increment while paused unexpectedly succeeded';
    exception when sqlstate '55000' then null;
    end;
    perform public.apply_jap_session_action(v_session.id, v_resume, 'resume', 'UTC');
    perform public.apply_jap_session_action(v_session.id, v_resume, 'resume', 'UTC');
    select * into v_session from public.apply_jap_session_action(v_session.id, v_finish, 'finish', 'UTC');
    select * into v_retry from public.apply_jap_session_action(v_session.id, v_finish, 'finish', 'UTC');
    if v_retry.ended_at is null then raise exception 'finish retry lost the completed state'; end if;
    if (select count(*) from public.jap_records where session_id = v_session.id) <> 1 then
        raise exception 'a finished session must have exactly one ledger record';
    end if;

    select lifetime_count, today_count into v_after_lifetime, v_after_today
      from public.get_practice_dashboard('UTC');
    if v_after_lifetime <> v_before_lifetime + 1 then
        raise exception 'lifetime dashboard count is not sourced exactly once from jap_records';
    end if;
    if v_after_today <> v_before_today + 1 then
        raise exception 'daily dashboard count is not sourced exactly once from jap_records';
    end if;

    -- Run the streak contract against an isolated activity ledger. The outer
    -- transaction rolls back these temporary deletions and inserts.
    delete from public.jap_records where user_id = v_owner;
    insert into public.jap_records (user_id, naam_id, count, record_date)
    values (v_owner, v_naam.id, 1, v_test_today),
           (v_owner, v_naam.id, 1, v_test_today - 1);
    select current_streak into v_streak
      from public.get_practice_dashboard('Pacific/Kiritimati');
    if v_streak <> 2 then
        raise exception 'streak with activity today and yesterday should be 2, got %', v_streak;
    end if;

    delete from public.jap_records where user_id = v_owner;
    insert into public.jap_records (user_id, naam_id, count, record_date)
    values (v_owner, v_naam.id, 1, v_test_today - 1),
           (v_owner, v_naam.id, 1, v_test_today - 2);
    select current_streak into v_streak
      from public.get_practice_dashboard('Pacific/Kiritimati');
    if v_streak <> 2 then
        raise exception 'streak ending yesterday should be retained as 2, got %', v_streak;
    end if;

    delete from public.jap_records where user_id = v_owner;
    insert into public.jap_records (user_id, naam_id, count, record_date)
    values (v_owner, v_naam.id, 1, v_test_today),
           (v_owner, v_naam.id, 1, v_test_today - 2);
    select current_streak into v_streak
      from public.get_practice_dashboard('Pacific/Kiritimati');
    if v_streak <> 1 then
        raise exception 'a missing day should break the streak at 1, got %', v_streak;
    end if;

    delete from public.jap_records where user_id = v_owner;
    insert into public.jap_records (user_id, naam_id, count, record_date)
    values (v_owner, v_naam.id, 1, v_test_today - 2);
    select current_streak into v_streak
      from public.get_practice_dashboard('Pacific/Kiritimati');
    if v_streak <> 0 then
        raise exception 'activity older than yesterday should yield a zero streak, got %', v_streak;
    end if;

    delete from public.jap_records where user_id = v_owner;
    select current_streak into v_streak
      from public.get_practice_dashboard('Pacific/Kiritimati');
    if v_streak <> 0 then
        raise exception 'streak with no activity should be 0, got %', v_streak;
    end if;

    -- The newest daily_goals row is the single effective goal; updating it
    -- twice must keep the same row while older rows remain untouched.
    select * into v_goal1 from public.save_daily_goal(1234);
    select * into v_goal2 from public.save_daily_goal(2345);
    if v_goal1.id <> v_goal2.id or v_goal2.target_count <> 2345 then
        raise exception 'daily goal updates did not consistently use the newest row';
    end if;

    -- An authenticated caller cannot observe or mutate another user's session.
    perform set_config('request.jwt.claim.sub', v_other::text, true);
    perform set_config('request.jwt.claims', jsonb_build_object('sub', v_other, 'role', 'authenticated')::text, true);
    select * into v_other_naam from public.ensure_default_naam_type();
    select * into v_other_session from public.start_jap_session(gen_random_uuid(), v_other_naam.id);
    insert into pg_temp.phase3_test_context values (v_owner, v_other_session.id);
    perform set_config('request.jwt.claim.sub', v_owner::text, true);
    perform set_config('request.jwt.claims', jsonb_build_object('sub', v_owner, 'role', 'authenticated')::text, true);
    if exists (select 1 from public.get_active_jap_session() where id = v_other_session.id) then
        raise exception 'active-session RPC exposed another user’s session';
    end if;
    begin
        insert into public.jap_session_events
            (user_id, session_id, operation_id, sequence_no, event_type, count_after, delta_count)
        values (v_owner, v_other_session.id, gen_random_uuid(), 999999, 'start', 0, 0);
        raise exception 'cross-owner session event link unexpectedly succeeded';
    exception when foreign_key_violation then null;
    end;
    begin
        perform public.apply_jap_session_action(v_other_session.id, gen_random_uuid(), 'increment', 'UTC');
        raise exception 'cross-owner session mutation unexpectedly succeeded';
    exception when sqlstate 'P0002' then null;
    end;

    -- Exercise deletion only inside this outer transaction. ROLLBACK restores
    -- the disposable owner's auth and public rows after the DO block.
    perform public.delete_my_account();
    if exists (select 1 from auth.users where id = v_owner)
       or exists (select 1 from public.jap_session_events where user_id = v_owner)
       or exists (select 1 from public.jap_records where user_id = v_owner)
       or exists (select 1 from public.jap_sessions where user_id = v_owner)
       or exists (select 1 from public.daily_goals where user_id = v_owner)
       or exists (select 1 from public.naam_types where user_id = v_owner)
       or exists (select 1 from public.profiles where id = v_owner) then
        raise exception 'delete_my_account left caller-owned data behind';
    end if;
    if not exists (select 1 from auth.users where id = v_other) then
        raise exception 'delete_my_account removed another user';
    end if;
    begin
        perform public.ensure_default_naam_type();
        raise exception 'deleted account recreated rows with its still-present test JWT';
    exception when foreign_key_violation then null;
    end;
end
$$;

-- Exercise the existing authenticated RLS policy with the other user's row.
set local role authenticated;
select set_config('request.jwt.claim.sub', (select owner_id::text from pg_temp.phase3_test_context), true);
select set_config('request.jwt.claims', jsonb_build_object(
    'sub', (select owner_id from pg_temp.phase3_test_context), 'role', 'authenticated'
)::text, true);
do $$
begin
    if exists (
        select 1 from public.jap_sessions s
        where s.id = (select hidden_session_id from pg_temp.phase3_test_context)
    ) then
        raise exception 'Existing RLS policy exposed another user session';
    end if;
end
$$;
reset role;

rollback;

-- Concurrency cases: use two separate psql sessions and the same disposable
-- user. Set both JWT claim settings transaction-locally in EACH session.
-- Keep transaction A open while starting transaction B.
--
-- Concurrent start, session A (run through SELECT, then leave the transaction open):
-- BEGIN;
-- SELECT set_config('request.jwt.claim.sub', '<USER_UUID>', true);
-- SELECT set_config('request.jwt.claims',
--   jsonb_build_object('sub', '<USER_UUID>', 'role', 'authenticated')::text, true);
-- SELECT id FROM public.start_jap_session('<OP_A_UUID>', '<NAAM_UUID>');
--
-- Concurrent start, session B (run while A is still open; this call should wait):
-- BEGIN;
-- SELECT set_config('request.jwt.claim.sub', '<USER_UUID>', true);
-- SELECT set_config('request.jwt.claims',
--   jsonb_build_object('sub', '<USER_UUID>', 'role', 'authenticated')::text, true);
-- SELECT id FROM public.start_jap_session('<OP_B_UUID>', '<NAAM_UUID>');
-- After B is waiting, commit A. Then let B return and commit B.
-- COMMIT session A; COMMIT session B;
-- B should return A's session ID. Assert there is one active row.
--
-- Concurrent increment actions on that session, session A
-- (run through SELECT, then leave the transaction open):
-- BEGIN;
-- SELECT set_config('request.jwt.claim.sub', '<USER_UUID>', true);
-- SELECT set_config('request.jwt.claims',
--   jsonb_build_object('sub', '<USER_UUID>', 'role', 'authenticated')::text, true);
-- SELECT id FROM public.apply_jap_session_action(
--   '<SESSION_UUID>', '<OP_1_UUID>', 'increment', 'UTC');
--
-- Concurrent increment actions, session B (run while A is still open):
-- BEGIN;
-- SELECT set_config('request.jwt.claim.sub', '<USER_UUID>', true);
-- SELECT set_config('request.jwt.claims',
--   jsonb_build_object('sub', '<USER_UUID>', 'role', 'authenticated')::text, true);
-- SELECT id FROM public.apply_jap_session_action(
--   '<SESSION_UUID>', '<OP_2_UUID>', 'increment', 'UTC');
-- After B is waiting, commit A. Then let B return and commit B.
-- COMMIT session A; COMMIT session B;
-- Both actions should succeed; count rises by 2 and event sequence numbers are
-- distinct and consecutive for that session.
