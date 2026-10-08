package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.utils.Prefers;

/** Seeds editable presets once; never overwrites a user's selected config. */
public final class BuiltinConfigs {
    private static final String INSTALLED = "builtin_configs_v1";

    private BuiltinConfigs() {}

    public static void install() {
        if (Prefers.getBoolean(INSTALLED)) return;
        AppDatabase.get().runInTransaction(() -> {
            boolean selectDefault = Config.vod().isEmpty();
            String[] names = App.get().getResources().getStringArray(R.array.builtin_config_names);
            String[] urls = App.get().getResources().getStringArray(R.array.builtin_config_urls);
            for (int i = 0; i < urls.length; i++) {
                Config.find(urls[i], names[i], 0).save();
            }
            if (selectDefault) Config.find(urls[0], 0).update();
        });
        // Official APKs use a different signature and package identity.
        Setting.putUpdate(false);
        Prefers.put(INSTALLED, true);
    }
}
