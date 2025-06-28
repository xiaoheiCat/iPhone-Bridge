package stu.xiaohei.iphonebridge;

import java.util.HashMap;
import java.util.Map;

public class ANCSConstants {
    
    // ANCS Service and Characteristics UUIDs
    public static final String SERVICE_ANCS = "7905F431-B5CE-4E99-A40F-4B1E122D00D0";
    public static final String CHAR_NOTIFICATION_SOURCE = "9FBF120D-6301-42D9-8C58-25E699A21DBD";
    public static final String CHAR_CONTROL_POINT = "69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9";
    public static final String CHAR_DATA_SOURCE = "22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB";
    public static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    
    // Error Codes
    public static final byte ERROR_UNKNOWN_COMMAND = (byte) 0xA0;
    public static final byte ERROR_INVALID_COMMAND = (byte) 0xA1;
    public static final byte ERROR_INVALID_PARAMETER = (byte) 0xA2;
    public static final byte ERROR_ACTION_FAILED = (byte) 0xA3;
    
    // App Icons Map (Unicode emojis for common apps)
    private static final Map<String, String> APP_ICONS = new HashMap<>();
    static {
        // Social
        APP_ICONS.put("com.tencent.xin", "💬");  // WeChat
        APP_ICONS.put("com.tencent.mqq", "🐧");  // QQ
        APP_ICONS.put("com.sina.weibo", "📢");  // Weibo
        APP_ICONS.put("com.facebook.Facebook", "📘");  // Facebook
        APP_ICONS.put("com.burbn.instagram", "📷");  // Instagram
        APP_ICONS.put("com.atebits.Tweetie2", "🐦");  // Twitter
        APP_ICONS.put("com.whatsapp.WhatsApp", "📱");  // WhatsApp
        APP_ICONS.put("com.skype.skype", "📞");  // Skype
        APP_ICONS.put("com.toyopagroup.picaboo", "👻");  // Snapchat
        
        // Communication
        APP_ICONS.put("com.apple.MobileSMS", "💬");  // Messages
        APP_ICONS.put("com.apple.mobilephone", "📞");  // Phone
        APP_ICONS.put("com.apple.facetime", "📹");  // FaceTime
        APP_ICONS.put("com.apple.mobilemail", "📧");  // Mail
        
        // Productivity
        APP_ICONS.put("com.apple.mobilecal", "📅");  // Calendar
        APP_ICONS.put("com.apple.reminders", "📝");  // Reminders
        APP_ICONS.put("com.apple.mobilenotes", "📓");  // Notes
        
        // Entertainment
        APP_ICONS.put("com.apple.Music", "🎵");  // Music
        APP_ICONS.put("com.netflix.Netflix", "🎬");  // Netflix
        APP_ICONS.put("com.google.ios.youtube", "📺");  // YouTube
        APP_ICONS.put("com.spotify.client", "🎶");  // Spotify
        
        // Others
        APP_ICONS.put("com.apple.news", "📰");  // News
        APP_ICONS.put("com.apple.weather", "☀️");  // Weather
        APP_ICONS.put("com.apple.Maps", "🗺️");  // Maps
        APP_ICONS.put("com.apple.Health", "❤️");  // Health
        APP_ICONS.put("com.apple.Fitness", "💪");  // Fitness
    }
    
    public static String getAppIcon(String bundleId) {
        return APP_ICONS.getOrDefault(bundleId, "📱");
    }
    
    public static String getAppDisplayName(String bundleId) {
        if (bundleId == null) return "未知应用";
        
        // Remove common prefixes
        String displayName = bundleId;
        if (displayName.startsWith("com.apple.")) {
            displayName = displayName.substring(10);
        } else if (displayName.contains(".")) {
            String[] parts = displayName.split("\\.");
            displayName = parts[parts.length - 1];
        }
        
        // Capitalize first letter
        if (displayName.length() > 0) {
            displayName = displayName.substring(0, 1).toUpperCase() + 
                         displayName.substring(1);
        }
        
        // Special cases
        switch (bundleId) {
            case "com.tencent.xin": return "微信";
            case "com.tencent.mqq": return "QQ";
            case "com.sina.weibo": return "微博";
            case "com.apple.MobileSMS": return "信息";
            case "com.apple.mobilephone": return "电话";
            case "com.apple.mobilemail": return "邮件";
            case "com.apple.mobilecal": return "日历";
            case "com.apple.reminders": return "提醒事项";
            case "com.apple.mobilenotes": return "备忘录";
            case "com.apple.Maps": return "地图";
            case "com.apple.Health": return "健康";
            case "com.apple.news": return "新闻";
            default: return displayName;
        }
    }
    
    public static String getErrorMessage(byte errorCode) {
        switch (errorCode) {
            case ERROR_UNKNOWN_COMMAND:
                return "未知命令";
            case ERROR_INVALID_COMMAND:
                return "无效命令";
            case ERROR_INVALID_PARAMETER:
                return "无效参数";
            case ERROR_ACTION_FAILED:
                return "操作失败";
            default:
                return "未知错误: " + String.format("0x%02X", errorCode);
        }
    }
}