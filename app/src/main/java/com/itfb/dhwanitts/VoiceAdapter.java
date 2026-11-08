package com.itfb.dhwanitts;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.ViewHolder> {
    private List<VoiceModel> voiceList;

    public VoiceAdapter(List<VoiceModel> voiceList) {
        this.voiceList = voiceList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // यहाँ सुनिश्चित करें कि लेआउट का नाम item_voice.xml ही है
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voice, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoiceModel voice = voiceList.get(position);
        holder.tvNameSize.setText(voice.getVoiceName());
        
        holder.btnDownload.setOnClickListener(v -> {
            holder.btnDownload.setEnabled(false);
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.tvDownloadStatus.setText("Downloading...");

            String destPath = v.getContext().getFilesDir() + "/" + voice.getVoiceName() + ".onnx";

            // यहाँ नया DownloadListener बिल्कुल सही तरीकों (Override) के साथ जोड़ा गया है
            VoiceDownloader.downloadVoice(voice.getDownloadUrl(), destPath, new VoiceDownloader.DownloadListener() {
                @Override
                public void onProgress(int progress) {
                    holder.progressBar.setProgress(progress);
                    holder.tvDownloadStatus.setText("Downloading: " + progress + "%");
                }

                @Override
                public void onSuccess() {
                    holder.btnDownload.setEnabled(true);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.tvDownloadStatus.setText("Downloaded");
                    Toast.makeText(v.getContext(), voice.getVoiceName() + " Downloaded!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(String error) {
                    holder.btnDownload.setEnabled(true);
                    holder.progressBar.setVisibility(View.GONE);
                    holder.tvDownloadStatus.setText("Error: Download failed");
                    Toast.makeText(v.getContext(), "Download Failed!", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return voiceList != null ? voiceList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNameSize, tvDownloadStatus;
        Button btnPreview, btnDownload;
        ProgressBar progressBar;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNameSize = itemView.findViewById(R.id.tv_voice_name_size);
            tvDownloadStatus = itemView.findViewById(R.id.tv_download_status);
            btnPreview = itemView.findViewById(R.id.btn_preview);
            btnDownload = itemView.findViewById(R.id.btn_download);
            progressBar = itemView.findViewById(R.id.progress_download);
        }
    }
}
