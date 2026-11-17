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
    private String engineMode;

    private static final String PIPER_BASE = "https://huggingface.co/rhasspy/piper-voices/resolve/main/";

    public VoiceModel(String key, String languageName, String country, String voiceName, String quality, String modelPath, String configPath, String sizeMB, boolean isDownloaded) {
        this(key, languageName, country, voiceName, quality, modelPath, configPath, sizeMB, isDownloaded, "Dhwani Natural");
    }

    public VoiceModel(String key, String languageName, String country, String voiceName, String quality, String modelPath, String configPath, String sizeMB, boolean isDownloaded, String engineMode) {
        this.key = key;
        this.languageName = languageName;
        this.country = country;
        this.voiceName = voiceName;
        this.quality = quality;
        this.modelPath = modelPath;
        this.configPath = configPath;
        this.sizeMB = sizeMB;
        this.isDownloaded = isDownloaded;
        this.engineMode = (engineMode == null || engineMode.isEmpty()) ? "Dhwani Natural" : engineMode;
    }

    public String getKey() { return key; }
    public String getLanguageName() { return languageName; }
    public String getCountry() { return country; }
    public String getVoiceName() { return voiceName; }
    public String getName() { return languageName + " - " + voiceName; }
    public String getQuality() { return quality; }
    public String getModelPath() { return modelPath; }
    public String getConfigPath() { return configPath; }
    public String getEngineMode() { return engineMode; }
    public String getModelUrl() {
        return (modelPath != null && modelPath.startsWith("http")) ? modelPath : PIPER_BASE + modelPath;
    }
    public String getConfigUrl() {
        return (configPath != null && configPath.startsWith("http")) ? configPath : PIPER_BASE + configPath;
    }
    public String getSizeMB() { return sizeMB; }
    public String getSizeInfo() { return "Mode: " + engineMode + " | Size: " + sizeMB + (country.isEmpty() ? "" : " | Country: " + country); }
    public boolean isDownloaded() { return isDownloaded; }
    public void setDownloaded(boolean downloaded) { isDownloaded = downloaded; }
}
