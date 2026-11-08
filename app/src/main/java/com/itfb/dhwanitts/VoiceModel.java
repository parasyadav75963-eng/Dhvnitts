package com.itfb.dhwanitts;

public class VoiceModel {
    String language;
    String voiceName;
    String size;
    String downloadUrl;
    boolean isDownloaded;

    public VoiceModel(String language, String voiceName, String size, String downloadUrl, boolean isDownloaded) {
        this.language = language;
        this.voiceName = voiceName;
        this.size = size;
        this.downloadUrl = downloadUrl;
        this.isDownloaded = isDownloaded;
    }
}
