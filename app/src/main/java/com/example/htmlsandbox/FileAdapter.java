package com.example.htmlsandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.ViewHolder> {

    private final List<MainActivity.FileItem> items;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MainActivity.FileItem item);
    }

    public FileAdapter(List<MainActivity.FileItem> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MainActivity.FileItem item = items.get(position);
        holder.name.setText(item.name);
        holder.path.setText(item.relativePath);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, path;

        ViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_file_name);
            path = itemView.findViewById(R.id.tv_file_path);
        }
    }
}
