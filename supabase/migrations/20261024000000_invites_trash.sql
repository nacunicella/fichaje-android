-- Fichaje: invitaciones por código y papelera (30 días).
-- Se aplica una vez en el SQL Editor de Supabase. Es seguro correrlo dos veces.

-- ---------------------------------------------------------------- papelera
alter table public.members  add column if not exists deleted_at timestamptz;
alter table public.shifts   add column if not exists deleted_at timestamptz;
alter table public.payments add column if not exists deleted_at timestamptz;

-- Una jornada en la papelera no bloquea abrir otra.
drop index if exists public.shifts_one_open_per_member;
create unique index shifts_one_open_per_member on public.shifts (member_id)
  where end_at is null and hours is null and deleted_at is null;

-- Un integrante en la papelera no puede usar la app hasta que lo restauren.
create or replace function public.my_member_id() returns uuid
language sql stable security definer set search_path = public as $$
  select id from public.members where user_id = auth.uid() and active and deleted_at is null
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.members where user_id = auth.uid() and role = 'admin' and active and deleted_at is null)
$$;

-- Los cobros ahora se mandan a la papelera (update) en vez de borrarse.
grant update on public.payments to authenticated;
drop policy if exists payments_update on public.payments;
create policy payments_update on public.payments for update to authenticated
  using (member_id = public.my_member_id() or public.is_admin())
  with check (member_id = public.my_member_id() or public.is_admin());

-- Lo que lleva más de 30 días en la papelera se borra de verdad. La app lo llama al cargar.
create or replace function public.purge_trash() returns void
language plpgsql security definer set search_path = public as $$
begin
  delete from public.shifts   where deleted_at < now() - interval '30 days';
  delete from public.payments where deleted_at < now() - interval '30 days';
  delete from public.members  where deleted_at < now() - interval '30 days'
    and not exists (select 1 from public.shifts s where s.member_id = members.id)
    and not exists (select 1 from public.payments p where p.member_id = members.id);
end
$$;
grant execute on function public.purge_trash() to authenticated;

-- ---------------------------------------------------------------- invitaciones
alter table public.members add column if not exists invite_code text unique;

-- El admin genera un código de 6 letras/números para un integrante que todavía no tiene cuenta.
create or replace function public.new_invite(member uuid) returns text
language plpgsql security definer set search_path = public as $$
declare
  alphabet text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
  code text;
begin
  if not public.is_admin() then raise exception 'Solo el admin puede invitar'; end if;
  if exists (select 1 from public.members where id = member and user_id is not null) then
    raise exception 'Ese integrante ya tiene cuenta';
  end if;
  loop
    code := '';
    for i in 1..6 loop
      code := code || substr(alphabet, 1 + floor(random() * length(alphabet))::int, 1);
    end loop;
    exit when not exists (select 1 from public.members where invite_code = code);
  end loop;
  update public.members set invite_code = code where id = member and deleted_at is null;
  if not found then raise exception 'Integrante no encontrado'; end if;
  return code;
end
$$;
grant execute on function public.new_invite(uuid) to authenticated;

-- Quien se registra con un código queda vinculado a ese integrante (y se le guarda el email).
create or replace function public.claim_invite(code text) returns boolean
language plpgsql security definer set search_path = public as $$
declare
  uid uuid := auth.uid();
  mail text;
begin
  if uid is null then return false; end if;
  select email into mail from auth.users where id = uid;
  update public.members
     set user_id = uid, email = coalesce(email, mail), invite_code = null
   where upper(invite_code) = upper(trim(code)) and user_id is null and deleted_at is null;
  return found;
end
$$;
grant execute on function public.claim_invite(text) to authenticated;
