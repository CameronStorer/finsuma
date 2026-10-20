import { useAuthStore } from '/@/renderer/store';

// Talks to the yt-download service on the server (yt-download/server.py).

export type YoutubeJob = {
    created: number;
    error: null | string;
    files: string[];
    finished?: number;
    id: string;
    mode: YoutubeMode;
    progress: number;
    status: 'done' | 'failed' | 'queued' | 'running';
    title: null | string;
    url: string;
};

export type YoutubeMode = 'playlist' | 'single';

export type YoutubeServiceConfig = {
    token: string;
    url: string;
};

const CONFIG_KEY = 'finsuma_youtube_service';

// Optional extra address to try, e.g. VITE_FINSUMA_SERVICE_URL=http://192.168.1.10:3200 in .env.local
const FALLBACK_SERVICE_URL: string | undefined = import.meta.env.VITE_FINSUMA_SERVICE_URL;

// The download service runs on the same machine as the music server, on port 3200.
const defaultServiceUrl = () => {
    try {
        const serverUrl = useAuthStore.getState().currentServer?.url;
        if (serverUrl) return `http://${new URL(serverUrl).hostname}:3200`;
    } catch {
        // fall through
    }
    return FALLBACK_SERVICE_URL ?? 'http://localhost:3200';
};

// The service can share the music server URL under /finsuma (reverse proxy or tunnel path rule).
const sameUrlAsServer = () => {
    const serverUrl = useAuthStore.getState().currentServer?.url;
    return serverUrl ? `${serverUrl.replace(/\/+$/, '')}/finsuma` : undefined;
};

const isLocalhost = (url: string) => {
    try {
        return ['127.0.0.1', 'localhost'].includes(new URL(url).hostname);
    } catch {
        return true;
    }
};

export const getYoutubeConfig = (): YoutubeServiceConfig => {
    try {
        const saved = JSON.parse(localStorage.getItem(CONFIG_KEY) || '{}');
        const url = saved.url && !isLocalhost(saved.url) ? saved.url : defaultServiceUrl();
        return { token: saved.token || '', url };
    } catch {
        return { token: '', url: defaultServiceUrl() };
    }
};

export const saveYoutubeConfig = (config: YoutubeServiceConfig) => {
    resolvedServiceUrl = null;
    try {
        localStorage.setItem(
            CONFIG_KEY,
            JSON.stringify({
                token: config.token.trim(),
                url: config.url.trim().replace(/\/+$/, ''),
            }),
        );
    } catch {
        // storage blocked; config just won't persist
    }
};

// Try the saved address, then the music server host, then the home LAN address,
// and remember the first one that answers. Handles Navidrome being reached
// through a public domain while the download service is LAN-only.
let resolvedServiceUrl: null | string = null;

const probe = async (url: string) => {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 2500);
    try {
        const res = await fetch(`${url}/health`, { signal: controller.signal });
        return res.ok;
    } catch {
        return false;
    } finally {
        clearTimeout(timer);
    }
};

const resolveServiceUrl = async () => {
    if (resolvedServiceUrl) return resolvedServiceUrl;
    const candidates = [
        ...new Set(
            [
                getYoutubeConfig().url,
                sameUrlAsServer(),
                defaultServiceUrl(),
                FALLBACK_SERVICE_URL,
            ].filter((u): u is string => Boolean(u)),
        ),
    ];
    for (const candidate of candidates) {
        if (await probe(candidate)) {
            resolvedServiceUrl = candidate;
            // Remember the address that works so it is tried first next time.
            const config = getYoutubeConfig();
            if (config.url !== candidate) saveYoutubeConfig({ ...config, url: candidate });
            resolvedServiceUrl = candidate;
            return candidate;
        }
    }
    throw new Error(
        `Can't reach the download service (tried ${candidates.join(', ')}). Are you on your home network?`,
    );
};

const request = async <T>(path: string, init?: RequestInit): Promise<T> => {
    const { token } = getYoutubeConfig();
    const url = await resolveServiceUrl();
    let res: Response;
    try {
        res = await fetch(`${url}${path}`, {
            ...init,
            headers: { 'Content-Type': 'application/json', 'X-Token': token, ...init?.headers },
        });
    } catch {
        resolvedServiceUrl = null; // try the candidates again next time
        throw new Error(`Can't reach the download service at ${url}`);
    }
    const body = await res.json().catch(() => ({}));
    if (!res.ok) {
        if (res.status === 401) throw new Error('Wrong download token. Check YouTube settings.');
        throw new Error(body.error || `Download service error (${res.status})`);
    }
    return body as T;
};

export type YoutubePreview = {
    album: string;
    artist: string;
    duration: null | number;
    thumbnail: null | string;
    title: string;
};

export type YoutubeTagOverrides = { album?: string; artist?: string; title?: string };

export const youtubeApi = {
    download: (url: string, mode: YoutubeMode, tags?: YoutubeTagOverrides) =>
        request<YoutubeJob>('/download', {
            body: JSON.stringify({ mode, url, ...tags }),
            method: 'POST',
        }),
    jobs: () => request<{ jobs: YoutubeJob[] }>('/jobs').then((r) => r.jobs),
    prefs: () =>
        request<{ playlists: Record<string, { pinned?: boolean; rating?: number }> }>('/prefs'),
    preview: (url: string) => request<YoutubePreview>(`/preview?url=${encodeURIComponent(url)}`),
    setPlaylistPref: (id: string, changes: { pinned?: boolean; rating?: number }) =>
        request<{ pinned?: boolean; rating?: number }>(
            `/prefs/playlists/${encodeURIComponent(id)}`,
            { body: JSON.stringify(changes), method: 'PUT' },
        ),
};

const YOUTUBE_HOSTS = [
    'm.youtube.com',
    'music.youtube.com',
    'www.youtube.com',
    'youtu.be',
    'youtube.com',
];
const SOUNDCLOUD_HOSTS = [
    'm.soundcloud.com',
    'on.soundcloud.com',
    'soundcloud.com',
    'www.soundcloud.com',
];

export const isSoundcloudUrl = (value: string) => {
    try {
        const { hostname, protocol } = new URL(value.trim());
        return protocol === 'https:' && SOUNDCLOUD_HOSTS.includes(hostname);
    } catch {
        return false;
    }
};

// Any link the download service accepts (YouTube or SoundCloud).
export const isYoutubeUrl = (value: string) => {
    try {
        const { hostname, protocol } = new URL(value.trim());
        return protocol === 'https:' && YOUTUBE_HOSTS.includes(hostname);
    } catch {
        return false;
    }
};

export const isSupportedUrl = (value: string) => isYoutubeUrl(value) || isSoundcloudUrl(value);

// Playlist pages default to the whole playlist; everything else to one song.
export const guessMode = (value: string): YoutubeMode => {
    try {
        if (getDownloadTarget(value.trim()).isPlaylistPage) return 'playlist';
        return 'single';
    } catch {
        return 'single';
    }
};

export const hasPlaylistParam = (value: string) => {
    try {
        return new URL(value.trim()).searchParams.has('list');
    } catch {
        return false;
    }
};

export type DownloadTarget = {
    hasList: boolean;
    hasVideo: boolean;
    isPlaylistPage: boolean;
    ok: boolean;
};

const NO_TARGET: DownloadTarget = {
    hasList: false,
    hasVideo: false,
    isPlaylistPage: false,
    ok: false,
};

// SoundCloud pages that look like /<user>/<track> but aren't tracks.
const SOUNDCLOUD_NOT_USERS = new Set([
    'charts',
    'discover',
    'feed',
    'jobs',
    'messages',
    'mobile',
    'notifications',
    'pages',
    'people',
    'popular',
    'search',
    'settings',
    'signin',
    'stations',
    'stream',
    'tags',
    'upload',
    'you',
]);
const SOUNDCLOUD_NOT_TRACKS = new Set([
    'albums',
    'comments',
    'followers',
    'following',
    'likes',
    'popular-tracks',
    'reposts',
    'sets',
    'spotlight',
    'tracks',
]);

const getSoundcloudTarget = (url: URL): DownloadTarget => {
    const parts = url.pathname.split('/').filter(Boolean);
    if (url.hostname === 'on.soundcloud.com') {
        return { ...NO_TARGET, hasVideo: parts.length > 0, ok: parts.length > 0 };
    }
    if (parts.length === 0 || SOUNDCLOUD_NOT_USERS.has(parts[0])) return NO_TARGET;
    const isSet = parts.length >= 3 && parts[1] === 'sets';
    const isTrack = parts.length === 2 && !SOUNDCLOUD_NOT_TRACKS.has(parts[1]);
    return { hasList: isSet, hasVideo: isTrack, isPlaylistPage: isSet, ok: isSet || isTrack };
};

const getYoutubeTarget = (url: URL): DownloadTarget => {
    const isPlaylistPage = url.pathname.startsWith('/playlist');
    const hasVideo =
        (url.pathname === '/watch' && url.searchParams.has('v')) ||
        url.pathname.startsWith('/shorts/') ||
        (url.hostname === 'youtu.be' && url.pathname.length > 1);
    const hasList = url.searchParams.has('list');
    return { hasList, hasVideo, isPlaylistPage, ok: hasVideo || (isPlaylistPage && hasList) };
};

// What the current page lets you download: a single song, a playlist, or nothing.
export const getDownloadTarget = (value: string): DownloadTarget => {
    try {
        if (isSoundcloudUrl(value)) return getSoundcloudTarget(new URL(value));
        if (isYoutubeUrl(value)) return getYoutubeTarget(new URL(value));
        return NO_TARGET;
    } catch {
        return NO_TARGET;
    }
};
