package stu.xiaohei.iphonebridge;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

/**
 * 处理通知栏中的按钮点击事件
 */
public class NotificationActionReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationActionReceiver";

    public static final String ACTION_POSITIVE = "stu.xiaohei.iphonebridge.ACTION_POSITIVE";
    public static final String ACTION_NEGATIVE = "stu.xiaohei.iphonebridge.ACTION_NEGATIVE";
    public static final String EXTRA_NOTIFICATION_UID = "notification_uid";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        String uid = intent.getStringExtra(EXTRA_NOTIFICATION_UID);

        if (uid == null) {
            Log.e(TAG, "Notification UID is null");
            return;
        }

        Log.d(TAG, "Received action: " + action + " for UID: " + uid);

        boolean isPositive;
        if (ACTION_POSITIVE.equals(action)) {
            isPositive = true;
        } else if (ACTION_NEGATIVE.equals(action)) {
            isPositive = false;
        } else {
            Log.e(TAG, "Unknown action: " + action);
            return;
        }

        // 立即取消通知，提供即时反馈
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(uid.hashCode());
        Log.d(TAG, "Cancelled notification for UID: " + uid);

        // 绑定服务并执行操作
        Intent serviceIntent = new Intent(context, BridgeService.class);
        context.bindService(serviceIntent, new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                BridgeService.LocalBinder binder = (BridgeService.LocalBinder) service;
                BridgeService bridgeService = binder.getService();

                // 获取通知信息以显示操作名称
                NotificationHandler.NotificationInfo info = bridgeService.getNotificationInfo(uid);
                String actionLabel = isPositive ?
                    (info != null && info.positiveActionLabel != null ? info.positiveActionLabel : "确认") :
                    (info != null && info.negativeActionLabel != null ? info.negativeActionLabel : "取消");

                Toast.makeText(context,
                    "正在执行: " + actionLabel,
                    Toast.LENGTH_SHORT).show();

                // 执行操作
                bridgeService.performNotificationAction(uid, isPositive);

                // 解绑服务
                context.unbindService(this);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
            }
        }, Context.BIND_AUTO_CREATE);
    }
}
