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
import java.io.File;
import java.util.List;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.ViewHolder> {
    
    private List<VoiceModel> voiceList;

    public VoiceAdapter(List<VoiceModel> voiceList) {
        this.voiceList = voiceList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voice, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VoiceModel voice = voiceList.get(position);
        
        holder.tvNameSize.setText(voice.language + " - " + voice.voiceName + " (" + voice.size + ")");

        // अगर पहले से डाउनलोड है तो बटन को 'Downloaded' कर दें
        if (voice.isDownloaded) {
            holder.btnDownload.setText("Downloaded");
            holder.btnDownload.setEnabled(false);
            holder.progressBar.setVisibility(View.GONE);
            holder.tvDownloadStatus.setVisibility(View.GONE);
        } else {
            holder.btnDownload.setText("Download");
            holder.btnDownload.setEnabled(true);
        }

        // Preview बटन
        holder.btnPreview.setOnClickListener(v -> {
            Toast.makeText(v.getContext(), "Preview playing: " + voice.voiceName, Toast.LENGTH_SHORT).show();
        });

        // Download बटन दबाने का लॉजिक
        holder.btnDownload.setOnClickListener(v -> {
            // UI अपडेट करें: प्रोग्रेस बार दिखाएँ, बटन डिसेबल करें
            holder.btnDownload.setEnabled(false);
            holder.progressBar.setVisibility(View.VISIBLE);
            holder.tvDownloadStatus.setVisibility(View.VISIBLE);
            holder.tvDownloadStatus.setText("Downloading: 0%");
            holder.progressBar.setProgress(0);

            // फोन के सुरक्षित फोल्डर में फाइल सेव करने का रास्ता बनाना
            File voicesDir = new File(v.getContext().getFilesDir(), "voices");
            if (!voicesDir.exists()) voicesDir.mkdirs();
            String destinationFile = new File(voicesDir, voice.voiceName + ".onnx").getAbsolutePath();

            // असली डाउनलोडर कॉल करना
            // (चूँकि अभी JSON में असली URL नहीं है, इसलिए हम एक डमी URL यूज कर रहे हैं ताकि एरर ना आए। 
            // बाद में हम JSON वाला 'voice.downloadUrl' यूज करेंगे)
            String testUrl = voice.downloadUrl;
            
            VoiceDownloader.downloadVoice(testUrl, destinationFile, new VoiceDownloader.DownloadListener() {
                @Override
                public void onProgress(int progress) {
                    holder.progressBar.setProgress(progress);
                    holder.tvDownloadStatus.setText("Downloading: " + progress + "%");
                }

                @Override
                public void onComplete(String filePath) {
                    voice.isDownloaded = true;
                    holder.progressBar.setVisibility(View.GONE);
                    holder.tvDownloadStatus.setText("Downloaded Successfully!");
                    holder.btnDownload.setText("Downloaded");
                    Toast.makeText(v.getContext(), voice.voiceName + " Downloaded!", Toast.LENGTH_SHORT).show();
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
        return voiceList.size();
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
