-- Fichaje: esquema inicial.
-- Se aplica una vez en el SQL Editor de Supabase (o con `supabase db push`).
-- El proyecto tiene "Automatically expose new tables" apagado, así que los
-- permisos (GRANT) y las políticas RLS están todos acá, explícitos.

create extension if not exists pgcrypto;

-- ---------------------------------------------------------------- tablas

create table public.settings (
  id           int primary key default 1 check (id = 1),
  company      text not null default 'Mi empresa',
  currency     text not null default 'ARS' check (currency in ('ARS', 'USD', 'EUR')),
  rate         numeric(12, 2) not null default 5000 check (rate > 0),
  holiday_mult numeric(4, 2) not null default 2 check (holiday_mult >= 1),
  goal_hours   numeric(4, 2) not null default 8 check (goal_hours > 0 and goal_hours <= 24),
  updated_at   timestamptz not null default now()
);
insert into public.settings (id) values (1);

create table public.members (
  id         uuid primary key default gen_random_uuid(),
  user_id    uuid unique references auth.users (id) on delete set null,
  first_name text not null default '',
  last_name  text not null default '',
  email      text unique,
  rate       numeric(12, 2) check (rate is null or rate > 0), -- null = usa la tarifa general
  active     boolean not null default true,
  role       text not null default 'member' check (role in ('admin', 'member')),
  created_at timestamptz not null default now()
);

create table public.payments (
  id             uuid primary key default gen_random_uuid(),
  member_id      uuid not null references public.members (id) on delete restrict,
  paid_at        timestamptz not null default now(),
  amount         numeric(14, 2) not null,
  worked_seconds bigint not null,
  shift_count    int not null
);

create table public.shifts (
  id         uuid primary key default gen_random_uuid(),
  member_id  uuid not null references public.members (id) on delete restrict,
  start_at   timestamptz not null,
  end_at     timestamptz,                 -- null = jornada abierta
  hours      numeric(5, 2) check (hours is null or (hours > 0 and hours <= 24)), -- cargada "por horas"
  holiday    boolean not null default false,
  manual     boolean not null default false,
  note       text,
  rate       numeric(12, 2),              -- se congela al cerrar; abierta = tarifa de hoy
  payment_id uuid references public.payments (id) on delete set null,
  created_at timestamptz not null default now(),
  check (end_at is null or end_at > start_at)
);
create unique index shifts_one_open_per_member on public.shifts (member_id) where end_at is null and hours is null;
create index shifts_member_start on public.shifts (member_id, start_at desc);

create table public.pauses (
  id       uuid primary key default gen_random_uuid(),
  shift_id uuid not null references public.shifts (id) on delete cascade,
  start_at timestamptz not null,
  end_at   timestamptz,
  check (end_at is null or end_at > start_at)
);
create index pauses_shift on public.pauses (shift_id);

-- Auditoría: la escriben solo los triggers. Nadie puede insertar, editar ni borrar desde la app.
create table public.audit_log (
  id           bigint generated always as identity primary key,
  at           timestamptz not null default now(),
  actor_user   uuid,
  actor_member uuid,
  table_name   text not null,
  action       text not null,   -- insert | update | delete
  row_id       text,
  member_id    uuid,            -- a quién afecta
  old_data     jsonb,
  new_data     jsonb
);
create index audit_at on public.audit_log (at desc);
create index audit_member on public.audit_log (member_id, at desc);

-- ---------------------------------------------------------------- helpers

create or replace function public.my_member_id() returns uuid
language sql stable security definer set search_path = public as $$
  select id from public.members where user_id = auth.uid() and active
$$;

create or replace function public.is_admin() returns boolean
language sql stable security definer set search_path = public as $$
  select exists (select 1 from public.members where user_id = auth.uid() and role = 'admin' and active)
$$;

-- ---------------------------------------------------------------- auditoría

create or replace function public.audit_row() returns trigger
language plpgsql security definer set search_path = public as $$
declare
  n jsonb := case when tg_op = 'DELETE' then null else to_jsonb(new) end;
  o jsonb := case when tg_op = 'INSERT' then null else to_jsonb(old) end;
  mid uuid;
begin
  if tg_op = 'UPDATE' and n = o then
    return new;
  end if;
  mid := case tg_table_name
    when 'members'  then coalesce(n ->> 'id', o ->> 'id')::uuid
    when 'shifts'   then coalesce(n ->> 'member_id', o ->> 'member_id')::uuid
    when 'payments' then coalesce(n ->> 'member_id', o ->> 'member_id')::uuid
    when 'pauses'   then (select s.member_id from public.shifts s where s.id = coalesce(n ->> 'shift_id', o ->> 'shift_id')::uuid)
    else null
  end;
  insert into public.audit_log (actor_user, actor_member, table_name, action, row_id, member_id, old_data, new_data)
  values (auth.uid(), public.my_member_id(), tg_table_name, lower(tg_op), coalesce(n ->> 'id', o ->> 'id'), mid, o, n);
  return coalesce(new, old);
end
$$;

create trigger audit_settings after insert or update or delete on public.settings for each row execute function public.audit_row();
create trigger audit_members  after insert or update or delete on public.members  for each row execute function public.audit_row();
create trigger audit_shifts   after insert or update or delete on public.shifts   for each row execute function public.audit_row();
create trigger audit_pauses   after insert or update or delete on public.pauses   for each row execute function public.audit_row();
create trigger audit_payments after insert or update or delete on public.payments for each row execute function public.audit_row();

-- ---------------------------------------------------------------- alta de usuarios
-- Quien se registra queda vinculado al integrante que tenga su email.
-- El primer usuario del proyecto (sin integrantes cargados) queda como admin.

create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  update public.members set user_id = new.id
   where user_id is null and lower(email) = lower(new.email);
  if not found and not exists (select 1 from public.members) then
    insert into public.members (user_id, email, role) values (new.id, new.email, 'admin');
  end if;
  return new;
end
$$;

create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------- RLS

alter table public.settings  enable row level security;
alter table public.members   enable row level security;
alter table public.shifts    enable row level security;
alter table public.pauses    enable row level security;
alter table public.payments  enable row level security;
alter table public.audit_log enable row level security;

-- Todo el equipo ve la configuración, el equipo y las jornadas (pantallas Hoy y Equipo).
create policy settings_read  on public.settings for select to authenticated using (public.my_member_id() is not null);
create policy settings_admin on public.settings for update to authenticated using (public.is_admin()) with check (public.is_admin());

create policy members_read   on public.members for select to authenticated using (public.my_member_id() is not null);
create policy members_insert on public.members for insert to authenticated with check (public.is_admin());
create policy members_update on public.members for update to authenticated using (public.is_admin()) with check (public.is_admin());
create policy members_delete on public.members for delete to authenticated using (public.is_admin());

-- Cada uno maneja sus jornadas; el admin, las de todos.
create policy shifts_read   on public.shifts for select to authenticated using (public.my_member_id() is not null);
create policy shifts_insert on public.shifts for insert to authenticated with check (member_id = public.my_member_id() or public.is_admin());
create policy shifts_update on public.shifts for update to authenticated
  using (member_id = public.my_member_id() or public.is_admin())
  with check (member_id = public.my_member_id() or public.is_admin());
create policy shifts_delete on public.shifts for delete to authenticated using (member_id = public.my_member_id() or public.is_admin());

create policy pauses_read on public.pauses for select to authenticated using (public.my_member_id() is not null);
create policy pauses_write on public.pauses for all to authenticated
  using (exists (select 1 from public.shifts s where s.id = shift_id and (s.member_id = public.my_member_id() or public.is_admin())))
  with check (exists (select 1 from public.shifts s where s.id = shift_id and (s.member_id = public.my_member_id() or public.is_admin())));

create policy payments_read on public.payments for select to authenticated using (member_id = public.my_member_id() or public.is_admin());
create policy payments_insert on public.payments for insert to authenticated with check (member_id = public.my_member_id() or public.is_admin());
create policy payments_delete on public.payments for delete to authenticated using (member_id = public.my_member_id() or public.is_admin());

-- Auditoría: el admin ve todo; cada uno, lo que hizo o lo que lo afecta. Sin políticas de escritura.
create policy audit_read on public.audit_log for select to authenticated
  using (public.is_admin() or member_id = public.my_member_id() or actor_member = public.my_member_id());

-- ---------------------------------------------------------------- permisos

grant usage on schema public to anon, authenticated;
grant select, update on public.settings to authenticated;
grant select, insert, update, delete on public.members, public.shifts, public.pauses to authenticated;
grant select, insert, delete on public.payments to authenticated;
grant select on public.audit_log to authenticated;
grant execute on function public.my_member_id(), public.is_admin() to authenticated;
