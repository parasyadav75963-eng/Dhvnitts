package com.itfb.dhwanitts;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class VoiceLibraryFragment extends Fragment {

    private RecyclerView recyclerView;
    private TextView tvHeader;
    
    private LanguageAdapter languageAdapter;
    private VoiceAdapter voiceAdapter;
    
    private List<VoiceModel> allVoicesList;
    private List<String> uniqueLanguagesList;
    
    private boolean isShowingVoices = false; // यह चेक करेगा कि हम अभी भाषा देख रहे हैं या वॉइसेस

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_voice_library, container, false);

        tvHeader = view.findViewById(R.id.btn_nav_back); // हम Header का इस्तेमाल कर रहे हैं
        Button btnNavBack = view.findViewById(R.id.btn_nav_back);
        recyclerView = view.findViewById(R.id.recycler_view_voices);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        allVoicesList = new ArrayList<>();
        uniqueLanguagesList = new ArrayList<>();
        
        loadVoicesFromJson();

        // 1. Language Adapter तैयार करना (जिसमें क्लिक करने का लॉजिक है)
        languageAdapter = new LanguageAdapter(uniqueLanguagesList, language -> {
            // जब कोई भाषा पर क्लिक करे, तो उस भाषा की वॉइसेस छांटना
            List<VoiceModel> filteredVoices = new ArrayList<>();
            for (VoiceModel v : allVoicesList) {
                if (v.getLanguage().equals(language)) {
                    filteredVoices.add(v);
                }
            }
            
            // नया Voice Adapter सेट करना
            voiceAdapter = new VoiceAdapter(filteredVoices);
            recyclerView.setAdapter(voiceAdapter);
            isShowingVoices = true;
            btnNavBack.setText("Back to Languages");
        });

        // शुरुआत में भाषाओं की लिस्ट सेट करना
        recyclerView.setAdapter(languageAdapter);
        isShowingVoices = false;
        btnNavBack.setText("Navigate Back (Home)");

        // 'Navigate Back' बटन का लॉजिक अपडेट किया गया
        btnNavBack.setOnClickListener(v -> {
            if (isShowingVoices) {
                // अगर वॉइसेस देख रहे हैं, तो वापस भाषाओं की लिस्ट पर जाएँ
                recyclerView.setAdapter(languageAdapter);
                isShowingVoices = false;
                btnNavBack.setText("Navigate Back (Home)");
            } else {
                // अगर भाषाओं की लिस्ट पर हैं, तो Home टैब पर जाएँ
                BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
                bottomNav.setSelectedItemId(R.id.nav_home);
            }
        });

        return view;
    }

    private void loadVoicesFromJson() {
        try {
            InputStream is = requireContext().getAssets().open("voices.json");
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            
            String jsonString = new String(buffer, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(jsonString);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);
                String lang = obj.getString("language");
                String name = obj.getString("voiceName");
                String vSize = obj.getString("size");
                String url = obj.getString("downloadUrl");
                
                allVoicesList.add(new VoiceModel(lang, name, vSize, url, false));
                
                // सिर्फ अनोखी (unique) भाषाओं को लिस्ट में डालना
                if (!uniqueLanguagesList.contains(lang)) {
                    uniqueLanguagesList.add(lang);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Error loading JSON!", Toast.LENGTH_SHORT).show();
        }
    }
}
