import { openModal } from '@mantine/modals';
import { useQueryClient } from '@tanstack/react-query';
import { FormEvent, useState } from 'react';

import {
    getDownloadTarget,
    getYoutubeConfig,
    guessMode,
    hasPlaylistParam,
    hasServerLogin,
    isSupportedUrl,
    saveYoutubeConfig,
    youtubeApi,
    YoutubeMode,
} from '/@/renderer/features/youtube/api/youtube-api';
import { YoutubeJobList } from '/@/renderer/features/youtube/components/youtube-job-list';
import { youtubeJobsQueryKey } from '/@/renderer/features/youtube/hooks/use-youtube-jobs';
import { Button } from '/@/shared/components/button/button';
import { Group } from '/@/shared/components/group/group';
import { PasswordInput } from '/@/shared/components/password-input/password-input';
import { SegmentedControl } from '/@/shared/components/segmented-control/segmented-control';
import { Stack } from '/@/shared/components/stack/stack';
import { TextInput } from '/@/shared/components/text-input/text-input';
import { Text } from '/@/shared/components/text/text';
import { toast } from '/@/shared/components/toast/toast';

interface YoutubeDownloadFormProps {
    onQueued?: () => void;
}

export const YoutubeDownloadForm = ({ onQueued }: YoutubeDownloadFormProps) => {
    const queryClient = useQueryClient();
    const [url, setUrl] = useState('');
    const [mode, setMode] = useState<YoutubeMode>('single');
    const [submitting, setSubmitting] = useState(false);
    const [needsToken, setNeedsToken] = useState(
        () => !getYoutubeConfig().token && !hasServerLogin(),
    );

    const trimmed = url.trim();
    const valid = isSupportedUrl(trimmed);
    const showModeChoice =
        valid &&
        (hasPlaylistParam(trimmed) || getDownloadTarget(trimmed).hasList || mode === 'playlist');

    const handleUrlChange = (value: string) => {
        setUrl(value);
        if (isSupportedUrl(value)) setMode(guessMode(value));
    };

    const handleSubmit = async (e: FormEvent) => {
        e.preventDefault();
        if (!valid || submitting) return;
        setSubmitting(true);
        try {
            await youtubeApi.download(trimmed, mode);
            toast.info({
                message:
                    mode === 'playlist' ? 'Downloading the whole playlist' : 'Downloading song',
                title: 'Added to download queue',
            });
            setUrl('');
            setMode('single');
            queryClient.invalidateQueries({ queryKey: youtubeJobsQueryKey });
            onQueued?.();
        } catch (err) {
            const message = err instanceof Error ? err.message : String(err);
            if (message.includes('token')) setNeedsToken(true);
            toast.error({ message, title: "Couldn't start download" });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Stack gap="md">
            {needsToken && <YoutubeServiceSettings onSaved={() => setNeedsToken(false)} />}
            <form onSubmit={handleSubmit}>
                <Stack gap="sm">
                    <TextInput
                        autoFocus
                        data-autofocus
                        error={trimmed && !valid ? 'Paste a YouTube or SoundCloud link' : undefined}
                        label="YouTube link"
                        onChange={(e) => handleUrlChange(e.currentTarget.value)}
                        placeholder="https://www.youtube.com/watch?v=…"
                        value={url}
                    />
                    {showModeChoice && (
                        <Stack gap={4}>
                            <Text isMuted size="sm">
                                This link is part of a playlist. What should be downloaded?
                            </Text>
                            <SegmentedControl
                                data={[
                                    { label: 'Just this song', value: 'single' },
                                    { label: 'Whole playlist', value: 'playlist' },
                                ]}
                                onChange={(v) => setMode(v as YoutubeMode)}
                                value={mode}
                            />
                        </Stack>
                    )}
                    <Group justify="flex-end">
                        <Button
                            disabled={!valid}
                            loading={submitting}
                            type="submit"
                            variant="filled"
                        >
                            Download MP3
                        </Button>
                    </Group>
                </Stack>
            </form>
        </Stack>
    );
};

interface YoutubeServiceSettingsProps {
    onSaved?: () => void;
}

export const YoutubeServiceSettings = ({ onSaved }: YoutubeServiceSettingsProps) => {
    const queryClient = useQueryClient();
    const [config, setConfig] = useState(getYoutubeConfig);

    const handleSave = () => {
        saveYoutubeConfig(config);
        queryClient.invalidateQueries({ queryKey: youtubeJobsQueryKey });
        toast.success({ message: 'Download service settings saved' });
        onSaved?.();
    };

    return (
        <Stack gap="sm">
            <TextInput
                label="Download service address"
                onChange={(e) => setConfig({ ...config, url: e.currentTarget.value })}
                value={config.url}
            />
            <PasswordInput
                description="The YT_TOKEN value in ~/.config/yt-download.env on the server"
                label="Download token"
                onChange={(e) => setConfig({ ...config, token: e.currentTarget.value })}
                value={config.token}
            />
            <Group justify="flex-end">
                <Button disabled={!config.token.trim()} onClick={handleSave} variant="default">
                    Save
                </Button>
            </Group>
        </Stack>
    );
};

const YoutubeDownloadModalContent = () => (
    <Stack gap="lg">
        <YoutubeDownloadForm />
        <YoutubeJobList limit={3} />
    </Stack>
);

export const openYoutubeDownloadModal = () => {
    openModal({
        children: <YoutubeDownloadModalContent />,
        size: 'lg',
        title: 'Download from YouTube',
    });
};
