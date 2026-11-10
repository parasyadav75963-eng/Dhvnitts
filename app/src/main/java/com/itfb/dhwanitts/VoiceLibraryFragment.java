package com.itfb.dhwanitts;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceLibraryFragment extends Fragment {
    private RecyclerView recyclerView;
    private VoiceAdapter adapter;
    private List<VoiceModel> voiceList = new ArrayList<>();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_library, container, false);
        recyclerView = view.findViewById(R.id.recycler_voices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        adapter = new VoiceAdapter(voiceList);
        recyclerView.setAdapter(adapter);
        
        fetchOfficialVoices();
        return view;
    }

    private void fetchOfficialVoices() {
        Toast.makeText(getContext(), "Syncing official Piper catalog...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            try {
                URL url = new URL("https://huggingface.co/rhasspy/piper-voices/resolve/main/voices.json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();

                String jsonStr = response.toString();
                
                FileOutputStream fos = requireContext().openFileOutput("cached_voices.json", Context.MODE_PRIVATE);
                fos.write(jsonStr.getBytes());
                fos.close();

                parseVoicesJson(jsonStr);
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(getContext(), "Network Error. Loading cache.", Toast.LENGTH_LONG).show());
                loadFromCache();
            }
        });
    }

    private void loadFromCache() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(requireContext().openFileInput("cached_voices.json")));
            StringBuilder sb = new StringBuilder();
            String line;
            while((line = br.readLine()) != null) sb.append(line);
            br.close();
            parseVoicesJson(sb.toString());
        } catch (Exception e) {
            mainHandler.post(() -> Toast.makeText(getContext(), "No cached data. Please connect to internet.", Toast.LENGTH_LONG).show());
        }
    }

    private void parseVoicesJson(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            List<VoiceModel> fetchedVoices = new ArrayList<>();
            File voicesDir = new File(requireContext().getFilesDir(), "voices");

            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject voiceObj = root.getJSONObject(key);
                
                JSONObject langObj = voiceObj.getJSONObject("language");
                String langEnglish = langObj.optString("name_english", "Unknown");
                String country = langObj.optString("country_english", "");
                String quality = voiceObj.optString("quality", "medium");
                String voiceName = voiceObj.optString("name", "Unknown");

                JSONObject filesObj = voiceObj.getJSONObject("files");
                String modelPath = "";
                String configPath = "";
                long totalSizeBytes = 0;

                Iterator<String> fileKeys = filesObj.keys();
                while (fileKeys.hasNext()) {
                    String filePath = fileKeys.next();
                    if (filePath.endsWith(".onnx")) modelPath = filePath;
                    else if (filePath.endsWith(".json")) configPath = filePath;
                    
                    JSONObject fileMeta = filesObj.getJSONObject(filePath);
                    totalSizeBytes += fileMeta.optLong("size_bytes", 0);
                }

                if (modelPath.isEmpty() || configPath.isEmpty()) continue;

                String sizeMB = String.format("%.1f MB", totalSizeBytes / (1024.0 * 1024.0));
                
                File checkModel = new File(voicesDir, key + ".onnx");
                File checkConfig = new File(voicesDir, key + ".onnx.json");
                boolean isDownloaded = checkModel.exists() && checkConfig.exists() && checkModel.length() > 50000;

                fetchedVoices.add(new VoiceModel(key, langEnglish, country, voiceName, quality, modelPath, configPath, sizeMB, isDownloaded));
            }

            mainHandler.post(() -> {
                voiceList.clear();
                voiceList.addAll(fetchedVoices);
                adapter.notifyDataSetChanged();
                Toast.makeText(getContext(), "Voices Loaded!", Toast.LENGTH_SHORT).show();
            });
        } catch (Exception e) { e.printStackTrace(); }
    }
}
