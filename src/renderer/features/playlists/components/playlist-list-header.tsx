import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useIsFetchingItemListCount } from '/@/renderer/components/item-list/helpers/use-is-fetching-item-list';
import { PageHeader } from '/@/renderer/components/page-header/page-header';
import { useListContext } from '/@/renderer/context/list-context';
import { openBatchGradientCovers } from '/@/renderer/features/playlists/components/batch-gradient-covers';
import { PlaylistListHeaderFilters } from '/@/renderer/features/playlists/components/playlist-list-header-filters';
import { usePlaylistListFilters } from '/@/renderer/features/playlists/hooks/use-playlist-list-filters';
import { FilterBar } from '/@/renderer/features/shared/components/filter-bar';
import { LibraryHeaderBar } from '/@/renderer/features/shared/components/library-header-bar';
import { ListSearchInput } from '/@/renderer/features/shared/components/list-search-input';
import { AppRoute } from '/@/renderer/router/routes';
import { useCurrentServer } from '/@/renderer/store';
import { hasFeature } from '/@/shared/api/utils';
import { Button } from '/@/shared/components/button/button';
import { Group } from '/@/shared/components/group/group';
import { Stack } from '/@/shared/components/stack/stack';
import { LibraryItem } from '/@/shared/types/domain-types';
import { ServerFeature } from '/@/shared/types/features-types';

interface PlaylistListHeaderProps {
    title?: string;
}

export const PlaylistListHeader = ({ title }: PlaylistListHeaderProps) => {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const server = useCurrentServer();
    const canUploadCovers = hasFeature(server, ServerFeature.PLAYLIST_IMAGE_UPLOAD);

    const pageTitle = title || t('page.playlistList.title');

    return (
        <Stack gap={0}>
            <PageHeader>
                <LibraryHeaderBar ignoreMaxWidth>
                    <PlayButton />
                    <LibraryHeaderBar.Title>{pageTitle}</LibraryHeaderBar.Title>
                    <PlaylistListHeaderBadge />
                </LibraryHeaderBar>
                <Group>
                    {canUploadCovers && (
                        <Button
                            onClick={openBatchGradientCovers}
                            size="compact-md"
                            variant="default"
                        >
                            Generate covers
                        </Button>
                    )}
                    <Button
                        onClick={() => navigate(AppRoute.YOUTUBE)}
                        size="compact-md"
                        variant="default"
                    >
                        + From YouTube
                    </Button>
                    <ListSearchInput />
                </Group>
            </PageHeader>
            <FilterBar>
                <PlaylistListHeaderFilters />
            </FilterBar>
        </Stack>
    );
};

const PlaylistListHeaderBadge = () => {
    const { itemCount } = useListContext();

    const isFetching = useIsFetchingItemListCount({
        itemType: LibraryItem.PLAYLIST,
    });

    return <LibraryHeaderBar.Badge isLoading={isFetching}>{itemCount}</LibraryHeaderBar.Badge>;
};

const PlayButton = () => {
    const { query } = usePlaylistListFilters();

    return <LibraryHeaderBar.PlayButton itemType={LibraryItem.PLAYLIST} listQuery={query} />;
};
