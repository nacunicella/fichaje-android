-- Cerrar mi cuenta: borra el usuario (login) y deja al integrante inactivo.
-- Las jornadas y cobros quedan, para que el admin no pierda el historial.
create or replace function public.delete_my_account() returns void
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
begin
  if uid is null then raise exception 'sin sesión'; end if;
  if exists (select 1 from public.members where user_id = uid and role = 'admin' and active and deleted_at is null)
     and not exists (select 1 from public.members where user_id <> uid and role = 'admin' and active and deleted_at is null) then
    raise exception 'Sos el único admin: antes nombrá a otro admin del equipo.';
  end if;
  update public.members set user_id = null, active = false, invite_code = null where user_id = uid;
  delete from auth.users where id = uid;
end $$;

revoke all on function public.delete_my_account() from public;
grant execute on function public.delete_my_account() to authenticated;
