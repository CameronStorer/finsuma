package com.cameronstorer.finsuma.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.cameronstorer.finsuma.R;
import com.cameronstorer.finsuma.model.HomeSector;
import com.cameronstorer.finsuma.util.Constants;
import com.cameronstorer.finsuma.util.HomeSectorDefaults;
import com.cameronstorer.finsuma.util.Preferences;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.List;

public class HomeRearrangementViewModel extends AndroidViewModel {
    private List<HomeSector> sectors = new ArrayList<>();

    public HomeRearrangementViewModel(@NonNull Application application) {
        super(application);
    }

    public List<HomeSector> getHomeSectorList() {
        if (sectors != null && !sectors.isEmpty()) return sectors;

        if (Preferences.getHomeSectorList() != null && !Preferences.getHomeSectorList().equals("null")) {
            sectors = new Gson().fromJson(
                    Preferences.getHomeSectorList(),
                    new TypeToken<List<HomeSector>>() {
                    }.getType()
            );
        } else {
            sectors = fillStandardHomeSectorList();
        }

        return sectors;
    }

    public void orderSectorLiveListAfterSwap(List<HomeSector> sectors) {
        this.sectors = sectors;
    }

    public void saveHomeSectorList(List<HomeSector> sectors) {
        Preferences.setHomeSectorList(sectors);
    }

    public void resetHomeSectorList() {
        Preferences.setHomeSectorList(null);
    }

    public void closeDialog() {
        sectors = null;
    }

    private List<HomeSector> fillStandardHomeSectorList() {
        return HomeSectorDefaults.build(getApplication());
    }
}
