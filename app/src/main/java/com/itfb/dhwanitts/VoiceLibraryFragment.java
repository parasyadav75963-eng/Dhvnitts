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
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class VoiceLibraryFragment extends Fragment {
    private RecyclerView recyclerView;
    private Spinner modeSpinner, langSpinner;
    private VoiceAdapter adapter;
    private final List<VoiceModel> voiceList = new ArrayList<>();
    private final List<VoiceModel> allVoicesMaster = new ArrayList<>();
    private final List<String> modeOptions = new ArrayList<>();
    private final List<String> langOptions = new ArrayList<>();
    private ArrayAdapter<String> modeAdapter, langAdapter;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static final String MODE_NATURAL = "Dhwani Natural (Neural HD)";
    public static final String MODE_SONIC = "Dhwani Sonic (Ultra-Lite Fast)";
    public static final String MODE_CRISP = "Dhwani Crisp (Classic Reflex)";
    public static final String MODE_TURBO = "Dhwani Turbo (Formant Robotic)";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_library, container, false);
        recyclerView = view.findViewById(R.id.recycler_voices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new VoiceAdapter(voiceList);
        recyclerView.setItemAnimator(null);
        recyclerView.setItemAnimator(null);
        recyclerView.setAdapter(adapter);

        if (view instanceof ViewGroup) {
            ViewGroup root = (ViewGroup) view;
            LinearLayout filterBox = new LinearLayout(requireContext());
            filterBox.setOrientation(LinearLayout.VERTICAL);
            filterBox.setPadding(32, 24, 32, 16);

            TextView modeLbl = new TextView(requireContext());
            modeLbl.setText("Select TTS Engine Mode:");
            modeLbl.setTextSize(15f);
            modeLbl.setTextColor(0xFF00E676);
            filterBox.addView(modeLbl);

            modeSpinner = new Spinner(requireContext());
            modeSpinner.setContentDescription("Select TTS Engine Mode Dropdown");
            modeOptions.clear();
            modeOptions.add(MODE_NATURAL);
            modeOptions.add(MODE_SONIC);
            modeOptions.add(MODE_CRISP);
            modeOptions.add(MODE_TURBO);
            modeOptions.add(EloquenceBridge.MODE_ELOQUENCE);
            modeAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, modeOptions);
            modeSpinner.setAdapter(modeAdapter);
            filterBox.addView(modeSpinner);

            TextView langLbl = new TextView(requireContext());
            langLbl.setText("Select Language (A to Z):");
            langLbl.setTextSize(15f);
            langLbl.setTextColor(0xFF00E676);
            langLbl.setPadding(0, 16, 0, 0);
            filterBox.addView(langLbl);

            langSpinner = new Spinner(requireContext());
            langSpinner.setContentDescription("Select Language A to Z Dropdown");
            langOptions.clear();
            langOptions.add("Loading languages...");
            langAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, langOptions);
            langSpinner.setAdapter(langAdapter);
            filterBox.addView(langSpinner);

            AdapterView.OnItemSelectedListener filterListener = new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
                    applyModeAndLanguageFilter();
                }
                @Override
                public void onNothingSelected(AdapterView<?> parent) {}
            };
            modeSpinner.setOnItemSelectedListener(filterListener);
            langSpinner.setOnItemSelectedListener(filterListener);

            root.addView(filterBox, 0);
        }

        fetchOfficialVoices();
        return view;
    }

    private void applyModeAndLanguageFilter() {
        if (allVoicesMaster.isEmpty()) return;
        int mPos = modeSpinner != null ? modeSpinner.getSelectedItemPosition() : 0;
        int lPos = langSpinner != null ? langSpinner.getSelectedItemPosition() : 0;
        if (mPos < 0 || mPos >= modeOptions.size() || lPos < 0 || lPos >= langOptions.size()) return;

        String selectedMode = modeOptions.get(mPos);
        String selectedLang = langOptions.get(lPos);

        File voicesDir = new File(requireContext().getFilesDir(), "voices");
        voiceList.clear();
        for (VoiceModel vm : allVoicesMaster) {
            if (!selectedLang.startsWith("Installed Voices") && !vm.getEngineMode().equals(selectedMode)) continue;
            if (EloquenceBridge.isEloquenceKey(vm.getKey())) {
                vm.setDownloaded(true);
            } else {
                File checkModel = new File(voicesDir, vm.getKey() + ".onnx");
                File checkConfig = new File(voicesDir, vm.getKey() + ".onnx.json");
                vm.setDownloaded(checkModel.exists() && checkConfig.exists() && checkModel.length() > 1024);
            }

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

                // 1. Dhwani Natural
                File mNat = new File(voicesDir, key + ".onnx");
                File cNat = new File(voicesDir, key + ".onnx.json");
                fetchedVoices.add(new VoiceModel(key, langEnglish, country, voiceName, quality, modelPath, configPath, sizeMB, mNat.exists() && cNat.exists(), MODE_NATURAL));

                // 2. Dhwani Sonic (Ultra-Lite Fast)
                if (quality.equalsIgnoreCase("x_low") || quality.equalsIgnoreCase("low") || quality.equalsIgnoreCase("medium")) {
                    String sKey = key + "__sonic";
                    File mSon = new File(voicesDir, sKey + ".onnx");
                    File cSon = new File(voicesDir, sKey + ".onnx.json");
                    fetchedVoices.add(new VoiceModel(sKey, langEnglish, country, voiceName + " [Sonic]", "sonic-fast", modelPath, configPath, sizeMB, mSon.exists() && cSon.exists(), MODE_SONIC));
                }

                // 3. Dhwani Crisp (Classic Reflex)
                String cKey = key + "__crisp";
                File mCri = new File(voicesDir, cKey + ".onnx");
                File cCri = new File(voicesDir, cKey + ".onnx.json");
                fetchedVoices.add(new VoiceModel(cKey, langEnglish, country, voiceName + " [Crisp]", "crisp-reflex", modelPath, configPath, sizeMB, mCri.exists() && cCri.exists(), MODE_CRISP));

                // 4. Dhwani Turbo (Formant Robotic)
                String tKey = key + "__turbo";
                File mTur = new File(voicesDir, tKey + ".onnx");
                File cTur = new File(voicesDir, tKey + ".onnx.json");
                fetchedVoices.add(new VoiceModel(tKey, langEnglish, country, voiceName + " [Turbo]", "turbo-robotic", modelPath, configPath, sizeMB, mTur.exists() && cTur.exists(), MODE_TURBO));

                uniqueLangs.add(langEnglish);
            }

            for (VoiceModel evv : EloquenceBridge.getBuiltInVoices()) {
                fetchedVoices.add(evv);
                uniqueLangs.add(evv.getLanguageName());
            }
            VoiceLangHelper.sortVoicesIndianFirst(fetchedVoices);
            mainHandler.post(() -> {
                allVoicesMaster.clear();
                allVoicesMaster.addAll(fetchedVoices);

                langOptions.clear();
                langOptions.add("Installed Voices (Downloaded)");
                langOptions.addAll(uniqueLangs);
                langAdapter.notifyDataSetChanged();

                int defaultIdx = langOptions.indexOf("Hindi");
                if (defaultIdx < 0) defaultIdx = langOptions.indexOf("English");
                if (defaultIdx < 0) defaultIdx = 0;
                langSpinner.setSelection(defaultIdx);
                applyModeAndLanguageFilter();
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
