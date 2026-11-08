package com.itfb.dhwanitts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

public class CheckVoiceDataActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // एंड्रॉयड को सिग्नल भेजना कि हमारा TTS डेटा पास/रेडी है
        Intent returnData = new Intent();
        setResult(TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, returnData);
        finish(); // तुरंत एक्टिविटी बंद कर देना ताकि स्क्रीन पर कुछ न दिखे
    }
}
