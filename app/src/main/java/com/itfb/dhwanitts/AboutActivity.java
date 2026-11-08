package com.itfb.dhwanitts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        Button btnHelp = findViewById(R.id.btn_help_feedback);
        Button btnContact = findViewById(R.id.btn_contact_developer);

        // Help & Feedback बटन पर क्लिक करने से Email खुलेगा
        btnHelp.setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
            emailIntent.setData(Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Help and Feedback - Dhwani TTS");
            try {
                startActivity(emailIntent);
            } catch (Exception e) {
                Toast.makeText(this, "Email app not found!", Toast.LENGTH_SHORT).show();
            }
        });

        // Contact Developer बटन पर क्लिक करने से WhatsApp खुलेगा
        btnContact.setOnClickListener(v -> {
            String message = "Hello ITFB Team, I am using Dhwani TTS and I need help regarding...";
            // भारत के कंट्री कोड (+91) के साथ आपका नंबर जोड़ा गया है
            String url = "https://api.whatsapp.com/send?phone=+916260503286&text=" + Uri.encode(message);
            Intent waIntent = new Intent(Intent.ACTION_VIEW);
            waIntent.setData(Uri.parse(url));
            try {
                startActivity(waIntent);
            } catch (Exception e) {
                Toast.makeText(this, "WhatsApp is not installed!", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
