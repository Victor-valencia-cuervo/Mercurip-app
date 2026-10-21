-- Verificación: ejecuta esto DESPUÉS de mercurio_supabase.sql.
-- 1) Debe devolver las 8 tablas con rls = true
select c.relname as tabla, c.relrowsecurity as rls
from pg_class c join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public'
  and c.relname in ('users','playlists','playlist_tracks','rooms','messages','room_tracks','message_reactions','follows')
order by 1;

-- 2) Cantidad de políticas por tabla (todas deben tener al menos 1)
select tablename, count(*) as politicas from pg_policies where schemaname = 'public' group by 1 order by 1;

-- 3) Tablas en Realtime (deben salir 4)
select tablename from pg_publication_tables where pubname = 'supabase_realtime' and schemaname = 'public' order by 1;
