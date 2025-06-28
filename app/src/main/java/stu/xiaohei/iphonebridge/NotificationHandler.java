package stu.xiaohei.iphonebridge;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class NotificationHandler {
    private static final String TAG = "NotificationHandler";
    
    // ANCS Event IDs
    public static final byte EVENT_ID_NOTIFICATION_ADDED = 0;
    public static final byte EVENT_ID_NOTIFICATION_MODIFIED = 1;
    public static final byte EVENT_ID_NOTIFICATION_REMOVED = 2;
    
    // ANCS Category IDs
    public static final byte CATEGORY_ID_OTHER = 0;
    public static final byte CATEGORY_ID_INCOMING_CALL = 1;
    public static final byte CATEGORY_ID_MISSED_CALL = 2;
    public static final byte CATEGORY_ID_VOICEMAIL = 3;
    public static final byte CATEGORY_ID_SOCIAL = 4;
    public static final byte CATEGORY_ID_SCHEDULE = 5;
    public static final byte CATEGORY_ID_EMAIL = 6;
    public static final byte CATEGORY_ID_NEWS = 7;
    public static final byte CATEGORY_ID_HEALTH_AND_FITNESS = 8;
    public static final byte CATEGORY_ID_BUSINESS_AND_FINANCE = 9;
    public static final byte CATEGORY_ID_LOCATION = 10;
    public static final byte CATEGORY_ID_ENTERTAINMENT = 11;
    
    // ANCS Event Flags
    public static final byte EVENT_FLAG_SILENT = (byte) (1 << 0);
    public static final byte EVENT_FLAG_IMPORTANT = (byte) (1 << 1);
    public static final byte EVENT_FLAG_PRE_EXISTING = (byte) (1 << 2);
    public static final byte EVENT_FLAG_POSITIVE_ACTION = (byte) (1 << 3);
    public static final byte EVENT_FLAG_NEGATIVE_ACTION = (byte) (1 << 4);
    
    // ANCS Command IDs
    public static final byte COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES = 0;
    public static final byte COMMAND_ID_GET_APP_ATTRIBUTES = 1;
    public static final byte COMMAND_ID_PERFORM_NOTIFICATION_ACTION = 2;
    
    // ANCS Notification Attribute IDs
    public static final byte ATTRIBUTE_ID_APP_IDENTIFIER = 0;
    public static final byte ATTRIBUTE_ID_TITLE = 1;
    public static final byte ATTRIBUTE_ID_SUBTITLE = 2;
    public static final byte ATTRIBUTE_ID_MESSAGE = 3;
    public static final byte ATTRIBUTE_ID_MESSAGE_SIZE = 4;
    public static final byte ATTRIBUTE_ID_DATE = 5;
    public static final byte ATTRIBUTE_ID_POSITIVE_ACTION_LABEL = 6;
    public static final byte ATTRIBUTE_ID_NEGATIVE_ACTION_LABEL = 7;
    
    // ANCS Action IDs
    public static final byte ACTION_ID_POSITIVE = 0;
    public static final byte ACTION_ID_NEGATIVE = 1;
    
    private Map<String, NotificationInfo> notifications = new HashMap<>();
    private Map<String, ByteBuffer> pendingDataBuffers = new HashMap<>();
    
    public static class NotificationInfo {
        public String uid;
        public byte eventId;
        public byte categoryId;
        public byte eventFlags;
        public String appId;
        public String title;
        public String subtitle;
        public String message;
        public String date;
        public String positiveActionLabel;
        public String negativeActionLabel;
        public boolean hasPositiveAction;
        public boolean hasNegativeAction;
        
        public NotificationInfo(String uid) {
            this.uid = uid;
        }
        
        public String getFormattedInfo() {
            StringBuilder sb = new StringBuilder();
            sb.append("UID: ").append(uid);
            sb.append(", Category: ").append(getCategoryName(categoryId));
            if (title != null) sb.append(", Title: ").append(title);
            if (message != null) sb.append(", Message: ").append(message);
            return sb.toString();
        }
    }
    
    public NotificationInfo parseNotificationSource(byte[] data) {
        if (data == null || data.length < 8) {
            Log.e(TAG, "Invalid notification source data");
            return null;
        }
        
        byte eventId = data[0];
        byte eventFlags = data[1];
        byte categoryId = data[2];
        byte categoryCount = data[3];
        
        // UID is 4 bytes, little endian
        String uid = String.format("%02X%02X%02X%02X", 
            data[4] & 0xFF, data[5] & 0xFF, data[6] & 0xFF, data[7] & 0xFF);
        
        NotificationInfo info = new NotificationInfo(uid);
        info.eventId = eventId;
        info.categoryId = categoryId;
        info.eventFlags = eventFlags;
        info.hasPositiveAction = (eventFlags & EVENT_FLAG_POSITIVE_ACTION) != 0;
        info.hasNegativeAction = (eventFlags & EVENT_FLAG_NEGATIVE_ACTION) != 0;
        
        notifications.put(uid, info);
        
        Log.d(TAG, "Parsed notification: " + info.getFormattedInfo());
        return info;
    }
    
    public void parseDataSource(byte[] data) {
        if (data == null || data.length < 5) {
            Log.e(TAG, "Invalid data source data");
            return;
        }
        
        byte commandId = data[0];
        String uid = String.format("%02X%02X%02X%02X", 
            data[1] & 0xFF, data[2] & 0xFF, data[3] & 0xFF, data[4] & 0xFF);
        
        Log.d(TAG, "Parsing data source for UID: " + uid + ", command: " + (commandId & 0xFF) + ", length: " + data.length);
        
        NotificationInfo info = notifications.get(uid);
        if (info == null) {
            Log.w(TAG, "Notification not found for UID: " + uid + ", creating new one");
            info = new NotificationInfo(uid);
            notifications.put(uid, info);
        }
        
        if (commandId == COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES) {
            // 直接解析属性数据，从第5个字节开始
            if (data.length > 5) {
                parseNotificationAttributes(data, 5, info);
                Log.d(TAG, "Updated notification info: " + info.getFormattedInfo());
            } else {
                Log.w(TAG, "No attribute data to parse");
            }
        }
    }
    
    private void parseNotificationAttributes(byte[] data, int offset, NotificationInfo info) {
        int pos = offset;
        
        Log.d(TAG, "Parsing notification attributes, data length: " + data.length + ", offset: " + offset);
        
        while (pos < data.length) {
            if (pos + 1 > data.length) {
                Log.d(TAG, "Not enough data for attribute ID at position: " + pos);
                break;
            }
            
            byte attributeId = data[pos++];
            
            // 检查是否有长度字段
            int length = 0;
            if (pos + 2 <= data.length) {
                length = ByteBuffer.wrap(data, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                pos += 2;
            } else {
                // 如果没有长度字段，读取到下一个属性ID或数据结束
                int nextAttrPos = pos;
                while (nextAttrPos < data.length && 
                       data[nextAttrPos] != ATTRIBUTE_ID_APP_IDENTIFIER &&
                       data[nextAttrPos] != ATTRIBUTE_ID_TITLE &&
                       data[nextAttrPos] != ATTRIBUTE_ID_SUBTITLE &&
                       data[nextAttrPos] != ATTRIBUTE_ID_MESSAGE &&
                       data[nextAttrPos] != ATTRIBUTE_ID_DATE) {
                    nextAttrPos++;
                }
                length = nextAttrPos - pos;
            }
            
            Log.d(TAG, "Attribute ID: " + (attributeId & 0xFF) + ", Length: " + length + ", Position: " + pos);
            
            if (pos + length > data.length) {
                length = data.length - pos;
                Log.w(TAG, "Adjusting length to available data: " + length);
            }
            
            if (length > 0) {
                // 使用UTF-8编码解析字符串
                String value = new String(data, pos, length, StandardCharsets.UTF_8).trim();
                Log.d(TAG, "Parsed attribute value: '" + value + "'");
                pos += length;
                
                switch (attributeId) {
                    case ATTRIBUTE_ID_APP_IDENTIFIER:
                        info.appId = value;
                        break;
                    case ATTRIBUTE_ID_TITLE:
                        info.title = value;
                        break;
                    case ATTRIBUTE_ID_SUBTITLE:
                        info.subtitle = value;
                        break;
                    case ATTRIBUTE_ID_MESSAGE:
                        info.message = value;
                        break;
                    case ATTRIBUTE_ID_DATE:
                        info.date = value;
                        break;
                    case ATTRIBUTE_ID_POSITIVE_ACTION_LABEL:
                        info.positiveActionLabel = value;
                        break;
                    case ATTRIBUTE_ID_NEGATIVE_ACTION_LABEL:
                        info.negativeActionLabel = value;
                        break;
                    default:
                        Log.w(TAG, "Unknown attribute ID: " + (attributeId & 0xFF));
                        break;
                }
            }
        }
        
        Log.d(TAG, "Final notification attributes: " + info.getFormattedInfo());
    }
    
    public byte[] createGetNotificationAttributesCommand(String uid, byte... attributeIds) {
        ByteBuffer buffer = ByteBuffer.allocate(1 + 4 + attributeIds.length * 3);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Command ID
        buffer.put(COMMAND_ID_GET_NOTIFICATION_ATTRIBUTES);
        
        // UID (4 bytes, little endian)
        String[] uidBytes = {uid.substring(0, 2), uid.substring(2, 4), uid.substring(4, 6), uid.substring(6, 8)};
        for (String b : uidBytes) {
            buffer.put((byte) Integer.parseInt(b, 16));
        }
        
        // Attribute IDs with max length
        for (byte attributeId : attributeIds) {
            buffer.put(attributeId);
            buffer.putShort((short) 256); // Max length
        }
        
        return buffer.array();
    }
    
    public byte[] createPerformActionCommand(String uid, boolean positive) {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Command ID
        buffer.put(COMMAND_ID_PERFORM_NOTIFICATION_ACTION);
        
        // UID (4 bytes, little endian)
        String[] uidBytes = {uid.substring(0, 2), uid.substring(2, 4), uid.substring(4, 6), uid.substring(6, 8)};
        for (String b : uidBytes) {
            buffer.put((byte) Integer.parseInt(b, 16));
        }
        
        // Action ID
        buffer.put(positive ? ACTION_ID_POSITIVE : ACTION_ID_NEGATIVE);
        
        return buffer.array();
    }
    
    public NotificationInfo getNotification(String uid) {
        return notifications.get(uid);
    }
    
    public void removeNotification(String uid) {
        notifications.remove(uid);
    }
    
    public static String getCategoryName(byte categoryId) {
        switch (categoryId) {
            case CATEGORY_ID_INCOMING_CALL: return "来电";
            case CATEGORY_ID_MISSED_CALL: return "未接来电";
            case CATEGORY_ID_VOICEMAIL: return "语音邮件";
            case CATEGORY_ID_SOCIAL: return "社交";
            case CATEGORY_ID_SCHEDULE: return "日程";
            case CATEGORY_ID_EMAIL: return "邮件";
            case CATEGORY_ID_NEWS: return "新闻";
            case CATEGORY_ID_HEALTH_AND_FITNESS: return "健康";
            case CATEGORY_ID_BUSINESS_AND_FINANCE: return "商务";
            case CATEGORY_ID_LOCATION: return "位置";
            case CATEGORY_ID_ENTERTAINMENT: return "娱乐";
            default: return "其他";
        }
    }
    
    public static String getEventName(byte eventId) {
        switch (eventId) {
            case EVENT_ID_NOTIFICATION_ADDED: return "新增";
            case EVENT_ID_NOTIFICATION_MODIFIED: return "修改";
            case EVENT_ID_NOTIFICATION_REMOVED: return "移除";
            default: return "未知";
        }
    }
}