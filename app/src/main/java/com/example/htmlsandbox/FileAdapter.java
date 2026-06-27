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

public class FileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_GROUP = 0;
    private static final int TYPE_FILE = 1;

    private final List<GroupItem> groups;
    private final OnFileClickListener listener;

    public interface OnFileClickListener {
        void onFileClick(MainActivity.FileItem item);
    }

    public FileAdapter(List<GroupItem> groups, OnFileClickListener listener) {
        this.groups = groups;
        this.listener = listener;
    }

    @Override
    public int getItemViewType(int position) {
        return groups.get(position).isFile ? TYPE_FILE : TYPE_GROUP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_GROUP) {
            return new GroupViewHolder(inflater.inflate(R.layout.item_group, parent, false));
        } else {
            return new FileViewHolder(inflater.inflate(R.layout.item_file, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        GroupItem group = groups.get(position);
        if (holder instanceof GroupViewHolder) {
            GroupViewHolder gv = (GroupViewHolder) holder;
            gv.name.setText(group.groupName);
            gv.count.setText(group.files.size() + " 个文件");
            gv.icon.setRotation(group.expanded ? 90 : 0);
            gv.content.removeAllViews();
            if (group.expanded) {
                gv.content.setVisibility(View.VISIBLE);
                for (MainActivity.FileItem fi : group.files) {
                    View row = LayoutInflater.from(gv.content.getContext()).inflate(R.layout.item_file_row, gv.content, false);
                    ((TextView) row.findViewById(R.id.tv_file_name)).setText(fi.name);
                    row.setOnClickListener(v -> listener.onFileClick(fi));
                    gv.content.addView(row);
                }
            } else {
                gv.content.setVisibility(View.GONE);
            }
            gv.itemView.setOnClickListener(v -> {
                group.expanded = !group.expanded;
                notifyItemChanged(position);
            });
        } else if (holder instanceof FileViewHolder) {
            FileViewHolder fv = (FileViewHolder) holder;
            MainActivity.FileItem fi = group.files.get(0);
            fv.name.setText(fi.name);
            fv.path.setText("/" + fi.relativePath);
            fv.itemView.setOnClickListener(v -> listener.onFileClick(fi));
        }
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

    static class FileViewHolder extends RecyclerView.ViewHolder {
        TextView name, path;

        FileViewHolder(View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tv_file_name);
            path = itemView.findViewById(R.id.tv_file_path);
        }
    }

    public static class GroupItem {
        String groupName;
        boolean expanded;
        boolean isFile; // false = group header, true = single file (no children)
        List<MainActivity.FileItem> files;

        // For directory group
        public GroupItem(String groupName, List<MainActivity.FileItem> files) {
            this.groupName = groupName;
            this.files = files;
            this.isFile = false;
            this.expanded = false;
        }

        // For single file in root
        public GroupItem(MainActivity.FileItem singleFile) {
            this.groupName = singleFile.name;
            this.files = java.util.Collections.singletonList(singleFile);
            this.isFile = true;
            this.expanded = false;
        }
    }
}
