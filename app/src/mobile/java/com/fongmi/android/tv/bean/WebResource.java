package com.fongmi.android.tv.bean;

import androidx.annotation.NonNull;

public class WebResource {
    private final String title;
    private final String url;
    private final String page;
    private final boolean playable;
    private final String category;
    private final String description;

    public WebResource(@NonNull String title, @NonNull String url, @NonNull String page, boolean playable) {
        this.title = title;
        this.url = url;
        this.page = page;
        this.playable = playable;
        this.category = "全部";
        this.description = "";
    }

    public WebResource(@NonNull String title, @NonNull String description, @NonNull String category, @NonNull String url) {
        this.title = title;
        this.url = url;
        this.page = url;
        this.playable = false;
        this.category = category;
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public String getPage() {
        return page;
    }

    public boolean isPlayable() {
        return playable;
    }

    public String getCategory() { return category; }

    public String getDescription() { return description; }
}
