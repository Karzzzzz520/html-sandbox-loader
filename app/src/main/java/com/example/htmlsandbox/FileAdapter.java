package com.example.htmlsandbox;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.GroupViewHolder> {

    private final List<GroupItem> groups;
    private final OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(MainActivity.FileItem item);
    }

    public FileAdapter(List<GroupItem> groups, OnFileClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    @NonNull
    @Override
    public GroupViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_group, parent, false);
        return new GroupViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GroupViewHolder holder, int position) {
        GroupItem group = groups.get(position);
        holder.name.setText(group.title);
        holder.count.setText(group.files.size() + " 个文件");
        holder.icon.setRotation(group.expanded ? 90 : 0);
        holder.content.removeAllViews();

        if (group.expanded) {
            holder.content.setVisibility(View.VISIBLE);
            for (MainActivity.FileItem fi : group.files) {
                View row = LayoutInflater.from(holder.content.getContext()).inflate(R.layout.item_file_row, holder.content, false);
                ((TextView) row.findViewById(R.id.tv_file_name)).setText(fi.name);
                ((TextView) row.findViewById(R.id.tv_file_path)).setText("/" + fi.relativePath);
                row.setOnClickListener(v -> listener.onFileClick(fi));
                holder.content.addView(row);
            }
        } else {
            holder.content.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            group.expanded = !group.expanded;
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return groups.size();
    }

    static class GroupViewHolder extends RecyclerView.ViewHolder {
        TextView name, count;
        ImageView icon;
        LinearLayout content;

        GroupViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_group_name);
            count = itemView.findViewById(R.id.tv_group_count);
            icon = itemView.findViewById(R.id.iv_group_arrow);
            content = itemView.findViewById(R.id.ll_group_content);
        }
    }

    public static class GroupItem {
        String title;
        boolean expanded;
        List<MainActivity.FileItem> files;

        public GroupItem(String title, List<MainActivity.FileItem> files) {
            this.title = title;
            this.files = files;
            this.expanded = false;
        }

        public GroupItem(MainActivity.FileItem singleFile) {
            this.title = singleFile.name;
            this.files = java.util.Collections.singletonList(singleFile);
            this.expanded = false;
        }
    }
}
