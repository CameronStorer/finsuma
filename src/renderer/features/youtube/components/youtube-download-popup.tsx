import { closeAllModals, openModal } from '@mantine/modals';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';

import {
    getDownloadTarget,
    getYoutubeConfig,
    hasServerLogin,
    isSoundcloudUrl,
    youtubeApi,
    YoutubeMode,
} from '/@/renderer/features/youtube/api/youtube-api';
import { YoutubeServiceSettings } from '/@/renderer/features/youtube/components/youtube-download-form';
import { youtubeJobsQueryKey } from '/@/renderer/features/youtube/hooks/use-youtube-jobs';
import { Button } from '/@/shared/components/button/button';
import { Group } from '/@/shared/components/group/group';
import { SegmentedControl } from '/@/shared/components/segmented-control/segmented-control';
import { Skeleton } from '/@/shared/components/skeleton/skeleton';
import { Stack } from '/@/shared/components/stack/stack';
import { TextInput } from '/@/shared/components/text-input/text-input';
import { Text } from '/@/shared/components/text/text';
import { toast } from '/@/shared/components/toast/toast';

const COVER_SIZE = 96;

const YoutubeDownloadPopup = ({ url }: { url: string }) => {
    const queryClient = useQueryClient();
    const target = getDownloadTarget(url);
    const singleLabel = isSoundcloudUrl(url) ? 'Track' : 'Video';
    const [hasToken, setHasToken] = useState(
        () => Boolean(getYoutubeConfig().token) || hasServerLogin(),
    );
    const [mode, setMode] = useState<YoutubeMode>(
        target.isPlaylistPage || !target.hasVideo ? 'playlist' : 'single',
    );
    const [artist, setArtist] = useState('');
    const [title, setTitle] = useState('');
    const [submitting, setSubmitting] = useState(false);

    const preview = useQuery({
        enabled: hasToken && target.ok && mode === 'single',
        queryFn: () => youtubeApi.preview(url),
        queryKey: ['youtube', 'preview', url],
        retry: false,
        staleTime: Infinity,
    });

    useEffect(() => {
        if (preview.data) {
            setArtist(preview.data.artist);
            setTitle(preview.data.title);
        }
    }, [preview.data]);

    if (!target.ok) {
        return (
            <Stack gap="md">
                <Text>
                    Open a {singleLabel.toLowerCase()} or a playlist first, then press the download
                    button.
                </Text>
                <Group justify="flex-end">
                    <Button onClick={() => closeAllModals()} variant="default">
                        OK
                    </Button>
                </Group>
            </Stack>
        );
    }

    if (!hasToken) {
        return (
            <Stack gap="md">
                <Text isMuted size="sm">
                    One-time setup: connect Finsuma to the download service on your server.
                </Text>
                <YoutubeServiceSettings onSaved={() => setHasToken(true)} />
            </Stack>
        );
    }

    const canPlaylist = target.hasList;
    const canSingle = target.hasVideo;

    const handleDownload = async () => {
        setSubmitting(true);
        try {
            const tags =
                mode === 'single' && preview.data
                    ? {
                          artist: artist !== preview.data.artist ? artist : undefined,
                          title: title !== preview.data.title ? title : undefined,
                      }
                    : undefined;
            await youtubeApi.download(url, mode, tags);
            queryClient.invalidateQueries({ queryKey: youtubeJobsQueryKey });
            toast.info({
                message:
                    mode === 'playlist'
                        ? 'Downloading the whole playlist to your server'
                        : `Downloading ${title || 'song'} to your server`,
                title: 'Download started',
            });
            closeAllModals();
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            if (message.toLowerCase().includes('token')) setHasToken(false);
            toast.error({ message, title: "Couldn't start download" });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Stack gap="md">
            <SegmentedControl
                data={[
                    { disabled: !canSingle, label: singleLabel, value: 'single' },
                    { disabled: !canPlaylist, label: 'Playlist', value: 'playlist' },
                ]}
                fullWidth
                onChange={(v) => setMode(v as YoutubeMode)}
                value={mode}
            />

            {mode === 'single' ? (
                <Group align="flex-start" gap="md" wrap="nowrap">
                    {preview.data?.thumbnail ? (
                        <img
                            alt=""
                            src={preview.data.thumbnail}
                            style={{
                                borderRadius: 6,
                                flexShrink: 0,
                                height: COVER_SIZE,
                                objectFit: 'cover',
                                width: COVER_SIZE,
                            }}
                        />
                    ) : (
                        <Skeleton height={COVER_SIZE} width={COVER_SIZE} />
                    )}
                    <Stack gap="xs" style={{ flex: 1 }}>
                        <TextInput
                            disabled={!preview.data}
                            label="Artist"
                            onChange={(e) => setArtist(e.currentTarget.value)}
                            value={artist}
                        />
                        <TextInput
                            disabled={!preview.data}
                            label="Title"
                            onChange={(e) => setTitle(e.currentTarget.value)}
                            value={title}
                        />
                        {preview.error && (
                            <Text isMuted size="xs">
                                Couldn&apos;t read details ({(preview.error as Error).message}). The
                                song will still download with automatic tags.
                            </Text>
                        )}
                    </Stack>
                </Group>
            ) : (
                <Text isMuted size="sm">
                    Every song in this playlist is saved as an MP3 in a folder named after the
                    playlist, each with its own artist, title and cover.
                </Text>
            )}

            <Group justify="flex-end">
                <Button onClick={() => closeAllModals()} variant="default">
                    Cancel
                </Button>
                <Button
                    disabled={mode === 'single' && preview.isLoading}
                    loading={submitting}
                    onClick={handleDownload}
                    variant="filled"
                >
                    Download
                </Button>
            </Group>
        </Stack>
    );
};

export const openYoutubeDownloadPopup = (url: string) => {
    openModal({
        children: <YoutubeDownloadPopup url={url} />,
        size: 'md',
        title: 'Save to your music server',
    });
};
