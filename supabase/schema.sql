-- Mi Economía: ejecuta todo esto una vez en Supabase → SQL Editor → New query → Run.

create table if not exists public.eco_config (
  user_id    uuid primary key references auth.users(id) on delete cascade,
  data       jsonb not null,
  updated_at timestamptz not null default now()
);

create table if not exists public.eco_tx (
  user_id    uuid not null references auth.users(id) on delete cascade,
  id         text not null,
  data       jsonb not null,
  date       text,
  updated_at timestamptz not null default now(),
  primary key (user_id, id)
);
create index if not exists eco_tx_user_date on public.eco_tx (user_id, date);

-- Cada usuario solo ve y toca lo suyo.
alter table public.eco_config enable row level security;
alter table public.eco_tx     enable row level security;

drop policy if exists "eco_config propio" on public.eco_config;
create policy "eco_config propio" on public.eco_config
  for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

drop policy if exists "eco_tx propio" on public.eco_tx;
create policy "eco_tx propio" on public.eco_tx
  for all to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Tiempo real: lo que apuntas en el móvil aparece en el ordenador al momento.
do $$ begin
  begin alter publication supabase_realtime add table public.eco_config; exception when duplicate_object then null; end;
  begin alter publication supabase_realtime add table public.eco_tx;     exception when duplicate_object then null; end;
end $$;

-- Sin sesión no hay acceso a nada.
revoke all on public.eco_config from anon;
revoke all on public.eco_tx from anon;
