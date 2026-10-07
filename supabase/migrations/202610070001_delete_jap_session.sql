-- Delete one authenticated caller-owned session and its ledger/event rows.
-- Password confirmation is enforced by the app's reauthentication flow; ownership
-- is independently enforced here from auth.uid().
begin;

create or replace function public.delete_jap_session(p_session_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_user_id uuid := auth.uid();
begin
    if v_user_id is null then
        raise exception 'Authentication required.' using errcode = '28000';
    end if;
    if p_session_id is null then
        raise exception 'Session ID is required.' using errcode = '22023';
    end if;

    perform 1
    from public.jap_sessions
    where id = p_session_id and user_id = v_user_id and ended_at is not null
    for update;
    if not found then
        raise exception 'Completed session not found.' using errcode = 'P0002';
    end if;

    delete from public.jap_records
    where session_id = p_session_id and user_id = v_user_id;
    delete from public.jap_session_events
    where session_id = p_session_id and user_id = v_user_id;
    delete from public.jap_sessions
    where id = p_session_id and user_id = v_user_id;
end;
$$;

revoke all on function public.delete_jap_session(uuid) from public, anon;
grant execute on function public.delete_jap_session(uuid) to authenticated;

notify pgrst, 'reload schema';
commit;
