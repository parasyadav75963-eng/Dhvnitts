package com.itfb.dhwanitts;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class PronunciationDictionary {
    private static final String PREF_KEY = "custom_pronunciation_dict_json";

    public static String apply(Context ctx, String text) {
        if (text == null || text.trim().isEmpty()) return "";
        String out = text;

        // 1. User Custom Dictionary (App Settings -> Dictionary Editor)
        if (ctx != null) {
            Map<String, String> custom = getCustomEntries(ctx);
            for (Map.Entry<String, String> e : custom.entrySet()) {
                String k = e.getKey().trim();
                String v = e.getValue().trim();
                if (!k.isEmpty() && !v.isEmpty()) {
                    out = out.replaceAll("(?i)(?<!\\w)" + Pattern.quote(k) + "(?!\\w)", v);
                }
            }
        }

        // 2. Smart Auto-Normalizer (Symbols, Indian Units, Common Tech & Mix Words)
        out = out.replace("₹", " rupees ")
                 .replace("%", " percent ")
                 .replace("&", " and ")
                 .replace("@", " at ")
                 .replace("+", " plus ")
                 .replaceAll("(?i)\\bRs\\.?\\s*(\\d+)", "$1 rupees")
                 .replaceAll("(?i)\\bDr\\.", "Doctor")
                 .replaceAll("(?i)\\bMr\\.", "Mister")
                 .replaceAll("(?i)\\bMrs\\.", "Misses")
                 .replaceAll("(?i)\\bSt\\.", "Saint")
                 .replaceAll("(?i)\\bNo\\.", "Number")
                 .replaceAll("(?i)\\bvs\\.?", "versus")
                 .replaceAll("(?i)\\betc\\.?", "etcetera")
                 .replaceAll("(?i)\\bwhatsapp\\b", "Whats App")
                 .replaceAll("(?i)\\byoutube\\b", "You Tube")
                 .replaceAll("(?i)\\bgithub\\b", "Git Hub")
                 .replaceAll("(?i)\\btermux\\b", "Ter mux")
                 .replaceAll("(?i)\\bdhwanitts\\b", "Dhwani T T S")
                 .replaceAll("(?i)\\btalkback\\b", "Talk Back")
                 .replaceAll("(?i)\\bocr\\b", "O C R")
                 .replaceAll("(?i)\\bpdf\\b", "P D F")
                 .replaceAll("(?i)\\bupi\\b", "U P I")
                 .replaceAll("(?i)\\botp\\b", "O T P")
                 .replaceAll("(?i)\\bsms\\b", "S M S")
                 .replaceAll("(?i)\\burl\\b", "U R L")
                 .replaceAll("(?i)\\bapi\\b", "A P I")
                 .replaceAll("(?i)\\bcpu\\b", "C P U")
                 .replaceAll("(?i)\\bram\\b", "ram")
                 .replaceAll("(?i)\\b5g\\b", "5 G")
                 .replaceAll("(?i)\\b4g\\b", "4 G")
                 .replaceAll("(?i)\\bhd\\b", "H D")
                 .replaceAll("(?i)\\bai\\b", "A I");

        return out.replaceAll("\\s+", " ").trim();
    }

    public static Map<String, String> getCustomEntries(Context ctx) {
        Map<String, String> map = new LinkedHashMap<>();
        try {
            SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
            JSONObject obj = new JSONObject(sp.getString(PREF_KEY, "{}"));
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                map.put(k, obj.optString(k, ""));
            }
        } catch (Exception ignored) {}
        return map;
    }

    public static void saveEntry(Context ctx, String word, String replacement) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
            JSONObject obj = new JSONObject(sp.getString(PREF_KEY, "{}"));
            obj.put(word.trim(), replacement.trim());
            sp.edit().putString(PREF_KEY, obj.toString()).apply();
        } catch (Exception ignored) {}
    }

    public static void removeEntry(Context ctx, String word) {
        try {
            SharedPreferences sp = ctx.getSharedPreferences("DhwaniTTS_Prefs", Context.MODE_PRIVATE);
            JSONObject obj = new JSONObject(sp.getString(PREF_KEY, "{}"));
            obj.remove(word);
            sp.edit().putString(PREF_KEY, obj.toString()).apply();
        } catch (Exception ignored) {}
    }
}
