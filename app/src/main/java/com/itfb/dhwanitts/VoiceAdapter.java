package com.itfb.dhwanitts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder> {
    private List<VoiceModel> voiceList;
    private ExecutorService executorService = Executors.newFixedThreadPool(3);
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    public VoiceAdapter(List<VoiceModel> voiceList) {
        this.voiceList = voiceList;
    }

    @NonNull
    @Override
    public VoiceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_voice, parent, false);
        return new VoiceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VoiceViewHolder holder, int position) {
        VoiceModel voice = voiceList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvVoiceName.setText(voice.getLanguage() + " - " + voice.getVoiceName());
        holder.tvVoiceSize.setText(voice.getSize() + " MB");

        File voiceDir = new File(context.getExternalFilesDir(null), "piper_voices/" + voice.getVoiceName());
        boolean isDownloaded = voiceDir.exists() && voiceDir.list() != null && voiceDir.list().length > 0;

        if (isDownloaded) {
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
        } else {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDownload.setText("Download");
            holder.btnDownload.setEnabled(true);
        }

        // Preview Logic
        holder.btnPreview.setOnClickListener(v -> {
            if (isDownloaded) {
                Toast.makeText(context, "Testing " + voice.getVoiceName() + " (Select in TTS Settings to hear)", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(context, "Please download the voice first to preview!", Toast.LENGTH_SHORT).show();
            }
        });

        // Download Logic (Now with Redirect Handling)
        holder.btnDownload.setOnClickListener(v -> {
            holder.btnDownload.setEnabled(false);
            holder.btnDownload.setText("Downloading...");
            downloadVoiceFile(context, voice, position);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (voiceDir.exists()) {
                for (File child : voiceDir.listFiles()) {
                    child.delete();
                }
                voiceDir.delete();
            }
            Toast.makeText(context, voice.getVoiceName() + " Deleted!", Toast.LENGTH_SHORT).show();
            notifyItemChanged(position);
        });
    }

    private void downloadVoiceFile(Context context, VoiceModel voice, int position) {
        executorService.execute(() -> {
            try {
                URL url = new URL(voice.getDownloadUrl());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(true);
                
                // Redirect handling (For GitHub / Hindi voice links)
                int status = connection.getResponseCode();
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                    status == HttpURLConnection.HTTP_MOVED_PERM || 
                    status == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = connection.getHeaderField("Location");
                    connection = (HttpURLConnection) new URL(newUrl).openConnection();
                }

                File dir = new File(context.getExternalFilesDir(null), "piper_voices/" + voice.getVoiceName());
                if (!dir.exists()) dir.mkdirs();
                
                String fileName = voice.getVoiceName() + "_data.tar.gz"; 
                File outputFile = new File(dir, fileName);

                InputStream input = connection.getInputStream();
                FileOutputStream output = new FileOutputStream(outputFile);

                byte[] data = new byte[4096];
                int count;
                while ((count = input.read(data)) != -1) {
                    output.write(data, 0, count);
                }
                output.flush();
                output.close();
                input.close();

                mainHandler.post(() -> {
                    Toast.makeText(context, "Download Complete: " + voice.getVoiceName(), Toast.LENGTH_SHORT).show();
                    notifyItemChanged(position);
                });

            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    Toast.makeText(context, "Download Failed. Check Internet/URL.", Toast.LENGTH_SHORT).show();
                    notifyItemChanged(position);
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return voiceList.size();
    }

    public static class VoiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvVoiceName, tvVoiceSize;
        Button btnDownload, btnDelete, btnPreview;

        public VoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVoiceName = itemView.findViewById(R.id.tv_voice_name);
            tvVoiceSize = itemView.findViewById(R.id.tv_voice_size);
            btnDownload = itemView.findViewById(R.id.btn_download);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnPreview = itemView.findViewById(R.id.btn_preview);
        }
    }
}
