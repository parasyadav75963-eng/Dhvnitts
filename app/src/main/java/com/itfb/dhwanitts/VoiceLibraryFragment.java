package com.itfb.dhwanitts;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceLibraryFragment extends Fragment {
    private RecyclerView recyclerView;
    private Spinner langSpinner;
    private VoiceAdapter adapter;
    private final List<VoiceModel> voiceList = new ArrayList<>();
    private final List<VoiceModel> allVoicesMaster = new ArrayList<>();
    private final List<String> langOptions = new ArrayList<>();
    private ArrayAdapter<String> spinnerAdapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_library, container, false);
        recyclerView = view.findViewById(R.id.recycler_voices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VoiceAdapter(voiceList);
        recyclerView.setAdapter(adapter);

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            LinearLayout filterBox = new LinearLayout(requireContext());
            filterBox.setOrientation(LinearLayout.VERTICAL);
            filterBox.setPadding(32, 24, 32, 16);

            TextView lbl = new TextView(requireContext());
            lbl.setText("Select Language (A to Z):");
            lbl.setTextSize(16f);
            lbl.setTextColor(0xFFFFFFFF);
            filterBox.addView(lbl);

            langSpinner = new Spinner(requireContext());
            langSpinner.setContentDescription("Select Language A to Z Dropdown");
            langOptions.clear();
            langOptions.add("Loading languages...");
            spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, langOptions);
            langSpinner.setAdapter(spinnerAdapter);
            langSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    if (position >= 0 && position < langOptions.size()) {
                        filterVoicesByLanguage(langOptions.get(position));
                    }
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            });
            filterBox.addView(langSpinner);
            root.addView(filterBox, 0);
        }

        fetchOfficialVoices();
        return view;
    }

    private void filterVoicesByLanguage(String selectedLang) {
        if (allVoicesMaster.isEmpty()) return;
        voiceList.clear();
        for (VoiceModel vm : allVoicesMaster) {
            if (selectedLang.startsWith("Installed Voices")) {
                if (vm.isDownloaded()) voiceList.add(vm);
            } else if (vm.getLanguageName().equalsIgnoreCase(selectedLang)) {
                voiceList.add(vm);
            }
        }
        adapter.notifyDataSetChanged();
    }
    private void fetchOfficialVoices() {
        executorService.execute(() -> {
            try {
                URL url = new URL("https://huggingface.co/rhasspy/piper-voices/resolve/main/voices.json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String jsonStr = sb.toString();
                FileOutputStream fos = requireContext().openFileOutput("cached_voices.json", 0);
                fos.write(jsonStr.getBytes());
                fos.close();

                parseVoicesJson(jsonStr);
            } catch (Exception e) {
                loadFromCache();
            }
        });
    }

    private void loadFromCache() {
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(requireContext().openFileInput("cached_voices.json")));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            parseVoicesJson(sb.toString());
        } catch (Exception e) {
            mainHandler.post(() -> {
                if (getContext() != null) {
                    Toast.makeText(getContext(), "No cached data. Please connect to internet.", Toast.LENGTH_LONG).show();
                }
            });
        }
    }
    private void parseVoicesJson(String jsonStr) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            Iterator<String> keys = root.keys();
            List<VoiceModel> fetchedVoices = new ArrayList<>();
            Set<String> uniqueLangs = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            File voicesDir = new File(requireContext().getFilesDir(), "voices");
            if (!voicesDir.exists()) voicesDir.mkdirs();

            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject vObj = root.getJSONObject(key);
                String voiceName = vObj.optString("name", key);
                String quality = vObj.optString("quality", "medium");

                JSONObject langObj = vObj.optJSONObject("language");
                String langEnglish = langObj != null ? langObj.optString("name_english", "English") : "English";
                String country = langObj != null ? langObj.optString("country_english", "India") : "";

                JSONObject filesObj = vObj.optJSONObject("files");
                if (filesObj == null) continue;

                String modelPath = "", configPath = "";
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
                uniqueLangs.add(langEnglish);
            }

            VoiceLangHelper.sortVoicesIndianFirst(fetchedVoices);
            mainHandler.post(() -> {
                allVoicesMaster.clear();
                allVoicesMaster.addAll(fetchedVoices);

                langOptions.clear();
                langOptions.add("Installed Voices (Downloaded)");
                langOptions.addAll(uniqueLangs);
                spinnerAdapter.notifyDataSetChanged();

                int defaultIdx = langOptions.indexOf("English");
                if (defaultIdx < 0) defaultIdx = 0;
                langSpinner.setSelection(defaultIdx);
                filterVoicesByLanguage(langOptions.get(defaultIdx));
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
