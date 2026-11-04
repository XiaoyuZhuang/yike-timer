package com.yike.timer;

import android.content.Context;
import android.content.SharedPreferences;

final class Store {
    private final SharedPreferences sp;

    Store(Context context) {
        sp = context.getSharedPreferences("yike", Context.MODE_PRIVATE);
    }

    String read(String key, String fallback) {
        return sp.getString(key, fallback);
    }

    void write(String key, String value) {
        sp.edit().putString(key, value).apply();
    }

    void remove(String key) {
        sp.edit().remove(key).apply();
    }
}
