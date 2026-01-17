package com.printflow.entity.enums;

public enum Language {
    SR("sr", "Српски"),
    EN("en", "English"),
    FR("fr", "Français"),
    RU("ru", "Русский"),
    TR("tr", "Türkçe");
    
    private final String code;
    private final String displayName;
    
    Language(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }
    
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
}