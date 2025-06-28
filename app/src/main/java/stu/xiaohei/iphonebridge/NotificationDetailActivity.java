package stu.xiaohei.iphonebridge;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class NotificationDetailActivity extends AppCompatActivity {
    private static final String EXTRA_NOTIFICATION_UID = "notification_uid";
    
    private TextView titleText;
    private TextView messageText;
    private TextView appText;
    private TextView dateText;
    private TextView categoryText;
    private Button positiveButton;
    private Button negativeButton;
    
    private BridgeService bridgeService;
    private boolean serviceBound = false;
    private String notificationUid;
    private NotificationHandler.NotificationInfo notificationInfo;
    
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BridgeService.LocalBinder binder = (BridgeService.LocalBinder) service;
            bridgeService = binder.getService();
            serviceBound = true;
            loadNotificationDetails();
        }
        
        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
        }
    };
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_detail);
        
        initViews();
        
        notificationUid = getIntent().getStringExtra(EXTRA_NOTIFICATION_UID);
        if (notificationUid == null) {
            finish();
            return;
        }
        
        setTitle("通知详情");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, BridgeService.class);
        bindService(intent, serviceConnection, BIND_AUTO_CREATE);
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        if (serviceBound) {
            unbindService(serviceConnection);
            serviceBound = false;
        }
    }
    
    private void initViews() {
        titleText = findViewById(R.id.notificationTitle);
        messageText = findViewById(R.id.notificationMessage);
        appText = findViewById(R.id.notificationApp);
        dateText = findViewById(R.id.notificationDate);
        categoryText = findViewById(R.id.notificationCategory);
        positiveButton = findViewById(R.id.positiveActionButton);
        negativeButton = findViewById(R.id.negativeActionButton);
        
        positiveButton.setOnClickListener(v -> performAction(true));
        negativeButton.setOnClickListener(v -> performAction(false));
    }
    
    private void loadNotificationDetails() {
        if (!serviceBound || bridgeService == null) {
            return;
        }
        
        // 从服务中获取通知详情
        notificationInfo = bridgeService.getNotificationInfo(notificationUid);
        
        if (notificationInfo == null) {
            // 如果没有找到，创建一个示例
            notificationInfo = new NotificationHandler.NotificationInfo(notificationUid);
            notificationInfo.title = "示例通知";
            notificationInfo.message = "这是一个示例通知内容";
            notificationInfo.categoryId = NotificationHandler.CATEGORY_ID_SOCIAL;
            notificationInfo.hasNegativeAction = true;
        }
        
        updateUI();
    }
    
    private void updateUI() {
        if (notificationInfo == null) {
            return;
        }
        
        String appName = notificationInfo.appId != null ? notificationInfo.appId : "iPhone 应用";
        String displayTitle = "";
        String displayMessage = "";
        
        // 对于所有类型的通知，将消息内容的第一行作为标题，第二行作为消息内容
        String fullContent = "";
        if (notificationInfo.title != null && !notificationInfo.title.isEmpty()) {
            fullContent += notificationInfo.title;
        }
        if (notificationInfo.subtitle != null && !notificationInfo.subtitle.isEmpty()) {
            if (!fullContent.isEmpty()) fullContent += "\n";
            fullContent += notificationInfo.subtitle;
        }
        if (notificationInfo.message != null && !notificationInfo.message.isEmpty()) {
            if (!fullContent.isEmpty()) fullContent += "\n";
            fullContent += notificationInfo.message;
        }
        
        if (!fullContent.isEmpty()) {
             String[] lines = fullContent.split("\n", 2);
             displayTitle = lines[0];
             displayMessage = lines.length > 1 ? lines[1] : "";
         } else {
             // 空通知已经在推送和列表添加时被过滤，这里不应该出现
             displayTitle = "未知通知";
             displayMessage = "无详细信息";
         }
        
        titleText.setText(displayTitle);
        messageText.setText(displayMessage);
        
        appText.setText("应用: " + appName);
        
        // 处理时间显示，如果为未知则使用当前系统时间
        String timeText;
        if (notificationInfo.date != null && !notificationInfo.date.isEmpty() && !"未知".equals(notificationInfo.date)) {
            timeText = notificationInfo.date;
        } else {
            timeText = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    .format(new java.util.Date());
        }
        dateText.setText("时间: " + timeText);
        
        categoryText.setText("类别: " + NotificationHandler.getCategoryName(notificationInfo.categoryId));
        
        // 根据可用操作显示按钮
        if (notificationInfo.hasPositiveAction) {
            positiveButton.setVisibility(View.VISIBLE);
            positiveButton.setText(notificationInfo.positiveActionLabel != null ? 
                notificationInfo.positiveActionLabel : "确认");
        } else {
            positiveButton.setVisibility(View.GONE);
        }
        
        if (notificationInfo.hasNegativeAction) {
            negativeButton.setVisibility(View.VISIBLE);
            negativeButton.setText(notificationInfo.negativeActionLabel != null ? 
                notificationInfo.negativeActionLabel : "取消");
        } else {
            negativeButton.setVisibility(View.GONE);
        }
    }
    
    private void performAction(boolean positive) {
        if (!serviceBound || bridgeService == null || notificationInfo == null) {
            return;
        }
        
        // 执行操作
        String actionType = positive ? "积极" : "消极";
        String actionLabel = positive ? 
            (notificationInfo.positiveActionLabel != null ? notificationInfo.positiveActionLabel : "确认") :
            (notificationInfo.negativeActionLabel != null ? notificationInfo.negativeActionLabel : "取消");
            
        Toast.makeText(this, 
            "正在执行操作: " + actionLabel, 
            Toast.LENGTH_SHORT).show();
        
        // 通过服务发送命令到iPhone
        bridgeService.performNotificationAction(notificationUid, positive);
        
        // 延迟关闭，让用户看到提示
        new android.os.Handler().postDelayed(this::finish, 1000);
    }
    
    public static Intent createIntent(AppCompatActivity activity, String notificationUid) {
        Intent intent = new Intent(activity, NotificationDetailActivity.class);
        intent.putExtra(EXTRA_NOTIFICATION_UID, notificationUid);
        return intent;
    }
}