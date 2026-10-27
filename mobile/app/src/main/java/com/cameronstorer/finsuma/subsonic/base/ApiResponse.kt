package com.cameronstorer.finsuma.subsonic.base

import androidx.annotation.Keep
import com.cameronstorer.finsuma.subsonic.models.ResponseStatus
import com.cameronstorer.finsuma.subsonic.models.SubsonicResponse
import com.google.gson.annotations.SerializedName

@Keep
class ApiResponse {
    @SerializedName("subsonic-response")
    var subsonicResponse: SubsonicResponse = SubsonicResponse().apply {
        status = ResponseStatus.FAILED
    }
}