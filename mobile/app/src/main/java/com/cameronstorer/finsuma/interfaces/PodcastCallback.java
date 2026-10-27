package com.cameronstorer.finsuma.interfaces;

import androidx.annotation.Keep;

@Keep

public interface PodcastCallback {
    default void onDismiss() {}
}
