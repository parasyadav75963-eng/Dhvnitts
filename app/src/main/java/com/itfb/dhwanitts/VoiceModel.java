package com.itfb.dhwanitts;

public class VoiceModel {
    private String language;
    private String voiceName;
    private String size;
    private String downloadUrl;
    private boolean isDownloaded;

    public VoiceModel(String language, String voiceName, String size, String downloadUrl, boolean isDownloaded) {
        this.language = language;
        this.voiceName = voiceName;
        this.size = size;
        this.downloadUrl = downloadUrl;
        this.isDownloaded = isDownloaded;
    }

    public String getLanguage() {
        return language;
    }

    public String getVoiceName() {
        return voiceName;
    }

    public String getSize() {
        return size;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }
}
