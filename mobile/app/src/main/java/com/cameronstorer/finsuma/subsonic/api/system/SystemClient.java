package com.cameronstorer.finsuma.subsonic.api.system;

import android.util.Log;

import com.cameronstorer.finsuma.subsonic.RetrofitClient;
import com.cameronstorer.finsuma.subsonic.Subsonic;
import com.cameronstorer.finsuma.subsonic.base.ApiResponse;
import com.cameronstorer.finsuma.util.ConnectionUtil;
import com.cameronstorer.finsuma.util.Preferences;

import java.util.concurrent.TimeUnit;

import retrofit2.Call;

public class SystemClient {
    private static final String TAG = "SystemClient";

    private final Subsonic subsonic;
    private final SystemService systemService;

    public SystemClient(Subsonic subsonic) {
        this.subsonic = subsonic;
        this.systemService = new RetrofitClient(subsonic).getRetrofit().create(SystemService.class);
    }

    public Call<ApiResponse> ping() {
        Log.d(TAG, "ping()");
        int timeoutSeconds = Preferences.getNetworkPingTimeout();
        Call<ApiResponse> pingCall = systemService.ping(subsonic.getParams());
        // Keyed to the address this client points at, since a probe runs on its own client while
        // the in use address is still the public one.
        boolean pingingLocalAddress = ConnectionUtil.isUnderAddress(
                subsonic.getUrl(), Preferences.getLocalAddress());

        if (pingingLocalAddress) {
            pingCall.timeout()
                    .timeout(timeoutSeconds, TimeUnit.SECONDS);
        } else {
            int finalTimeout = Math.min(timeoutSeconds * 2, 10);
            pingCall.timeout()
                    .timeout(finalTimeout, TimeUnit.SECONDS);
        }
        return pingCall;
    }

    public Call<ApiResponse> getLicense() {
        Log.d(TAG, "getLicense()");
        return systemService.getLicense(subsonic.getParams());
    }

    public Call<ApiResponse> getOpenSubsonicExtensions() {
        Log.d(TAG, "getOpenSubsonicExtensions()");
        return systemService.getOpenSubsonicExtensions(subsonic.getParams());
    }
}
