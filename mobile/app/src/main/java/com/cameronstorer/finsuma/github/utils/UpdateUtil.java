package com.cameronstorer.finsuma.github.utils;

import com.cameronstorer.finsuma.BuildConfig;
import com.cameronstorer.finsuma.github.models.LatestRelease;

public class UpdateUtil {

    public static boolean showUpdateDialog(LatestRelease release) {
        if (release.getTagName() == null) return false;
        String remoteTag = release.getTagName().replaceAll("^\\D+", "");

        try {
            String[] local = BuildConfig.VERSION_NAME.split("\\.");
            String[] remote = remoteTag.split("\\.");

            for (int i = 0; i < local.length; i++) {
                int localPart = Integer.parseInt(local[i]);
                int remotePart = Integer.parseInt(remote[i]);

                if (localPart > remotePart) {
                    return false;
                } else if (localPart < remotePart) {
                    return true;
                }
            }
        } catch (Exception exception) {
            return false;
        }

        return false;
    }
}
