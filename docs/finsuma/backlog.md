# Finsuma backlog

Small items noted while building the main plan. Newest first.

## Everywhere

- [ ] **Low-latency pass (apps and server).** Measure, then trim: cache library lists, avoid
  re-fetching on every screen, smaller image sizes in lists, faster startup, and cheaper server
  calls (e.g. the Finsuma server reads `/prefs` from disk on each request).
- [ ] **SoundCloud bot detection** ("You have been blocked" on SoundCloud's sign-in, from its
  DataDome protection) in the in-app browsers. Browsing and downloading work without signing in.

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

## Android

- [ ] **Tracks page: the track number seems to count up when a song is tapped.** Probably the
  now-playing indicator in the song row (`SongHorizontalAdapter` playback state); the all-tracks
  list also shows album track numbers, which mean little there.
- [ ] **Local database cache** for the library (Tempus already uses Room): keep the Tracks list
  and home rows on the device so they open instantly and refresh in the background.
- [ ] **Sign in to YouTube/SoundCloud from Settings**, so the in-app browsers are ready. Google
  blocks sign-in inside in-app browsers, so this mainly helps SoundCloud.
- [ ] **Settings parity with desktop, where reasonable.** Bring over the desktop settings that
  make sense on a phone (playback/Auto DJ options, home sections, playlist behaviour, theme) and
  leave out desktop-only ones (window, MPV, global shortcuts, Discord).
- [ ] Download screen: show cover/artist/title preview and allow editing, like desktop.
- [ ] Show Finsuma playlist ratings, pins and favorites (from the Finsuma server's `/prefs`).

### Main plan

- [x] Home layout like desktop (Discovery, shuffled Playlists, Genres, Random albums, Recently
  added, New releases, Recently played, Most played); pull to refresh
- [x] Navigation: Home, Playlists, Tracks, Download (YouTube/SoundCloud in-app, saves to the
  Finsuma server); Radio/Podcasts from a toolbar button; compact icon-only bar
- [x] Desktop dark colour scheme and blue accent
- [x] Feishin-style song menu, previous button on the mini player, ratings shown, grid views
- [x] Auto DJ like desktop: similar songs, then genre, artist and random; toggle on the player, options in Settings
