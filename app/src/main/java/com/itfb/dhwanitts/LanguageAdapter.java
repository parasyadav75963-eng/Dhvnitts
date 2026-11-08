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
        void onLanguageClick(String language);
    }

    public LanguageAdapter(List<String> languageList, OnLanguageClickListener listener) {
        this.languageList = languageList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_language, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String lang = languageList.get(position);
        // "Hindi (5 Voices)" जैसा दिखाने के लिए हम आगे इसे और एडवांस कर सकते हैं
        holder.tvLangName.setText(lang);
        holder.itemView.setOnClickListener(v -> listener.onLanguageClick(lang));
    }

    @Override
    public int getItemCount() {
        return languageList.size();
    }

    public static class ViewHolder extends RecyclerView.Adapter.ViewHolder {
        TextView tvLangName;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvLangName = itemView.findViewById(R.id.tv_lang_name);
        }
    }
}
