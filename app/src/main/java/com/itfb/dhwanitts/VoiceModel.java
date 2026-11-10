package com.itfb.dhwanitts;

public class VoiceModel {
    private String key;
    private String languageName;
    private String country;
    private String voiceName;
    private String quality;
    private String modelPath;
    private String configPath;
    private String sizeMB;
    private boolean isDownloaded;

    public VoiceModel(String key, String languageName, String country, String voiceName, String quality, String modelPath, String configPath, String sizeMB, boolean isDownloaded) {
        this.key = key;
        this.languageName = languageName;
        this.country = country;
        this.voiceName = voiceName;
        this.quality = quality;
        this.modelPath = modelPath;
        this.configPath = configPath;
        this.sizeMB = sizeMB;
        this.isDownloaded = isDownloaded;
    }

    public String getKey() { return key; }
    public String getLanguageName() { return languageName; }
    public String getCountry() { return country; }
    public String getVoiceName() { return voiceName; }
    public String getQuality() { return quality; }
    public String getModelPath() { return modelPath; }
    public String getConfigPath() { return configPath; }
    public String getSizeMB() { return sizeMB; }
    public boolean isDownloaded() { return isDownloaded; }
    public void setDownloaded(boolean downloaded) { isDownloaded = downloaded; }
}
