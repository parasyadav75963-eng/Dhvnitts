package com.itfb.dhwanitts;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
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

    public VoiceAdapter(List<VoiceModel> voiceList) { this.voiceList = voiceList; }

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

        holder.tvVoiceName.setText(voice.getVoiceName());
        holder.tvVoiceSize.setText(voice.getSize());

        File voicesDir = new File(context.getFilesDir(), "voices");
        File modelFile = new File(voicesDir, voice.getVoiceName() + ".onnx");
        File configFile = new File(voicesDir, voice.getVoiceName() + ".onnx.json");
        
        boolean isDownloaded = modelFile.exists() && configFile.exists();

        if (isDownloaded) {
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
        } else {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDownload.setText("Download");
            holder.btnDownload.setEnabled(true);
        }

        holder.btnPreview.setOnClickListener(v -> {
            if (isDownloaded) {
                Toast.makeText(context, "Playing preview...", Toast.LENGTH_SHORT).show();
                playRealPreview(modelFile.getAbsolutePath(), configFile.getAbsolutePath(), voice.getVoiceName());
            } else {
                Toast.makeText(context, "Please download first!", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnDownload.setOnClickListener(v -> {
            holder.btnDownload.setEnabled(false);
            holder.btnDownload.setText("Downloading...");
            downloadVoiceFile(context, voice, position);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (modelFile.exists()) modelFile.delete();
            if (configFile.exists()) configFile.delete();
            Toast.makeText(context, "Deleted", Toast.LENGTH_SHORT).show();
            notifyItemChanged(position);
        });
    }

    private void playRealPreview(String modelPath, String configPath, String voiceName) {
        executorService.execute(() -> {
            try {
                PiperEngine engine = new PiperEngine();
                if (engine.initEngine(modelPath, configPath)) {
                    String text = "यह " + voiceName + " आवाज़ का असली प्रीव्यू है।";
                    byte[] audioData = engine.synthesizeText(text, 1.0f);
                    if (audioData != null && audioData.length > 0) {
                        AudioTrack audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 16000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, audioData.length, AudioTrack.MODE_STATIC);
                        audioTrack.write(audioData, 0, audioData.length);
                        audioTrack.play();
                    }
                    engine.destroyEngine();
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void downloadVoiceFile(Context context, VoiceModel voice, int position) {
        executorService.execute(() -> {
            try {
                File voicesDir = new File(context.getFilesDir(), "voices");
                if (!voicesDir.exists()) voicesDir.mkdirs();
                File modelFile = new File(voicesDir, voice.getVoiceName() + ".onnx");
                File configFile = new File(voicesDir, voice.getVoiceName() + ".onnx.json");

                downloadSingleFile(voice.getDownloadUrl(), modelFile);
                downloadSingleFile(voice.getDownloadUrl() + ".json", configFile);

                mainHandler.post(() -> {
                    Toast.makeText(context, "Download Complete", Toast.LENGTH_SHORT).show();
                    notifyItemChanged(position);
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(context, "Download Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    notifyItemChanged(position);
                });
            }
        });
    }

    private void downloadSingleFile(String fileUrl, File outputFile) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection;
        int redirects = 0;
        
        while (true) {
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(false); // हम खुद redirects हैंडल करेंगे
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)");
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                status == HttpURLConnection.HTTP_MOVED_PERM || 
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308) {
                
                String newUrl = connection.getHeaderField("Location");
                url = new URL(url, newUrl); // Relative URL को भी फिक्स करेगा
                redirects++;
                if (redirects > 10) throw new Exception("Redirect Loop");
            } else if (status == HttpURLConnection.HTTP_OK) {
                break;
            } else {
                throw new Exception("HTTP Error: " + status);
            }
        }

        InputStream input = connection.getInputStream();
        FileOutputStream output = new FileOutputStream(outputFile);
        byte[] data = new byte[8192];
        int count;
        while ((count = input.read(data)) != -1) {
            output.write(data, 0, count);
        }
        output.flush();
        output.close();
        input.close();

        // फाइल करप्ट होने पर सुरक्षा
        if (outputFile.length() < 1024) {
            outputFile.delete();
            throw new Exception("File Corrupt (Too Small)");
        }
    }

    @Override
    public int getItemCount() { return voiceList.size(); }
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
