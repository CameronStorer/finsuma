import isElectron from 'is-electron';
import { CSSProperties, useCallback, useRef, useState } from 'react';
import { RiArrowLeftLine, RiDownload2Fill } from 'react-icons/ri';

import { PageHeader } from '/@/renderer/components/page-header/page-header';
import { AnimatedPage } from '/@/renderer/features/shared/components/animated-page';
import { LibraryHeaderBar } from '/@/renderer/features/shared/components/library-header-bar';
import { PageErrorBoundary } from '/@/renderer/features/shared/components/page-error-boundary';
import { getDownloadTarget } from '/@/renderer/features/youtube/api/youtube-api';
import { YoutubeDownloadForm } from '/@/renderer/features/youtube/components/youtube-download-form';
import { openYoutubeDownloadPopup } from '/@/renderer/features/youtube/components/youtube-download-popup';
import { YoutubeJobList } from '/@/renderer/features/youtube/components/youtube-job-list';
import { useYoutubeJobs } from '/@/renderer/features/youtube/hooks/use-youtube-jobs';
import { Box } from '/@/shared/components/box/box';
import { Paper } from '/@/shared/components/paper/paper';
import { ScrollArea } from '/@/shared/components/scroll-area/scroll-area';
import { Stack } from '/@/shared/components/stack/stack';

// The parts of Electron's <webview> element we use.
type WebviewElement = HTMLElement & {
    canGoBack: () => boolean;
    getURL: () => string;
    goBack: () => void;
};

const YOUTUBE_HOME = 'https://www.youtube.com/';

interface SiteBrowserProps {
    home: string;
    partition: string;
}

const floatingButton = (size: number, background: string): CSSProperties => ({
    alignItems: 'center',
    background,
    border: 'none',
    borderRadius: '50%',
    boxShadow: '0 4px 14px rgba(0, 0, 0, 0.45)',
    color: '#fff',
    cursor: 'pointer',
    display: 'flex',
    height: size,
    justifyContent: 'center',
    width: size,
});

// A site (YouTube, SoundCloud) shown in a <webview>, with a floating download button.
export const SiteBrowser = ({ home, partition }: SiteBrowserProps) => {
    const webviewRef = useRef<WebviewElement>(null);
    const [currentUrl, setCurrentUrl] = useState(home);
    useYoutubeJobs(); // finish toasts + library rescan while this tab is open

    const attachWebview = useCallback((node: null | WebviewElement) => {
        webviewRef.current = node;
        if (!node) return;
        const sync = () => {
            try {
                setCurrentUrl(node.getURL());
            } catch {
                // not ready yet
            }
        };
        node.addEventListener('did-navigate', sync);
        node.addEventListener('did-navigate-in-page', sync);
    }, []);

    const canDownload = getDownloadTarget(currentUrl).ok;

    const handleDownload = () => {
        const url = webviewRef.current?.getURL() || currentUrl;
        openYoutubeDownloadPopup(url);
    };

    const handleBack = () => {
        if (webviewRef.current?.canGoBack()) webviewRef.current.goBack();
    };

    return (
        <Box style={{ height: '100%', position: 'relative', width: '100%' }}>
            <webview
                // Lets sign-in popups open. React only renders it as a string; typings say boolean.
                // eslint-disable-next-line react/no-unknown-property -- Electron webview attribute
                allowpopups={'true' as unknown as boolean}
                // eslint-disable-next-line react/no-unknown-property -- Electron webview attribute
                partition={partition}
                ref={attachWebview as never}
                src={home}
                style={{ height: '100%', width: '100%' }}
            />
            <Box
                style={{
                    alignItems: 'center',
                    bottom: 24,
                    display: 'flex',
                    gap: 12,
                    position: 'absolute',
                    right: 24,
                    zIndex: 10,
                }}
            >
                <button
                    aria-label="Back"
                    onClick={handleBack}
                    style={floatingButton(40, 'rgba(40, 40, 40, 0.9)')}
                    title="Back"
                    type="button"
                >
                    <RiArrowLeftLine size={20} />
                </button>
                <button
                    aria-label="Download to music server"
                    onClick={handleDownload}
                    style={{
                        ...floatingButton(56, '#f28c28'),
                        opacity: canDownload ? 1 : 0.55,
                        transition: 'opacity 150ms ease',
                    }}
                    title={
                        canDownload ? 'Download to music server' : 'Open a song or playlist first'
                    }
                    type="button"
                >
                    <RiDownload2Fill size={26} />
                </button>
            </Box>
        </Box>
    );
};

// Browsers block YouTube in frames, so the web build gets the paste-a-link form instead.
export const YoutubeLinkForm = ({ title = 'YouTube' }: { title?: string }) => (
    <>
        <PageHeader>
            <LibraryHeaderBar ignoreMaxWidth>
                <LibraryHeaderBar.Title>{title}</LibraryHeaderBar.Title>
            </LibraryHeaderBar>
        </PageHeader>
        <ScrollArea style={{ flex: 1 }}>
            <Box maw={720} p="lg">
                <Stack gap="lg">
                    <Paper p="md">
                        <YoutubeDownloadForm />
                    </Paper>
                    <YoutubeJobList />
                </Stack>
            </Box>
        </ScrollArea>
    </>
);

const YoutubeRoute = () => (
    <AnimatedPage>
        {isElectron() ? (
            <SiteBrowser home={YOUTUBE_HOME} partition="persist:youtube" />
        ) : (
            <YoutubeLinkForm />
        )}
    </AnimatedPage>
);

const YoutubeRouteWithBoundary = () => (
    <PageErrorBoundary>
        <YoutubeRoute />
    </PageErrorBoundary>
);

export default YoutubeRouteWithBoundary;
