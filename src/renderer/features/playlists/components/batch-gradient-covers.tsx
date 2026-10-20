import { closeAllModals, openModal } from '@mantine/modals';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';

import { playlistsQueries } from '/@/renderer/features/playlists/api/playlists-api';
import {
    drawGradientCover,
    GradientSpec,
    initialsFor,
    randomGradient,
} from '/@/renderer/features/playlists/utils/gradient-cover';
import {
    CoverSource,
    isCoverJobRunning,
    startCoverJob,
} from '/@/renderer/features/playlists/utils/playlist-cover-jobs';
import { useCurrentServer } from '/@/renderer/store';
import { Button } from '/@/shared/components/button/button';
import { Checkbox } from '/@/shared/components/checkbox/checkbox';
import { Group } from '/@/shared/components/group/group';
import { SegmentedControl } from '/@/shared/components/segmented-control/segmented-control';
import { Stack } from '/@/shared/components/stack/stack';
import { Switch } from '/@/shared/components/switch/switch';
import { Text } from '/@/shared/components/text/text';
import { Playlist, PlaylistListSort, SortOrder } from '/@/shared/types/domain-types';

const PREVIEW_SIZE = 44;

const SOURCE_HELP: Record<CoverSource, string> = {
    artist: 'Each playlist gets a photo of the artist on the most of its songs (a gradient if there is no photo).',
    default:
        "Removes the custom cover so the playlist shows the server's usual grid of 4 album covers.",
    gradient: 'Each playlist gets its own random gradient with its initials.',
    song: "Each playlist gets the cover of its most-played song (a gradient if there isn't one).",
};

const CoverPreview = ({ letters, spec }: { letters: string; spec: GradientSpec }) => {
    const ref = useRef<HTMLCanvasElement>(null);
    useEffect(() => {
        if (ref.current) drawGradientCover(ref.current, spec, letters, PREVIEW_SIZE * 2);
    }, [letters, spec]);
    return (
        <canvas
            ref={ref}
            style={{ borderRadius: 6, flexShrink: 0, height: PREVIEW_SIZE, width: PREVIEW_SIZE }}
        />
    );
};

const BatchGradientCovers = () => {
    const server = useCurrentServer();

    const playlistsQuery = useQuery(
        playlistsQueries.list({
            query: { sortBy: PlaylistListSort.NAME, sortOrder: SortOrder.ASC, startIndex: 0 },
            serverId: server?.id,
        }),
    );
    const playlists: Playlist[] = useMemo(
        () => playlistsQuery.data?.items ?? [],
        [playlistsQuery.data],
    );

    const [source, setSource] = useState<CoverSource>('gradient');
    const [includeCustom, setIncludeCustom] = useState(false);
    const [specs, setSpecs] = useState<Record<string, GradientSpec>>({});
    const [selected, setSelected] = useState<Set<string>>(new Set());

    // "Default" only applies to playlists that currently have a custom cover.
    const visible = useMemo(
        () =>
            source === 'default'
                ? playlists.filter((p) => p.uploadedImage)
                : playlists.filter((p) => includeCustom || !p.uploadedImage),
        [includeCustom, playlists, source],
    );
    const lastClicked = useRef<null | number>(null);

    // Every playlist gets its own random gradient; all visible ones start selected.
    useEffect(() => {
        setSpecs((prev) => {
            const next = { ...prev };
            for (const p of playlists) next[p.id] ??= randomGradient();
            return next;
        });
    }, [playlists]);
    useEffect(() => {
        setSelected(new Set(visible.map((p) => p.id)));
    }, [visible]);

    const shuffle = () => {
        setSpecs((prev) => {
            const next = { ...prev };
            for (const id of selected) next[id] = randomGradient();
            return next;
        });
    };

    // Click toggles one; shift-click sets the whole range since the last click to match.
    const toggle = (index: number, shiftKey: boolean) => {
        const id = visible[index].id;
        setSelected((prev) => {
            const next = new Set(prev);
            const turnOn = !prev.has(id);
            const from = shiftKey && lastClicked.current !== null ? lastClicked.current : index;
            const [lo, hi] = from < index ? [from, index] : [index, from];
            for (let i = lo; i <= hi; i++) {
                if (turnOn) next.add(visible[i].id);
                else next.delete(visible[i].id);
            }
            return next;
        });
        lastClicked.current = index;
    };
    const selectAll = () => setSelected(new Set(visible.map((p) => p.id)));
    const selectNone = () => setSelected(new Set());
    const invert = () =>
        setSelected((prev) => new Set(visible.filter((p) => !prev.has(p.id)).map((p) => p.id)));

    const apply = () => {
        if (!server?.id) return;
        startCoverJob({
            playlists: visible.filter((p) => selected.has(p.id)),
            serverId: server.id,
            source,
            specs,
        });
        closeAllModals();
    };

    if (playlistsQuery.isLoading) return <Text size="sm">Loading playlists…</Text>;

    const running = isCoverJobRunning();

    return (
        <Stack gap="md">
            <SegmentedControl
                data={[
                    { label: 'Gradient', value: 'gradient' },
                    { label: 'Top artist', value: 'artist' },
                    { label: 'Top song', value: 'song' },
                    { label: 'Default (4 albums)', value: 'default' },
                ]}
                fullWidth
                onChange={(v) => setSource(v as CoverSource)}
                value={source}
            />
            <Text isMuted size="sm">
                {SOURCE_HELP[source]} It runs in the background, so you can keep using the app.
            </Text>
            {source !== 'default' && (
                <Switch
                    checked={includeCustom}
                    label="Also replace covers I already uploaded"
                    onChange={(e) => setIncludeCustom(e.currentTarget.checked)}
                />
            )}
            <Group gap="xs">
                <Text isMuted size="xs">
                    {selected.size} of {visible.length} selected · shift-click to select a range
                </Text>
                <Button onClick={selectAll} size="compact-xs" variant="subtle">
                    All
                </Button>
                <Button onClick={selectNone} size="compact-xs" variant="subtle">
                    None
                </Button>
                <Button onClick={invert} size="compact-xs" variant="subtle">
                    Invert
                </Button>
            </Group>
            <div style={{ maxHeight: 360, overflowY: 'auto' }}>
                <Stack gap={6}>
                    {visible.length === 0 && (
                        <Text isMuted size="sm">
                            {source === 'default'
                                ? 'No playlist has a custom cover.'
                                : 'Every playlist already has a custom cover.'}
                        </Text>
                    )}
                    {visible.map((p, index) => (
                        <Group
                            gap="sm"
                            key={p.id}
                            onClick={(e) => toggle(index, e.shiftKey)}
                            style={{ cursor: 'pointer', userSelect: 'none' }}
                            wrap="nowrap"
                        >
                            <Checkbox checked={selected.has(p.id)} readOnly tabIndex={-1} />
                            {source === 'gradient' && specs[p.id] && (
                                <CoverPreview letters={initialsFor(p.name)} spec={specs[p.id]} />
                            )}
                            <Text lineClamp={1} size="sm">
                                {p.name}
                            </Text>
                        </Group>
                    ))}
                </Stack>
            </div>
            <Group justify="space-between">
                {source === 'gradient' ? (
                    <Button disabled={selected.size === 0} onClick={shuffle} variant="default">
                        Shuffle colors
                    </Button>
                ) : (
                    <span />
                )}
                <Group gap="sm">
                    <Button onClick={() => closeAllModals()} variant="default">
                        Cancel
                    </Button>
                    <Button
                        disabled={selected.size === 0 || running}
                        onClick={apply}
                        variant="filled"
                    >
                        {running
                            ? 'A job is already running'
                            : `Apply to ${selected.size} ${selected.size === 1 ? 'playlist' : 'playlists'}`}
                    </Button>
                </Group>
            </Group>
        </Stack>
    );
};

export const openBatchGradientCovers = () => {
    openModal({
        children: <BatchGradientCovers />,
        size: 'lg',
        title: 'Generate playlist covers',
    });
};
