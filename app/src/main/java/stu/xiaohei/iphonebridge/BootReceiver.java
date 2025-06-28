package stu.xiaohei.iphonebridge;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final String PREFS_NAME = "iPhoneBridgePrefs";
    private static final String PREF_LAST_DEVICE = "lastConnectedDevice";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
            Intent.ACTION_PACKAGE_REPLACED.equals(action)) {
            
            // 检查是否有保存的设备信息
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String lastDevice = prefs.getString(PREF_LAST_DEVICE, null);
            
            if (lastDevice != null) {
                Log.d(TAG, "Starting BridgeService on boot for device: " + lastDevice);
                
                // 启动前台服务
                Intent serviceIntent = new Intent(context, BridgeService.class);
                try {
                    context.startForegroundService(serviceIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to start service on boot", e);
                }
            } else {
                Log.d(TAG, "No saved device found, not starting service");
            }
        }
    }
}