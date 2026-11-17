package com.itfb.dhwanitts;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceAdapter extends RecyclerView.Adapter<VoiceAdapter.VoiceViewHolder> {
    private List<VoiceModel> voiceList;
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private Map<String, Integer> activeProgress = new HashMap<>();

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

        holder.tvVoiceName.setText(voice.getName() + " (" + voice.getQuality() + ")");
        holder.tvVoiceSize.setText(voice.getSizeInfo());

        File voicesDir = new File(context.getFilesDir(), "voices");
        File modelFile = new File(voicesDir, voice.getKey() + ".onnx");
        File configFile = new File(voicesDir, voice.getKey() + ".onnx.json");

        boolean isDownloaded = modelFile.exists() && modelFile.length() > 1024 && configFile.exists();
        Integer prog = activeProgress.get(voice.getKey());

        if (prog != null) {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDownload.setEnabled(false);
            holder.btnDownload.setText("Downloading " + prog + "%");
            if (holder.progressBar != null) {
                holder.progressBar.setVisibility(View.VISIBLE);
                holder.progressBar.setProgress(prog);
            }
        } else if (isDownloaded) {
            holder.btnDownload.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            if (holder.progressBar != null) holder.progressBar.setVisibility(View.GONE);
        } else {
            holder.btnDownload.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);
            holder.btnDownload.setText("Download");
            holder.btnDownload.setEnabled(true);
            if (holder.progressBar != null) holder.progressBar.setVisibility(View.GONE);
        }

        holder.btnPreview.setOnClickListener(v -> {
            if (modelFile.exists() && modelFile.length() > 1024 && configFile.exists()) {
                Toast.makeText(context, "Playing preview...", Toast.LENGTH_SHORT).show();
                String previewText = VoiceLangHelper.getSampleTextForVoice(voice.getKey().replace("__turbo","").replace("__crisp","").replace("__sonic",""));
                VoiceLangHelper.tuneConfigForZeroStutter(configFile, voice.getKey().replace("__turbo","").replace("__crisp","").replace("__sonic",""));
                playRealPreview(modelFile.getAbsolutePath(), configFile.getAbsolutePath(), previewText, context);
            } else {
                Toast.makeText(context, "Please download this voice first to preview!", Toast.LENGTH_SHORT).show();
            }
        });

        holder.btnDownload.setOnClickListener(v -> {
            activeProgress.put(voice.getKey(), 0);
            notifyItemChanged(holder.getAdapterPosition());
            downloadVoiceFile(context, voice, holder);
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (modelFile.exists()) modelFile.delete();
            if (configFile.exists()) configFile.delete();
            Toast.makeText(context, "Voice Deleted", Toast.LENGTH_SHORT).show();
            notifyItemChanged(holder.getAdapterPosition());
        });
    }
    private void playRealPreview(String modelPath, String configPath, String text, Context context) {
        executorService.execute(() -> {
            try {
                PiperEngine engine = new PiperEngine();
                PiperEngine.applyModeForPath(modelPath);
                if (engine.initEngine(modelPath, configPath, PiperEngine.setupEspeakData(context))) {
                    byte[] audioData = engine.synthesizeText(text, 1.0f);
                    if (audioData != null && audioData.length > 0) {
                        int sr = VoiceLangHelper.readSampleRate(new File(configPath));
                        PiperAudioPlayer.playAudio(audioData, sr);
                    } else {
                        mainHandler.post(() -> Toast.makeText(context, "Synthesis failed", Toast.LENGTH_SHORT).show());
                    }
                    engine.destroyEngine();
                } else {
                    mainHandler.post(() -> Toast.makeText(context, "Engine Init Failed!", Toast.LENGTH_LONG).show());
                }
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    private void downloadVoiceFile(Context context, VoiceModel voice, VoiceViewHolder holder) {
        executorService.execute(() -> {
            try {
                File voicesDir = new File(context.getFilesDir(), "voices");
                if (!voicesDir.exists()) voicesDir.mkdirs();
                File modelFile = new File(voicesDir, voice.getKey() + ".onnx");
                File configFile = new File(voicesDir, voice.getKey() + ".onnx.json");

                downloadSingleWithProgress(voice.getConfigUrl(), configFile, voice.getKey(), holder, false);
                VoiceLangHelper.tuneConfigForZeroStutter(configFile, voice.getKey().replace("__turbo","").replace("__crisp","").replace("__sonic",""));
                downloadSingleWithProgress(voice.getModelUrl(), modelFile, voice.getKey(), holder, true);

                mainHandler.post(() -> {
                    activeProgress.remove(voice.getKey());
                    Toast.makeText(context, "Download Complete 100%: " + voice.getName(), Toast.LENGTH_SHORT).show();
                    notifyDataSetChanged();
                });
            } catch (Exception e) {
                e.printStackTrace();
                mainHandler.post(() -> {
                    activeProgress.remove(voice.getKey());
                    Toast.makeText(context, "Download Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    notifyDataSetChanged();
                });
            }
        });
    }
    private void downloadSingleWithProgress(String urlStr, File outputFile, String voiceKey, VoiceViewHolder holder, boolean trackPercent) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.connect();
        int code = conn.getResponseCode();
        if (code >= 300 && code < 400) {
            String loc = conn.getHeaderField("Location");
            if (loc != null) {
                conn.disconnect();
                conn = (HttpURLConnection) new URL(loc).openConnection();
                conn.connect();
            }
        }
        int totalLen = conn.getContentLength();
        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(outputFile);
        byte[] buf = new byte[8192];
        int count;
        long downloaded = 0;
        int lastPct = -1;
        while ((count = in.read(buf)) != -1) {
            out.write(buf, 0, count);
            downloaded += count;
            if (trackPercent && totalLen > 0) {
                int pct = (int) ((downloaded * 100L) / totalLen);
                if (pct > 100) pct = 100;
                if (pct != lastPct && (pct - lastPct >= 2 || pct == 100)) {
                    lastPct = pct;
                    final int finalPct = pct;
                    activeProgress.put(voiceKey, finalPct);
                    mainHandler.post(() -> {
                        holder.btnDownload.setText("Downloading " + finalPct + "%");
                        if (holder.progressBar != null) {
                            holder.progressBar.setVisibility(View.VISIBLE);
                            holder.progressBar.setProgress(finalPct);
                        }
                    });
                }
            }
        }
        out.flush();
        out.close();
        in.close();
        if (outputFile.length() < 100) {
            outputFile.delete();
            throw new Exception("File Corrupt (Too Small)");
        }
    }

    @Override
    public int getItemCount() { return voiceList.size(); }

    public static class VoiceViewHolder extends RecyclerView.ViewHolder {
        TextView tvVoiceName, tvVoiceSize;
        Button btnDownload, btnDelete, btnPreview;
        ProgressBar progressBar;

        public VoiceViewHolder(@NonNull View itemView) {
            super(itemView);
            tvVoiceName = itemView.findViewById(R.id.tv_voice_name);
            tvVoiceSize = itemView.findViewById(R.id.tv_voice_size);
            btnDownload = itemView.findViewById(R.id.btn_download);
            btnDelete = itemView.findViewById(R.id.btn_delete);
            btnPreview = itemView.findViewById(R.id.btn_preview);
            if (itemView instanceof ViewGroup) {
                progressBar = new ProgressBar(itemView.getContext(), null, android.R.attr.progressBarStyleHorizontal);
                progressBar.setMax(100);
                progressBar.setVisibility(View.GONE);
                ViewGroup vg = (ViewGroup) itemView;
                if (vg.getChildCount() > 0 && vg.getChildAt(0) instanceof LinearLayout) {
                    ((LinearLayout) vg.getChildAt(0)).addView(progressBar);
                } else if (vg instanceof LinearLayout) {
                    vg.addView(progressBar);
                }
            }
        }
    }
}
