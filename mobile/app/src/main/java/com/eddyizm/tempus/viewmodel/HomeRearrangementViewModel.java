package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.model.HomeSector;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.HomeSectorDefaults;
import com.eddyizm.tempus.util.Preferences;
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
