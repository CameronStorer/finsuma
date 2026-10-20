import { useQuery } from '@tanstack/react-query';
import { useEffect } from 'react';

import { controller } from '/@/renderer/api/controller';
import { startScanWatch } from '/@/renderer/features/shared/hooks/use-scan-status';
import { youtubeApi, YoutubeJob } from '/@/renderer/features/youtube/api/youtube-api';
import { useCurrentServerId } from '/@/renderer/store';
import { toast } from '/@/shared/components/toast/toast';

export const youtubeJobsQueryKey = ['youtube', 'jobs'];

// Jobs already finished when we first saw them; we only announce new finishes.
let knownFinished: null | Set<string> = null;

const isFinished = (job: YoutubeJob) => job.status === 'done' || job.status === 'failed';

export const useYoutubeJobs = () => {
    const serverId = useCurrentServerId();

    const query = useQuery({
        queryFn: youtubeApi.jobs,
        queryKey: youtubeJobsQueryKey,
        refetchInterval: (q) => {
            const jobs = q.state.data;
            if (q.state.error) return 15000;
            return jobs?.some((j) => !isFinished(j)) ? 1500 : 8000;
        },
        retry: false,
        throwOnError: false,
    });

    useEffect(() => {
        const jobs = query.data;
        if (!jobs) return;

        if (knownFinished === null) {
            knownFinished = new Set(jobs.filter(isFinished).map((j) => j.id));
            return;
        }

        const newlyDone = jobs.filter((j) => isFinished(j) && !knownFinished!.has(j.id));
        if (newlyDone.length === 0) return;
        newlyDone.forEach((j) => knownFinished!.add(j.id));

        newlyDone.forEach((job) => {
            const name = job.title || job.url;
            if (job.status === 'done') {
                const count = job.files.length;
                toast.success({
                    message: `${name} — ${count} ${count === 1 ? 'song' : 'songs'} added`,
                    title: 'YouTube download finished',
                });
            } else {
                toast.error({ message: name, title: 'YouTube download failed' });
            }
        });

        // New files on disk: ask Navidrome to rescan so they show up right away.
        if (serverId && newlyDone.some((j) => j.status === 'done' && j.files.length > 0)) {
            controller
                .startLibraryScan({ apiClientProps: { serverId } })
                .then(() => startScanWatch())
                .catch(() => undefined);
        }
    }, [query.data, serverId]);

    return query;
};
