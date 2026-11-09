package com.itfb.dhwanitts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import java.util.ArrayList;

public class CheckVoiceDataActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Intent returnData = new Intent();
        
        // सिस्टम को बताना कि हमारे पास कौन सी भाषाएं उपलब्ध हैं (उदाहरण)
        ArrayList<String> availableVoices = new ArrayList<>();
        availableVoices.add("hi-IN");
        availableVoices.add("en-US");
        availableVoices.add("en-GB");
        
        returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, availableVoices);
        returnData.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<String>());
        
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);
        finish();
    }
}
