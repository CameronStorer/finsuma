package com.cameronstorer.finsuma.interfaces;

import androidx.annotation.Keep;

@Keep

public interface RadioCallback {
    default void onDismiss() {}
}
