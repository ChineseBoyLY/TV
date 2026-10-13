package com.fongmi.android.tv.ui.fragment;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.viewbinding.ViewBinding;
import androidx.media3.common.MediaItem;

import com.fongmi.android.tv.download.VideoDownloads;
import com.fongmi.android.tv.ui.activity.DownloadsActivity;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WebBrowserFragment extends BaseFragment {
    private final Map<String, Map<String, String>> videos = Collections.synchronizedMap(new LinkedHashMap<>());
    private LinearLayout root;
    private EditText address;
    private WebView webView;
    private MaterialButton download;
    private String pageTitle;

    public static WebBrowserFragment newInstance() {
        return new WebBrowserFragment();
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected ViewBinding getBinding(@NonNull android.view.LayoutInflater inflater, @Nullable android.view.ViewGroup container) {
        root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout bar = new LinearLayout(requireContext());
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(12), dp(8), dp(12), dp(8));
        address = new EditText(requireContext());
        address.setSingleLine(true);
        address.setHint("输入网址");
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        bar.addView(address, new LinearLayout.LayoutParams(0, dp(48), 1));
        MaterialButton go = new MaterialButton(requireContext());
        go.setText("前往");
        bar.addView(go, new LinearLayout.LayoutParams(-2, dp(48)));
        MaterialButton downloads = new MaterialButton(requireContext());
        downloads.setText("下载列表");
        LinearLayout.LayoutParams downloadsParams = new LinearLayout.LayoutParams(-2, dp(48));
        downloadsParams.setMargins(dp(4), 0, 0, 0);
        bar.addView(downloads, downloadsParams);
        root.addView(bar, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout content = new FrameLayout(requireContext());
        webView = new WebView(requireContext());
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setMediaPlaybackRequiresUserGesture(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) { pageTitle = title; }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return false; }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                synchronized (videos) { videos.clear(); }
                pageTitle = null;
                if (download != null) {
                    download.setVisibility(View.VISIBLE);
                    download.setText("嗅探视频");
                }
            }
            @Override public void onPageFinished(WebView view, String url) { address.setText(url); }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String scheme = request.getUrl().getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    boolean video;
                    try { video = Sniffer.isVideoFormat(url); }
                    catch (Exception ignored) { video = false; }
                    if (video) {
                        synchronized (videos) {
                            videos.put(url, new LinkedHashMap<>(request.getRequestHeaders()));
                            while (videos.size() > 30) videos.remove(videos.keySet().iterator().next());
                        }
                        MaterialButton button = download;
                        if (button != null) button.post(() -> {
                            if (download == button) {
                                download.setVisibility(View.VISIBLE);
                                download.setText("找到视频（" + videos.size() + "）");
                            }
                        });
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
        content.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        download = new MaterialButton(requireContext());
        download.setText("嗅探视频");
        download.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        floating.setMargins(dp(16), dp(16), dp(24), dp(24));
        content.addView(download, floating);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        go.setOnClickListener(v -> loadAddress());
        downloads.setOnClickListener(v -> DownloadsActivity.start(requireActivity()));
        address.setOnEditorActionListener((view, action, event) -> {
            if (action == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                loadAddress();
                return true;
            }
            return false;
        });
        download.setOnClickListener(v -> chooseVideo());
        return () -> root;
    }

    private void loadAddress() {
        String url = address.getText().toString().trim();
        if (url.isEmpty()) return;
        if (!url.matches("(?i)^https?://.*")) url = "https://" + url;
        address.setText(url);
        synchronized (videos) { videos.clear(); }
        download.setVisibility(View.VISIBLE);
        download.setText("嗅探视频");
        webView.loadUrl(url);
    }

    private void chooseVideo() {
        List<String> urls;
        synchronized (videos) { urls = new ArrayList<>(videos.keySet()); }
        if (urls.isEmpty()) {
            Notify.show("暂未识别到视频流，请先播放视频再试");
            return;
        }
        String[] labels = urls.stream().map(this::label).toArray(String[]::new);
        new AlertDialog.Builder(requireContext()).setTitle("选择要下载的视频")
                .setItems(labels, (dialog, which) -> addDownload(urls.get(which))).show();
    }

    private String label(String url) {
        Uri uri = Uri.parse(url);
        String path = uri.getLastPathSegment();
        return path == null || path.isBlank() ? uri.getHost() : path;
    }

    private void addDownload(String url) {
        Map<String, String> headers;
        synchronized (videos) { headers = new LinkedHashMap<>(videos.getOrDefault(url, Collections.emptyMap())); }
        String title = pageTitle;
        if (title == null || title.isBlank()) title = label(url);
        String mime = url.toLowerCase().contains(".m3u8") ? "application/x-mpegURL" : null;
        VideoDownloads.prepare(new MediaItem.Builder().setUri(url).setMimeType(mime).build(), title, headers, Notify::show);
    }

    public boolean goBack() {
        if (webView == null || !webView.canGoBack()) return false;
        webView.goBack();
        return true;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
            webView = null;
        }
        root = null;
        address = null;
        download = null;
        super.onDestroyView();
    }
}
