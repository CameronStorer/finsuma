package com.cameronstorer.finsuma.interfaces;

import androidx.annotation.Keep;

@Keep
public interface PlaylistCallback {
    default void onRenamed(String name) {}
}
