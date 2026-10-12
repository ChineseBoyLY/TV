package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.WebResource;
import com.fongmi.android.tv.databinding.FragmentWebSearchBinding;
import com.fongmi.android.tv.net.GuideCatalog;
import com.fongmi.android.tv.net.GuideContentSearch;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.WebSearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;

import java.util.List;

public class WebSearchFragment extends BaseFragment implements WebSearchAdapter.OnClickListener {
    private FragmentWebSearchBinding binding;
    private WebSearchAdapter adapter;
    private List<WebResource> items;
    private String category = "全部";

    public static WebSearchFragment newInstance(String keyword) {
        WebSearchFragment fragment = new WebSearchFragment();
        Bundle args = new Bundle(); args.putString("keyword", keyword); fragment.setArguments(args);
        return fragment;
    }
    private String keyword() { return getArguments().getString("keyword", ""); }

    @Override protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = FragmentWebSearchBinding.inflate(inflater, container, false);
    }
    @Override protected void initMenu() {
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(binding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);
        activity.setTitle("追剧么指南");
    }
    @Override protected void initView() {
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setAdapter(adapter = new WebSearchAdapter(this));
        items = GuideCatalog.all();
        for (String name : GuideCatalog.categories()) addCategory(name);
        binding.keyword.setText(keyword());
        binding.keyword.setSelection(binding.keyword.length());
        filter();
    }

    private void search() {
        String text = binding.keyword.getText().toString().trim();
        if (text.isEmpty()) return;
        binding.status.setText("正在搜索精选网站…");
        GuideContentSearch.search(text, new GuideContentSearch.Callback() {
            @Override public void onStatus(String status) { requireActivity().runOnUiThread(() -> binding.status.setText(status)); }
            @Override public void onResult(List<WebResource> result) { requireActivity().runOnUiThread(() -> { adapter.setItems(result); binding.status.setText(result.isEmpty() ? "精选网站中没有找到相关内容" : "找到 " + result.size() + " 条真实内容"); }); }
            @Override public void onError(String message) { requireActivity().runOnUiThread(() -> binding.status.setText(message)); }
        });
    }

    private void addCategory(String name) {
        TextView chip = new TextView(requireContext());
        chip.setText(name);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setSingleLine(true);
        chip.setMaxLines(1);
        chip.setTextColor(Color.WHITE);
        chip.setGravity(android.view.Gravity.CENTER);
        int pad = dp(14);
        chip.setPadding(pad, dp(7), pad, dp(7));
        android.view.ViewGroup.MarginLayoutParams params = new android.view.ViewGroup.MarginLayoutParams(-2, -2);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        chip.setLayoutParams(params);
        chip.setOnClickListener(v -> { category = name; refreshCategories(); filter(); });
        chip.setTag(name);
        binding.categoryContainer.addView(chip);
        refreshCategories();
    }

    private void refreshCategories() {
        for (int i = 0; i < binding.categoryContainer.getChildCount(); i++) {
            TextView chip = (TextView) binding.categoryContainer.getChildAt(i);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(18));
            background.setColor(category.equals(chip.getTag()) ? 0xFF30382A : 0x332F382E);
            chip.setBackground(background);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void filter() {
        String text = binding.keyword.getText().toString().trim().toLowerCase();
        List<WebResource> result = items.stream().filter(item -> ("全部".equals(category) || item.getCategory().equals(category)) && (text.isEmpty() || item.getTitle().toLowerCase().contains(text) || item.getDescription().toLowerCase().contains(text))).toList();
        adapter.setItems(result);
        binding.status.setText(result.size() + " 个精选资源");
    }
    @Override protected void initEvent() {
        binding.searchButton.setOnClickListener(v -> search());
        binding.keyword.setOnEditorActionListener((view, action, event) -> { if (action == EditorInfo.IME_ACTION_SEARCH) search(); return true; });
    }
    @Override public void onItemClick(WebResource item) {
        if (item.isPlayable()) VideoActivity.push(requireActivity(), item.getUrl());
        else startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(item.getUrl())));
    }
}
