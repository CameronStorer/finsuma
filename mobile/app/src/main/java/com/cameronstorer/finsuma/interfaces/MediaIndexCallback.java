package com.cameronstorer.finsuma.interfaces;

import androidx.annotation.Keep;

@Keep
public interface MediaIndexCallback {
    default void onRecovery(int index) {}
}
