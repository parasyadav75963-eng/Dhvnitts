package com.itfb.dhwanitts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class VoiceLibraryFragment extends Fragment {
    private RecyclerView recyclerView;
    private VoiceAdapter adapter;
    private List<VoiceModel> voiceList;
    private String selectedLanguage = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_library, container, false);
        recyclerView = view.findViewById(R.id.recycler_voices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        voiceList = new ArrayList<>();
        
        if (getArguments() != null) {
            selectedLanguage = getArguments().getString("selected_language", "");
        }
        
        loadVoicesFromJson(selectedLanguage);
        adapter = new VoiceAdapter(voiceList);
        recyclerView.setAdapter(adapter);
        return view;
    }

    private void loadVoicesFromJson(String langFilter) {
        try {
            InputStream is = requireContext().getAssets().open("voices.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String jsonStr = new String(buffer, "UTF-8");
            JSONObject jsonObject = new JSONObject(jsonStr);
            JSONArray languagesArray = jsonObject.getJSONArray("languages");
            
            for (int i = 0; i < languagesArray.length(); i++) {
                JSONObject langObj = languagesArray.getJSONObject(i);
                if (langObj.getString("language").equals(langFilter)) {
                    JSONArray voicesArray = langObj.getJSONArray("voices");
                    for (int j = 0; j < voicesArray.length(); j++) {
                        JSONObject voiceObj = voicesArray.getJSONObject(j);
                        voiceList.add(new VoiceModel(
                            langFilter,
                            voiceObj.getString("voiceName"),
                            voiceObj.getString("size"),
                            voiceObj.getString("downloadUrl"),
                            false
                        ));
                    }
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
