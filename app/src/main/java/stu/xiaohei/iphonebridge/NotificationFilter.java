package stu.xiaohei.iphonebridge;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 通知过滤管理类
 * 支持关键字黑白名单和正则表达式过滤
 */
public class NotificationFilter {
    private static final String TAG = "NotificationFilter";
    private static final String PREFS_NAME = "FilterPrefs";
    private static final String PREF_FILTER_ENABLED = "filter_enabled";
    private static final String PREF_FILTER_MODE = "filter_mode";
    private static final String PREF_WHITELIST = "whitelist";
    private static final String PREF_BLACKLIST = "blacklist";
    private static final String PREF_REGEX_ENABLED = "regex_enabled";
    private static final String PREF_REGEX_PATTERN = "regex_pattern";

    // 过滤模式
    public static final int MODE_WHITELIST = 0;  // 白名单模式：只显示匹配的
    public static final int MODE_BLACKLIST = 1;  // 黑名单模式：隐藏匹配的

    private SharedPreferences mPrefs;
    private boolean mFilterEnabled;
    private int mFilterMode;
    private Set<String> mWhitelist;
    private Set<String> mBlacklist;
    private boolean mRegexEnabled;
    private String mRegexPattern;
    private Pattern mCompiledPattern;

    public NotificationFilter(Context context) {
        mPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }

    /**
     * 从SharedPreferences加载设置
     */
    private void loadSettings() {
        mFilterEnabled = mPrefs.getBoolean(PREF_FILTER_ENABLED, false);
        mFilterMode = mPrefs.getInt(PREF_FILTER_MODE, MODE_BLACKLIST);
        mWhitelist = mPrefs.getStringSet(PREF_WHITELIST, new HashSet<>());
        mBlacklist = mPrefs.getStringSet(PREF_BLACKLIST, new HashSet<>());
        mRegexEnabled = mPrefs.getBoolean(PREF_REGEX_ENABLED, false);
        mRegexPattern = mPrefs.getString(PREF_REGEX_PATTERN, "");

        // 编译正则表达式
        if (mRegexEnabled && !mRegexPattern.isEmpty()) {
            try {
                mCompiledPattern = Pattern.compile(mRegexPattern);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "Invalid regex pattern: " + mRegexPattern, e);
                mCompiledPattern = null;
            }
        }
    }

    /**
     * 保存设置到SharedPreferences
     */
    public void saveSettings() {
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putBoolean(PREF_FILTER_ENABLED, mFilterEnabled);
        editor.putInt(PREF_FILTER_MODE, mFilterMode);
        editor.putStringSet(PREF_WHITELIST, mWhitelist);
        editor.putStringSet(PREF_BLACKLIST, mBlacklist);
        editor.putBoolean(PREF_REGEX_ENABLED, mRegexEnabled);
        editor.putString(PREF_REGEX_PATTERN, mRegexPattern);
        editor.apply();

        // 重新编译正则表达式
        if (mRegexEnabled && !mRegexPattern.isEmpty()) {
            try {
                mCompiledPattern = Pattern.compile(mRegexPattern);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "Invalid regex pattern: " + mRegexPattern, e);
                mCompiledPattern = null;
            }
        } else {
            mCompiledPattern = null;
        }
    }

    /**
     * 检查通知是否应该被显示
     * @param title 通知标题
     * @param message 通知内容
     * @return true表示应该显示，false表示应该过滤
     */
    public boolean shouldShowNotification(String title, String message) {
        // 如果过滤未启用，显示所有通知
        if (!mFilterEnabled) {
            return true;
        }

        // 合并标题和内容用于匹配
        String fullContent = (title != null ? title : "") + " " + (message != null ? message : "");

        // 首先检查正则表达式过滤
        if (mRegexEnabled && mCompiledPattern != null) {
            boolean regexMatches = mCompiledPattern.matcher(fullContent).find();
            Log.d(TAG, "Regex match result: " + regexMatches + " for content: " + fullContent);

            // 正则表达式在黑名单模式下：匹配则过滤
            // 正则表达式在白名单模式下：匹配则显示
            if (mFilterMode == MODE_BLACKLIST) {
                if (regexMatches) {
                    Log.d(TAG, "Filtered by regex blacklist");
                    return false;
                }
            } else {
                if (!regexMatches) {
                    Log.d(TAG, "Filtered by regex whitelist");
                    return false;
                }
                // 正则匹配通过，继续检查关键字
            }
        }

        // 然后检查关键字过滤
        if (mFilterMode == MODE_WHITELIST) {
            // 白名单模式：必须包含白名单中的任意关键字
            if (mWhitelist.isEmpty()) {
                return true; // 白名单为空，显示所有
            }
            for (String keyword : mWhitelist) {
                if (fullContent.contains(keyword)) {
                    Log.d(TAG, "Matched whitelist keyword: " + keyword);
                    return true;
                }
            }
            Log.d(TAG, "Filtered by whitelist");
            return false;
        } else {
            // 黑名单模式：不能包含黑名单中的任何关键字
            for (String keyword : mBlacklist) {
                if (fullContent.contains(keyword)) {
                    Log.d(TAG, "Matched blacklist keyword: " + keyword);
                    return false;
                }
            }
            return true;
        }
    }

    // Getters and Setters
    public boolean isFilterEnabled() {
        return mFilterEnabled;
    }

    public void setFilterEnabled(boolean enabled) {
        mFilterEnabled = enabled;
    }

    public int getFilterMode() {
        return mFilterMode;
    }

    public void setFilterMode(int mode) {
        mFilterMode = mode;
    }

    public Set<String> getWhitelist() {
        return new HashSet<>(mWhitelist);
    }

    public void setWhitelist(Set<String> whitelist) {
        mWhitelist = new HashSet<>(whitelist);
    }

    public Set<String> getBlacklist() {
        return new HashSet<>(mBlacklist);
    }

    public void setBlacklist(Set<String> blacklist) {
        mBlacklist = new HashSet<>(blacklist);
    }

    public boolean isRegexEnabled() {
        return mRegexEnabled;
    }

    public void setRegexEnabled(boolean enabled) {
        mRegexEnabled = enabled;
    }

    public String getRegexPattern() {
        return mRegexPattern;
    }

    public void setRegexPattern(String pattern) {
        mRegexPattern = pattern;
    }

    /**
     * 添加关键字到白名单
     */
    public void addToWhitelist(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            mWhitelist.add(keyword.trim());
        }
    }

    /**
     * 从白名单移除关键字
     */
    public void removeFromWhitelist(String keyword) {
        mWhitelist.remove(keyword);
    }

    /**
     * 添加关键字到黑名单
     */
    public void addToBlacklist(String keyword) {
        if (keyword != null && !keyword.trim().isEmpty()) {
            mBlacklist.add(keyword.trim());
        }
    }

    /**
     * 从黑名单移除关键字
     */
    public void removeFromBlacklist(String keyword) {
        mBlacklist.remove(keyword);
    }

    /**
     * 验证正则表达式是否有效
     */
    public static boolean isValidRegex(String regex) {
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
