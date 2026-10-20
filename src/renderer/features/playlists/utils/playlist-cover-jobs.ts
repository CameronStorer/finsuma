// Playlist cover sources other than a file: generated gradients and the playlist's
// most frequent artist. Batch jobs run outside any component, so closing the dialog
// (or switching pages) doesn't stop them; progress shows in a notification.

import { api } from '/@/renderer/api';
import { queryKeys } from '/@/renderer/api/query-keys';
import {
    gradientCoverBlob,
    GradientSpec,
    initialsFor,
    randomGradient,
} from '/@/renderer/features/playlists/utils/gradient-cover';
import { queryClient } from '/@/renderer/lib/react-query';
import { toast } from '/@/shared/components/toast/toast';
import { LibraryItem, Playlist, RelatedArtist, Song } from '/@/shared/types/domain-types';

// default = remove the custom cover so the server shows its usual grid of 4 album covers.
export type CoverSource = 'artist' | 'default' | 'gradient' | 'song';

const COVER_SIZE = 600;
const JOB_TOAST_ID = 'playlist-cover-job';
let jobRunning = false;

export const isCoverJobRunning = () => jobRunning;

const IGNORED_ARTISTS = /^(various artists|unknown artist|\[unknown artist\])$/i;

// The artist credited first on the most songs in the playlist.
export const findTopArtist = async (serverId: string, playlistId: string) => {
    const songs = await api.controller.getPlaylistSongList({
        apiClientProps: { serverId },
        query: { id: playlistId },
    });
    const counts = new Map<string, { artist: RelatedArtist; count: number }>();
    for (const song of songs.items) {
        const artist = song.artists?.[0] ?? song.albumArtists?.[0];
        if (!artist?.id || IGNORED_ARTISTS.test(artist.name)) continue;
        const entry = counts.get(artist.id) ?? { artist, count: 0 };
        entry.count += 1;
        counts.set(artist.id, entry);
    }
    let top: undefined | { artist: RelatedArtist; count: number };
    for (const entry of counts.values()) {
        if (!top || entry.count > top.count) top = entry;
    }
    return top?.artist ?? null;
};

const squareCrop = async (blob: Blob): Promise<Blob> => {
    const bitmap = await createImageBitmap(blob);
    const side = Math.min(bitmap.width, bitmap.height);
    const canvas = document.createElement('canvas');
    canvas.width = COVER_SIZE;
    canvas.height = COVER_SIZE;
    const ctx = canvas.getContext('2d');
    if (!ctx) throw new Error('Canvas unavailable');
    ctx.drawImage(
        bitmap,
        (bitmap.width - side) / 2,
        (bitmap.height - side) / 2,
        side,
        side,
        0,
        0,
        COVER_SIZE,
        COVER_SIZE,
    );
    bitmap.close();
    return new Promise((resolve, reject) =>
        canvas.toBlob(
            (b) => (b ? resolve(b) : reject(new Error('Could not crop'))),
            'image/jpeg',
            0.9,
        ),
    );
};

// A square photo of the playlist's top artist, or null if there is no usable artist/photo.
export const topArtistCoverBlob = async (serverId: string, playlistId: string) => {
    const artist = await findTopArtist(serverId, playlistId);
    if (!artist) return null;
    const request = api.controller.getImageRequest({
        apiClientProps: { serverId },
        query: { id: artist.id, itemType: LibraryItem.ALBUM_ARTIST, size: COVER_SIZE },
    });
    if (!request) return null;
    const res = await fetch(request.url, {
        credentials: request.credentials,
        headers: request.headers,
    });
    if (!res.ok) return null;
    const blob = await res.blob();
    if (!blob.type.startsWith('image/')) return null;
    return { artist, blob: await squareCrop(blob) };
};

// The playlist's most-played song (first song if nothing has plays yet).
export const findTopSong = async (serverId: string, playlistId: string) => {
    const songs = await api.controller.getPlaylistSongList({
        apiClientProps: { serverId },
        query: { id: playlistId },
    });
    let top: Song | undefined;
    for (const song of songs.items) {
        if (!top || (song.playCount ?? 0) > (top.playCount ?? 0)) top = song;
    }
    return top ?? null;
};

// The album cover of one song, cropped square.
export const songCoverBlob = async (serverId: string, song: Song) => {
    const request = api.controller.getImageRequest({
        apiClientProps: { serverId },
        query: {
            id: song.imageId || song.albumId || song.id,
            itemType: LibraryItem.SONG,
            size: COVER_SIZE,
        },
    });
    if (!request) return null;
    const res = await fetch(request.url, {
        credentials: request.credentials,
        headers: request.headers,
    });
    if (!res.ok) return null;
    const blob = await res.blob();
    if (!blob.type.startsWith('image/')) return null;
    return squareCrop(blob);
};

export const topSongCoverBlob = async (serverId: string, playlistId: string) => {
    const song = await findTopSong(serverId, playlistId);
    if (!song) return null;
    const blob = await songCoverBlob(serverId, song);
    return blob ? { blob, song } : null;
};

export const resetToDefaultCover = (serverId: string, playlistId: string) =>
    api.controller.deletePlaylistImage({ apiClientProps: { serverId }, query: { id: playlistId } });

const uploadCover = async (serverId: string, playlistId: string, blob: Blob) => {
    await api.controller.uploadPlaylistImage({
        apiClientProps: { serverId },
        body: { image: new Uint8Array(await blob.arrayBuffer()) },
        query: { id: playlistId },
    });
};

const plural = (n: number, word: string) => `${n} ${word}${n === 1 ? '' : 's'}`;

export const startCoverJob = ({
    playlists,
    serverId,
    source,
    specs,
}: {
    playlists: Playlist[];
    serverId: string;
    source: CoverSource;
    specs?: Record<string, GradientSpec>;
}) => {
    if (jobRunning) {
        toast.warn({ message: 'A cover job is already running. It will finish shortly.' });
        return;
    }
    jobRunning = true;
    const total = playlists.length;
    toast.show({
        autoClose: false,
        id: JOB_TOAST_ID,
        loading: true,
        message: `Updating covers: 0 of ${total}`,
        type: 'info',
        withCloseButton: false,
    });

    void (async () => {
        let failed = 0;
        let fellBack = 0;
        for (const [i, playlist] of playlists.entries()) {
            try {
                if (source === 'default') {
                    if (playlist.uploadedImage) await resetToDefaultCover(serverId, playlist.id);
                    toast.update({
                        id: JOB_TOAST_ID,
                        message: `Updating covers: ${i + 1} of ${total}`,
                    });
                    continue;
                }
                let blob: Blob | null = null;
                if (source === 'artist') {
                    blob = (await topArtistCoverBlob(serverId, playlist.id))?.blob ?? null;
                    if (!blob) fellBack += 1;
                } else if (source === 'song') {
                    blob = (await topSongCoverBlob(serverId, playlist.id))?.blob ?? null;
                    if (!blob) fellBack += 1;
                }
                blob ??= await gradientCoverBlob(
                    specs?.[playlist.id] ?? randomGradient(),
                    initialsFor(playlist.name),
                );
                await uploadCover(serverId, playlist.id, blob);
            } catch {
                failed += 1;
            }
            toast.update({
                id: JOB_TOAST_ID,
                message: `Updating covers: ${i + 1} of ${total}`,
            });
        }

        await queryClient.invalidateQueries({ queryKey: queryKeys.playlists.root(serverId) });
        jobRunning = false;

        const done = total - failed;
        const notes = [
            fellBack
                ? `${plural(fellBack, 'playlist')} had no usable picture, so got a gradient`
                : '',
            failed ? `${failed} failed (you can only change playlists you own)` : '',
        ].filter(Boolean);
        toast.update({
            autoClose: 8000,
            id: JOB_TOAST_ID,
            loading: false,
            message: `${plural(done, 'cover')} updated.${notes.length ? ` ${notes.join('; ')}.` : ''}`,
            withCloseButton: true,
        });
    })();
};
