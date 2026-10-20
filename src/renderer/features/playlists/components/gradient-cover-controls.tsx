import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';

import { api } from '/@/renderer/api';
import {
    cleanLetters,
    gradientCoverFile,
    GradientSpec,
    initialsFor,
    MAX_COVER_LETTERS,
    randomGradient,
} from '/@/renderer/features/playlists/utils/gradient-cover';
import {
    songCoverBlob,
    topArtistCoverBlob,
    topSongCoverBlob,
} from '/@/renderer/features/playlists/utils/playlist-cover-jobs';
import { useCurrentServerId } from '/@/renderer/store';
import { Button } from '/@/shared/components/button/button';
import { Group } from '/@/shared/components/group/group';
import { Select } from '/@/shared/components/select/select';
import { Stack } from '/@/shared/components/stack/stack';
import { TextInput } from '/@/shared/components/text-input/text-input';
import { toast } from '/@/shared/components/toast/toast';

interface GradientCoverControlsProps {
    onGenerated: (file: File) => void;
    onUseDefault: () => void;
    playlistId: string;
    playlistName: string;
}

// Sits under the cover in the edit-playlist dialog. The generated image becomes the
// pending cover, so it is uploaded together with the rest of the form on Save.
export const GradientCoverControls = ({
    onGenerated,
    onUseDefault,
    playlistId,
    playlistName,
}: GradientCoverControlsProps) => {
    const serverId = useCurrentServerId();
    const [spec, setSpec] = useState<GradientSpec | null>(null);
    const [letters, setLetters] = useState(() => initialsFor(playlistName));
    const [loading, setLoading] = useState<'artist' | 'pick' | 'song' | null>(null);
    const [pickerOpened, setPickerOpened] = useState(false);

    // Playlist songs for the "Choose song" picker, loaded the first time it opens.
    const songsQuery = useQuery({
        enabled: pickerOpened && Boolean(serverId),
        queryFn: () =>
            api.controller.getPlaylistSongList({
                apiClientProps: { serverId: serverId! },
                query: { id: playlistId },
            }),
        queryKey: ['playlist-cover-songs', serverId, playlistId],
        staleTime: 60_000,
    });
    const songOptions = useMemo(() => {
        const seen = new Set<string>();
        return (songsQuery.data?.items ?? [])
            .filter((song) => {
                if (seen.has(song.id)) return false;
                seen.add(song.id);
                return true;
            })
            .map((song) => ({
                label: [song.name, song.artists?.[0]?.name].filter(Boolean).join(' · '),
                value: song.id,
            }));
    }, [songsQuery.data]);

    const pickSong = async (songId: null | string) => {
        const song = songsQuery.data?.items.find((s) => s.id === songId);
        if (!song || !serverId) return;
        setLoading('pick');
        try {
            const blob = await songCoverBlob(serverId, song);
            if (!blob) {
                toast.warn({ message: `${song.name} has no cover art.` });
                return;
            }
            setSpec(null);
            onGenerated(new File([blob], 'cover.jpg', { type: 'image/jpeg' }));
            toast.info({ message: `Using the cover of ${song.name}. Save to apply.` });
        } catch (err) {
            toast.error({ message: err instanceof Error ? err.message : String(err) });
        } finally {
            setLoading(null);
        }
    };

    useEffect(() => {
        if (!spec) return;
        let cancelled = false;
        gradientCoverFile(spec, letters).then((file) => {
            if (!cancelled) onGenerated(file);
        });
        return () => {
            cancelled = true;
        };
        // onGenerated is a fresh closure each render; re-render only on spec/letters changes
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [spec, letters]);

    const applyPicture = async (kind: 'artist' | 'song') => {
        if (!serverId) return;
        setLoading(kind);
        try {
            const result =
                kind === 'artist'
                    ? await topArtistCoverBlob(serverId, playlistId)
                    : await topSongCoverBlob(serverId, playlistId);
            if (!result) {
                toast.warn({
                    message: `No ${kind === 'artist' ? 'artist photo' : 'song cover'} found.`,
                });
                return;
            }
            setSpec(null); // stop the gradient from overriding the picture
            onGenerated(new File([result.blob], 'cover.jpg', { type: 'image/jpeg' }));
            const name = 'artist' in result ? result.artist.name : result.song.name;
            toast.info({ message: `Using ${name}. Save to apply.` });
        } catch (err) {
            toast.error({ message: err instanceof Error ? err.message : String(err) });
        } finally {
            setLoading(null);
        }
    };

    return (
        <Stack gap={6} style={{ width: 240 }}>
            <Button
                fullWidth
                onClick={() => setSpec(randomGradient())}
                size="compact-sm"
                variant="default"
            >
                {spec ? 'Another gradient' : 'Random gradient'}
            </Button>
            <TextInput
                aria-label="Cover letters"
                onChange={(e) => {
                    setLetters(cleanLetters(e.currentTarget.value));
                    if (!spec) setSpec(randomGradient());
                }}
                placeholder={`Letters (up to ${MAX_COVER_LETTERS})`}
                size="xs"
                value={letters}
            />
            <Group gap={6} grow>
                <Button
                    loading={loading === 'artist'}
                    onClick={() => applyPicture('artist')}
                    size="compact-sm"
                    variant="default"
                >
                    Top artist
                </Button>
                <Button
                    loading={loading === 'song'}
                    onClick={() => applyPicture('song')}
                    size="compact-sm"
                    variant="default"
                >
                    Top song
                </Button>
            </Group>
            <Select
                aria-label="Choose a song for the cover"
                data={songOptions}
                disabled={loading === 'pick'}
                nothingFoundMessage={songsQuery.isLoading ? 'Loading songs…' : 'No songs found'}
                onChange={pickSong}
                onDropdownOpen={() => setPickerOpened(true)}
                placeholder="Choose song…"
                searchable
                size="xs"
                value={null}
            />
            <Button
                fullWidth
                onClick={() => {
                    setSpec(null);
                    onUseDefault();
                    toast.info({
                        message:
                            'The custom cover will be removed when you save (back to the 4-album grid).',
                    });
                }}
                size="compact-sm"
                variant="subtle"
            >
                Default (4 albums)
            </Button>
        </Stack>
    );
};
