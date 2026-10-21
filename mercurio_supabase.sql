-- =====================================================================
-- MERCURIO · esquema completo de Supabase (equivale a Hermes v1–v6 + tablas de la app Android)
-- Ejecutar UNA vez en Supabase > SQL Editor > New query > Run.
-- Es idempotente: se puede volver a correr sin romper nada.
-- Convención de Hermes: ids text, tiempos en milisegundos (bigint).
-- =====================================================================

create extension if not exists pgcrypto;

-- helper: ahora en ms
create or replace function public.now_ms() returns bigint
language sql stable as $$ select (extract(epoch from now()) * 1000)::bigint $$;

-- ---------------------------------------------------------------------
-- 1. users (perfil; id = auth.users.id)
-- ---------------------------------------------------------------------
create table if not exists public.users (
  id               uuid primary key references auth.users(id) on delete cascade,
  handle           text default '',
  name             text default '',
  bio              text default '',
  avatar           text default '',
  visibility       text default 'public' check (visibility in ('public','friends','private')),
  show_stats       boolean default true,
  show_activity    boolean default true,
  show_nowplaying  boolean default true,
  now_playing      jsonb,
  now_playing_at   bigint default 0,
  updated_at       bigint default public.now_ms()
);

-- perfil automático al registrarse (email/contraseña o Google)
create or replace function public.handle_new_user() returns trigger
language plpgsql security definer set search_path = public as $$
begin
  insert into public.users (id, handle, name, avatar)
  values (
    new.id,
    coalesce(new.raw_user_meta_data->>'full_name', split_part(new.email,'@',1), ''),
    coalesce(new.raw_user_meta_data->>'full_name', ''),
    coalesce(new.raw_user_meta_data->>'avatar_url', '')
  )
  on conflict (id) do nothing;
  return new;
end $$;
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------
-- 2. playlists + playlist_tracks
--    playlists: igual que Hermes (tracks_json) para compartir datos con la web.
--    playlist_tracks: la usa la app Android (CloudPlaylistsRepository.addTrack).
-- ---------------------------------------------------------------------
create table if not exists public.playlists (
  id           text primary key default gen_random_uuid()::text,
  user_id      uuid not null default auth.uid() references auth.users(id) on delete cascade,
  name         text not null default 'Sin nombre',
  description  text default '',
  color        text default '#6366f1',
  is_public    boolean default true,
  tracks_json  jsonb default '[]'::jsonb,
  updated_at   bigint default public.now_ms()
);
create index if not exists playlists_user_idx on public.playlists (user_id);

create table if not exists public.playlist_tracks (
  id           text primary key default gen_random_uuid()::text,
  playlist_id  text not null references public.playlists(id) on delete cascade,
  title        text default '',
  artist       text default '',
  url          text default '',
  position     integer default 0,
  added_at     bigint default public.now_ms()
);
create index if not exists playlist_tracks_pl_idx on public.playlist_tracks (playlist_id);

-- ---------------------------------------------------------------------
-- 3. rooms, messages, room_tracks, message_reactions, follows
-- ---------------------------------------------------------------------
create table if not exists public.rooms (
  id           text primary key,
  kind         text default 'group' check (kind in ('group','dm')),
  member_a     uuid,
  member_b     uuid,
  name         text default '',
  description  text default '',
  genre        text default '',
  icon         text default '',
  color        text default '',
  is_public    boolean default true,
  created_by   uuid default auth.uid(),
  created_at   bigint default public.now_ms()
);

create table if not exists public.messages (
  id           text primary key,
  room_id      text not null,
  author       text default '',
  author_id    uuid default auth.uid(),
  text         text default '',
  song_title   text default '',
  song_artist  text default '',
  song_art     text default '',
  song_url     text default '',
  created_at   bigint default public.now_ms()
);
create index if not exists messages_room_idx on public.messages (room_id, created_at);

create table if not exists public.room_tracks (
  room_id    text not null,
  track_id   text not null,
  title      text default '',
  artist     text default '',
  art        text default '',
  url        text default '',
  duration   numeric default 0,
  added_by   uuid default auth.uid(),
  added_at   bigint default public.now_ms(),
  primary key (room_id, track_id)
);

create table if not exists public.message_reactions (
  id          text primary key,            -- `${message_id}:${user_id}:${emoji}`
  room_id     text not null,
  message_id  text not null,
  user_id     uuid not null default auth.uid(),
  emoji       text not null,
  created_at  bigint not null default public.now_ms()
);
create index if not exists message_reactions_room_idx on public.message_reactions (room_id);

create table if not exists public.follows (
  follower_id   uuid not null references auth.users(id) on delete cascade,
  following_id  uuid not null references auth.users(id) on delete cascade,
  created_at    bigint default public.now_ms(),
  primary key (follower_id, following_id)
);
create index if not exists follows_following_idx on public.follows (following_id);

-- ---------------------------------------------------------------------
-- 4. Row Level Security
-- ---------------------------------------------------------------------
alter table public.users             enable row level security;
alter table public.playlists         enable row level security;
alter table public.playlist_tracks   enable row level security;
alter table public.rooms             enable row level security;
alter table public.messages          enable row level security;
alter table public.room_tracks       enable row level security;
alter table public.message_reactions enable row level security;
alter table public.follows           enable row level security;

-- users: perfiles legibles por todos, cada quien edita el suyo
drop policy if exists users_read_all   on public.users;
drop policy if exists users_insert_own on public.users;
drop policy if exists users_update_own on public.users;
create policy users_read_all   on public.users for select using (true);
create policy users_insert_own on public.users for insert with check (auth.uid() = id);
create policy users_update_own on public.users for update using (auth.uid() = id) with check (auth.uid() = id);

-- playlists: las tuyas completas; las públicas las ve cualquiera
drop policy if exists playlists_select on public.playlists;
drop policy if exists playlists_insert on public.playlists;
drop policy if exists playlists_update on public.playlists;
drop policy if exists playlists_delete on public.playlists;
create policy playlists_select on public.playlists for select using (user_id = auth.uid() or is_public = true);
create policy playlists_insert on public.playlists for insert with check (user_id = auth.uid());
create policy playlists_update on public.playlists for update using (user_id = auth.uid()) with check (user_id = auth.uid());
create policy playlists_delete on public.playlists for delete using (user_id = auth.uid());

-- playlist_tracks: según la playlist padre
drop policy if exists pltracks_select on public.playlist_tracks;
drop policy if exists pltracks_insert on public.playlist_tracks;
drop policy if exists pltracks_update on public.playlist_tracks;
drop policy if exists pltracks_delete on public.playlist_tracks;
create policy pltracks_select on public.playlist_tracks for select using (
  exists (select 1 from public.playlists p where p.id = playlist_id and (p.user_id = auth.uid() or p.is_public)));
create policy pltracks_insert on public.playlist_tracks for insert with check (
  exists (select 1 from public.playlists p where p.id = playlist_id and p.user_id = auth.uid()));
create policy pltracks_update on public.playlist_tracks for update using (
  exists (select 1 from public.playlists p where p.id = playlist_id and p.user_id = auth.uid()));
create policy pltracks_delete on public.playlist_tracks for delete using (
  exists (select 1 from public.playlists p where p.id = playlist_id and p.user_id = auth.uid()));

-- rooms
drop policy if exists rooms_select on public.rooms;
drop policy if exists rooms_insert on public.rooms;
drop policy if exists rooms_update on public.rooms;
drop policy if exists rooms_delete on public.rooms;
create policy rooms_select on public.rooms for select using (
  coalesce(is_public, true) or created_by = auth.uid() or member_a = auth.uid() or member_b = auth.uid());
create policy rooms_insert on public.rooms for insert with check (auth.uid() is not null and created_by = auth.uid());
create policy rooms_update on public.rooms for update using (created_by = auth.uid());
create policy rooms_delete on public.rooms for delete using (created_by = auth.uid());

-- messages
drop policy if exists messages_select on public.messages;
drop policy if exists messages_insert on public.messages;
drop policy if exists messages_delete on public.messages;
create policy messages_select on public.messages for select using (auth.uid() is not null);
create policy messages_insert on public.messages for insert with check (author_id = auth.uid());
create policy messages_delete on public.messages for delete using (author_id = auth.uid());

-- room_tracks (en salas y directos todos pueden agregar y quitar, como en Hermes v6)
drop policy if exists room_tracks_select on public.room_tracks;
drop policy if exists room_tracks_insert on public.room_tracks;
drop policy if exists room_tracks_update on public.room_tracks;
drop policy if exists room_tracks_delete on public.room_tracks;
create policy room_tracks_select on public.room_tracks for select using (auth.uid() is not null);
create policy room_tracks_insert on public.room_tracks for insert with check (auth.uid() is not null);
create policy room_tracks_update on public.room_tracks for update using (auth.uid() is not null);
create policy room_tracks_delete on public.room_tracks for delete using (auth.uid() is not null);

-- reacciones
drop policy if exists reactions_read   on public.message_reactions;
drop policy if exists reactions_insert on public.message_reactions;
drop policy if exists reactions_delete on public.message_reactions;
create policy reactions_read   on public.message_reactions for select using (true);
create policy reactions_insert on public.message_reactions for insert with check (user_id = auth.uid());
create policy reactions_delete on public.message_reactions for delete using (user_id = auth.uid());

-- follows
drop policy if exists follows_read   on public.follows;
drop policy if exists follows_insert on public.follows;
drop policy if exists follows_delete on public.follows;
create policy follows_read   on public.follows for select using (true);
create policy follows_insert on public.follows for insert with check (follower_id = auth.uid());
create policy follows_delete on public.follows for delete using (follower_id = auth.uid());

-- ---------------------------------------------------------------------
-- 5. Realtime (chat, salas, canciones de sala, reacciones)
-- ---------------------------------------------------------------------
do $$
declare t text;
begin
  foreach t in array array['messages','rooms','room_tracks','message_reactions'] loop
    if not exists (
      select 1 from pg_publication_tables
      where pubname = 'supabase_realtime' and schemaname = 'public' and tablename = t
    ) then
      execute format('alter publication supabase_realtime add table public.%I', t);
    end if;
  end loop;
end $$;

-- ---------------------------------------------------------------------
-- 6. Perfiles para usuarios que YA existían antes del trigger
-- ---------------------------------------------------------------------
insert into public.users (id, handle, name)
select u.id, coalesce(split_part(u.email,'@',1), ''), coalesce(u.raw_user_meta_data->>'full_name','')
from auth.users u
on conflict (id) do nothing;
