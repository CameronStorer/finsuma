import isElectron from 'is-electron';

import { AnimatedPage } from '/@/renderer/features/shared/components/animated-page';
import { PageErrorBoundary } from '/@/renderer/features/shared/components/page-error-boundary';
import { SiteBrowser, YoutubeLinkForm } from '/@/renderer/features/youtube/routes/youtube-route';

const SOUNDCLOUD_HOME = 'https://soundcloud.com/discover';

const SoundcloudRoute = () => (
    <AnimatedPage>
        {isElectron() ? (
            <SiteBrowser home={SOUNDCLOUD_HOME} partition="persist:soundcloud-v2" />
        ) : (
            <YoutubeLinkForm title="SoundCloud" />
        )}
    </AnimatedPage>
);

const SoundcloudRouteWithBoundary = () => (
    <PageErrorBoundary>
        <SoundcloudRoute />
    </PageErrorBoundary>
);

export default SoundcloudRouteWithBoundary;
