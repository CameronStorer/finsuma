import { useQueryClient } from '@tanstack/react-query';
import { useCallback } from 'react';

import { queryKeys } from '/@/renderer/api/query-keys';
import { setPlaylistPref } from '/@/renderer/features/playlists/utils/playlist-prefs';
import { useCurrentServerId } from '/@/renderer/store';
import { ContextMenu } from '/@/shared/components/context-menu/context-menu';
import { Playlist } from '/@/shared/types/domain-types';

// Pinned playlists stay at the top of the Playlists page, sidebar and home row.
export const PinPlaylistAction = ({ items }: { items: Playlist[] }) => {
    const queryClient = useQueryClient();
    const serverId = useCurrentServerId();
    const allPinned = items.length > 0 && items.every((p) => p.pinned);

    const onSelect = useCallback(async () => {
        await Promise.all(items.map((p) => setPlaylistPref(p.id, { pinned: !allPinned })));
        if (serverId) {
            queryClient.invalidateQueries({ queryKey: queryKeys.playlists.root(serverId) });
        }
    }, [allPinned, items, queryClient, serverId]);

    if (items.length === 0) return null;

    return (
        <ContextMenu.Item leftIcon={allPinned ? 'unpin' : 'pin'} onSelect={onSelect}>
            {allPinned ? 'Unpin' : 'Pin to top'}
        </ContextMenu.Item>
    );
};
