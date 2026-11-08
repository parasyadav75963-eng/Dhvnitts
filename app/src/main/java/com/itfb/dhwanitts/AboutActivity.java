package com.itfb.dhwanitts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // डेवलपर कॉन्टैक्ट बटन ढूँढना और WhatsApp जोड़ना
        Button btnContact = findViewById(R.id.btn_contact_developer);
        if (btnContact != null) {
            btnContact.setOnClickListener(v -> {
                // आपका ITFB WhatsApp नंबर (+916260503286)
                String phoneNumber = "+916260503286";
                String message = "Hello, I am using DhwaniTTS and want to connect with you.";
                String url = "https://api.whatsapp.com/send?phone=" + phoneNumber + "&text=" + Uri.encode(message);
                
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    // अगर WhatsApp इनस्टॉल न हो तो ब्राउज़र या एरर हैंडलिंग
                }
            });
        }
    }
}
