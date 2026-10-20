# Finsuma backlog

Small items noted while building the main plan. Newest first.

## Desktop

- [ ] **Sidebar playlists: sort, filter and pinning.** Add sort options (name, rating, recently
  updated, song count) and a quick filter box to the sidebar playlist list, plus pin/unpin from its
  right-click menu. The sidebar already loads playlists through the shared API wrapper, so pinned
  playlists should already appear first there; this item is about controls in the sidebar itself.
  (`src/renderer/features/sidebar/components/sidebar-playlist-list.tsx`)
- [ ] **Favoriting a playlist fails** with "required id parameter missing". Navidrome's star API
  only covers songs, albums and artists, so store playlist favorites in Finsuma playlist prefs,
  like ratings and pins (`src/renderer/features/playlists/utils/playlist-prefs.ts` and the
  server's `/prefs`).

## Android (main plan)

- [x] Home layout like desktop (Discovery, Playlists, Genres, Random albums, Recently added,
  New releases, Recently played, Most played)
- [ ] Navigation trimmed to Home, Playlists, Library (tracks/artists with sort, filter, search)
- [ ] Player logic parity with desktop (Auto DJ, queue behaviour)
