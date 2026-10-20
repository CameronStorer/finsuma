// Playlist ratings and pins. Navidrome can't store or return either for playlists, so
// Finsuma keeps them itself: on the Finsuma server (shared by every Finsuma install)
// with a local copy for when the server can't be reached.

import { youtubeApi } from '/@/renderer/features/youtube/api/youtube-api';
import {
    Playlist,
    PlaylistListArgs,
    PlaylistListResponse,
    PlaylistListSort,
    SortOrder,
} from '/@/shared/types/domain-types';

export type PlaylistPref = { pinned?: boolean; rating?: number };
type PrefMap = Record<string, PlaylistPref>;

const LOCAL_KEY = 'finsuma_playlist_prefs';
const SYNC_INTERVAL_MS = 60_000;

let prefs: PrefMap = readLocal();
let lastSync = 0;
let syncing: null | Promise<void> = null;

function readLocal(): PrefMap {
    try {
        return JSON.parse(localStorage.getItem(LOCAL_KEY) || '{}') as PrefMap;
    } catch {
        return {};
    }
}

const writeLocal = () => {
    try {
        localStorage.setItem(LOCAL_KEY, JSON.stringify(prefs));
    } catch {
        // storage blocked; the in-memory copy still works this session
    }
};

// Pull the shared copy from the Finsuma server at most once a minute.
const syncFromServer = () => {
    if (Date.now() - lastSync < SYNC_INTERVAL_MS) return Promise.resolve();
    syncing ??= youtubeApi
        .prefs()
        .then((res) => {
            prefs = { ...prefs, ...res.playlists };
            // Drop local entries the server no longer has (cleared elsewhere).
            for (const id of Object.keys(prefs)) if (!res.playlists[id]) delete prefs[id];
            writeLocal();
        })
        .catch(() => undefined) // offline / no token: keep the local copy
        .finally(() => {
            lastSync = Date.now();
            syncing = null;
        });
    return syncing;
};

export const getPlaylistPref = (id: string): PlaylistPref => prefs[id] ?? {};

export const setPlaylistPref = async (id: string, changes: Partial<PlaylistPref>) => {
    const next = { ...prefs[id], ...changes };
    if (!next.rating) delete next.rating;
    if (!next.pinned) delete next.pinned;
    if (Object.keys(next).length) prefs[id] = next;
    else delete prefs[id];
    writeLocal();
    try {
        await youtubeApi.setPlaylistPref(id, {
            pinned: Boolean(changes.pinned ?? next.pinned),
            rating: changes.rating ?? next.rating ?? 0,
        });
    } catch {
        // Saved locally; the server copy catches up next time it's reachable and changed.
    }
};

const withPrefs = (playlist: Playlist): Playlist => {
    const pref = prefs[playlist.id];
    return { ...playlist, pinned: Boolean(pref?.pinned), userRating: pref?.rating ?? null };
};

// Wraps the server's playlist list: adds ratings and pins, puts pinned playlists first,
// and handles sorting by rating (which no music server supports for playlists).
export const getPlaylistListWithPrefs = async (
    args: PlaylistListArgs,
    fetchList: (args: PlaylistListArgs) => Promise<PlaylistListResponse> | undefined,
): Promise<PlaylistListResponse> => {
    await syncFromServer();
    const { query } = args;
    const byRating = query.sortBy === PlaylistListSort.RATING;

    // Fetch everything (playlist counts are small) so pinning and rating sort work across pages.
    const res = await fetchList({
        ...args,
        query: {
            ...query,
            limit: undefined,
            sortBy: byRating ? PlaylistListSort.NAME : query.sortBy,
            sortOrder: byRating ? SortOrder.ASC : query.sortOrder,
            startIndex: 0,
        },
    });
    if (!res) throw new Error('Could not load playlists');

    let items = res.items.map(withPrefs);
    if (byRating) {
        const dir = query.sortOrder === SortOrder.ASC ? 1 : -1;
        items = [...items].sort((a, b) => dir * ((a.userRating ?? 0) - (b.userRating ?? 0)));
    }
    // Stable: pinned first, everything else keeps its order.
    items = [...items.filter((p) => p.pinned), ...items.filter((p) => !p.pinned)];

    const start = query.startIndex || 0;
    const end = query.limit ? start + query.limit : undefined;
    return {
        ...res,
        items: items.slice(start, end),
        startIndex: start,
        totalRecordCount: items.length,
    };
};
