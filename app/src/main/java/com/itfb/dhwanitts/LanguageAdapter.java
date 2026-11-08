package com.itfb.dhwanitts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.ViewHolder> {

    private List<String> languageList;
    private OnLanguageClickListener listener;

    public interface OnLanguageClickListener {
        void onLanguageClick(String lang);
    }

    public LanguageAdapter(List<String> languageList, OnLanguageClickListener listener) {
        this.languageList = languageList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String lang = languageList.get(position);
        holder.textView.setText(lang);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onLanguageClick(lang);
            }
        });
    }

    @Override
    public int getItemCount() {
        return languageList != null ? languageList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textView;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(android.R.id.text1);
        }
    }
}
