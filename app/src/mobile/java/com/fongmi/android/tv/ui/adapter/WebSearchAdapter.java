package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.WebResource;
import com.fongmi.android.tv.databinding.AdapterWebSearchBinding;

import java.util.ArrayList;
import java.util.List;

public class WebSearchAdapter extends RecyclerView.Adapter<WebSearchAdapter.ViewHolder> {
    private final List<WebResource> items = new ArrayList<>();
    private final OnClickListener listener;

    public WebSearchAdapter(OnClickListener listener) { this.listener = listener; }
    public void setItems(List<WebResource> values) { items.clear(); items.addAll(values); notifyDataSetChanged(); }
    public interface OnClickListener { void onItemClick(WebResource item); }

    @NonNull @Override public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new ViewHolder(AdapterWebSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }
    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WebResource item = items.get(position);
        holder.binding.title.setText(item.getTitle());
        holder.binding.url.setText(item.getCategory() + " · " + item.getDescription());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }
    @Override public int getItemCount() { return items.size(); }
    static class ViewHolder extends RecyclerView.ViewHolder {
        final AdapterWebSearchBinding binding;
        ViewHolder(AdapterWebSearchBinding binding) { super(binding.getRoot()); this.binding = binding; }
    }
}
