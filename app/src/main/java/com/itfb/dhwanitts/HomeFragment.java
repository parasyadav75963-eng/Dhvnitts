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

public class HomeFragment extends Fragment {
    private RecyclerView recyclerView;
    private LanguageAdapter adapter;
    private List<String> languageList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);
        recyclerView = view.findViewById(R.id.recycler_languages);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        languageList = new ArrayList<>();
        
        loadLanguagesFromJson();
        
        adapter = new LanguageAdapter(languageList, lang -> {
            Fragment libraryFragment = new VoiceLibraryFragment();
            Bundle bundle = new Bundle();
            bundle.putString("selected_language", lang);
            libraryFragment.setArguments(bundle);
            
            // addToBackStack जोड़ा गया ताकि बैक बटन सही से काम करे
            requireActivity().getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, libraryFragment)
                    .addToBackStack(null)
                    .commit();
        });
        recyclerView.setAdapter(adapter);
        return view;
    }

    private void loadLanguagesFromJson() {
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
                languageList.add(langObj.getString("language"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
