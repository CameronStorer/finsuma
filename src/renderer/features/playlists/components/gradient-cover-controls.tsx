import { useEffect, useState } from 'react';

import {
    cleanLetters,
    gradientCoverFile,
    GradientSpec,
    initialsFor,
    MAX_COVER_LETTERS,
    randomGradient,
} from '/@/renderer/features/playlists/utils/gradient-cover';
import {
    topArtistCoverBlob,
    topSongCoverBlob,
} from '/@/renderer/features/playlists/utils/playlist-cover-jobs';
import { useCurrentServerId } from '/@/renderer/store';
import { Button } from '/@/shared/components/button/button';
import { Group } from '/@/shared/components/group/group';
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
    const [loading, setLoading] = useState<'artist' | 'song' | null>(null);

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
            <Button
                fullWidth
                onClick={() => {
                    setSpec(null);
                    onUseDefault();
                }}
                size="compact-sm"
                variant="subtle"
            >
                Default (4 albums)
            </Button>
        </Stack>
    );
};
