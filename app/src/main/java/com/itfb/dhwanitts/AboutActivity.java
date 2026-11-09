package com.itfb.dhwanitts;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // यहाँ activity_settings की जगह activity_about सेट कर दिया गया है
        setContentView(R.layout.activity_about);
    }
}
