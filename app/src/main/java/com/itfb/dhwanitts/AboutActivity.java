package com.itfb.dhwanitts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        TextView tvDesc = findViewById(R.id.tv_about_description);
        String description = "Welcome to DhwaniTTS by Interested Technology for Blind (ITFB) – The Real Game-Changer for Visually Impaired Users!\n\n"
                + "DhwaniTTS is a high-performance, offline neural Text-to-Speech engine designed for crystal-clear multi-language synthesis, ultra-fast response with TalkBack, and smart Single, Dual, and Mix language switching.\n\n"
                + "At Interested Technology for Blind, we don't just talk tech — we make tech feelable. From screen reader tricks to mind-blowing Android hacks, from accessible gaming to smart tutorials and accessible software — this is the ultimate zone for blind users who want to explore their phones beyond limits.\n\n"
                + "Our community is not just content — it's confidence, clarity, and coolness rolled into one. Whether you're a beginner or a tech ninja with no sight, here you'll find tips, apps, reviews, and tools that truly speak your language — audio!\n\n"
                + "⚡ Real tricks. 💡 Real talk. 🎧 100% Voice-Accessible.\n"
                + "No confusion. No complications. Just pure usable tech — because here, blindness meets brilliance!";
        tvDesc.setText(description);

        findViewById(R.id.btn_join_groups).setOnClickListener(v -> {
            String message = "Hello Admin, I agree with all the rules created by you, and I would like to join your WhatsApp or Telegram group. Please add me.";
            openUrl("https://api.whatsapp.com/send?phone=916260503286&text=" + Uri.encode(message));
        });

        findViewById(R.id.btn_youtube).setOnClickListener(v -> openUrl("https://www.youtube.com/@INTERESTEDTECHNOLOGYFORBLIND"));
        findViewById(R.id.btn_whatsapp_channel).setOnClickListener(v -> openUrl("https://whatsapp.com/channel/0029Va4fpB4GJP8PZEI26y1C"));
        findViewById(R.id.btn_telegram_channel).setOnClickListener(v -> openUrl("https://t.me/interestedtechnologyforblind"));
        findViewById(R.id.btn_facebook).setOnClickListener(v -> openUrl("https://facebook.com/Interestedtechnologyforblind"));
        findViewById(R.id.btn_instagram).setOnClickListener(v -> openUrl("https://instagram.com/interestedtechnologyforblind"));
        findViewById(R.id.btn_x_twitter).setOnClickListener(v -> openUrl("https://x.com/Interested131"));

        findViewById(R.id.btn_help_feedback).setOnClickListener(v -> {
            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:contact.i.t.f.b@gmail.com"));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "DhwaniTTS Help & Feedback");
            try {
                startActivity(Intent.createChooser(emailIntent, "Send Email"));
            } catch (Exception e) {
                Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open link", Toast.LENGTH_SHORT).show();
        }
    }
}
