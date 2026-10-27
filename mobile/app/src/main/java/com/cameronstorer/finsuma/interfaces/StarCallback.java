package com.cameronstorer.finsuma.interfaces;

import androidx.annotation.Keep;

@Keep
public interface StarCallback {
    default void onError() {}
    default void onSuccess() {}

    default void onRefused() {}
}
