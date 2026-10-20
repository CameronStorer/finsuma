import { YoutubeJob } from '/@/renderer/features/youtube/api/youtube-api';
import { useYoutubeJobs } from '/@/renderer/features/youtube/hooks/use-youtube-jobs';
import { Badge } from '/@/shared/components/badge/badge';
import { Group } from '/@/shared/components/group/group';
import { Paper } from '/@/shared/components/paper/paper';
import { Progress } from '/@/shared/components/progress/progress';
import { Stack } from '/@/shared/components/stack/stack';
import { Text } from '/@/shared/components/text/text';

const STATUS_LABEL: Record<YoutubeJob['status'], string> = {
    done: 'Done',
    failed: 'Failed',
    queued: 'Waiting',
    running: 'Downloading',
};

const STATUS_COLOR: Record<YoutubeJob['status'], string> = {
    done: 'green',
    failed: 'red',
    queued: 'gray',
    running: 'orange',
};

interface YoutubeJobListProps {
    limit?: number;
}

export const YoutubeJobList = ({ limit }: YoutubeJobListProps) => {
    const { data: jobs, error, isLoading } = useYoutubeJobs();

    if (error) {
        return (
            <Text isMuted size="sm">
                {error instanceof Error ? error.message : String(error)}
            </Text>
        );
    }

    if (isLoading) return null;

    if (!jobs || jobs.length === 0) {
        return (
            <Text isMuted size="sm">
                No downloads yet. Songs land in Music/YouTube and show up in your library after the
                rescan.
            </Text>
        );
    }

    return (
        <Stack gap="xs">
            <Text fw={600} size="sm">
                Recent downloads
            </Text>
            {(limit ? jobs.slice(0, limit) : jobs).map((job) => (
                <YoutubeJobRow job={job} key={job.id} />
            ))}
        </Stack>
    );
};

const YoutubeJobRow = ({ job }: { job: YoutubeJob }) => {
    const songs = job.files.length;
    const detail =
        job.status === 'done'
            ? `${songs} ${songs === 1 ? 'song' : 'songs'} saved`
            : job.status === 'failed'
              ? job.error?.split('\n').pop() || 'Something went wrong'
              : job.mode === 'playlist'
                ? `Playlist · ${songs} done so far`
                : 'Single song';

    return (
        <Paper p="sm">
            <Stack gap={6}>
                <Group justify="space-between" wrap="nowrap">
                    <Text fw={500} lineClamp={1} size="sm">
                        {job.title || job.url}
                    </Text>
                    <Badge color={STATUS_COLOR[job.status]} variant="light">
                        {STATUS_LABEL[job.status]}
                    </Badge>
                </Group>
                {job.status === 'running' && (
                    <Progress animated color="orange" size="sm" value={job.progress} />
                )}
                <Text isMuted lineClamp={2} size="xs">
                    {detail}
                </Text>
            </Stack>
        </Paper>
    );
};
