package stu.xiaohei.iphonebridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.UUID;

public class BridgeService extends Service {
    private static final String TAG = "BridgeService";
    private static final String CHANNEL_ID = "iphone_bridge_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String PREFS_NAME = "iPhoneBridgePrefs";
    private static final String PREF_LAST_DEVICE = "lastConnectedDevice";
    private static final long RECONNECT_INTERVAL = 3 * 60 * 1000; // 3分钟
    
    // ANCS UUIDs
    private static final String SERVICE_ANCS = "7905F431-B5CE-4E99-A40F-4B1E122D00D0";
    private static final String CHAR_NOTIFICATION_SOURCE = "9FBF120D-6301-42D9-8C58-25E699A21DBD";
    private static final String CHAR_CONTROL_POINT = "69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9";
    private static final String CHAR_DATA_SOURCE = "22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB";
    private static final String DESCRIPTOR_CONFIG = "00002902-0000-1000-8000-00805f9b34fb";
    
    private final IBinder binder = new LocalBinder();
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothGatt bluetoothGatt;
    private BluetoothDevice connectedDevice;
    
    private BluetoothGattService ancsService;
    private BluetoothGattCharacteristic notificationSourceChar;
    private BluetoothGattCharacteristic controlPointChar;
    private BluetoothGattCharacteristic dataSourceChar;
    
    private NotificationHandler notificationHandler;
    private ServiceCallback serviceCallback;
    
    // 自动重连相关
    private Handler reconnectHandler = new Handler();
    private Runnable reconnectRunnable;
    private boolean shouldReconnect = false;
    private SharedPreferences sharedPreferences;
    
    // 电源管理
    private PowerManager.WakeLock wakeLock;
    
    public interface ServiceCallback {
        void onConnectionStateChanged(boolean connected);
        void onNotificationReceived(NotificationHandler.NotificationInfo info);
        void onServiceReady();
    }
    
    public class LocalBinder extends Binder {
        public BridgeService getService() {
            return BridgeService.this;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service created");
        
        try {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                Log.e(TAG, "BluetoothManager is null");
                stopSelf();
                return;
            }
            
            bluetoothAdapter = bluetoothManager.getAdapter();
            if (bluetoothAdapter == null) {
                Log.e(TAG, "BluetoothAdapter is null");
                stopSelf();
                return;
            }
            
            notificationHandler = new NotificationHandler();
            sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            
            // 初始化电源管理
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "iPhoneBridge::ServiceWakeLock");
            wakeLock.acquire();
            
            // 初始化自动重连
            initAutoReconnect();
            
            createNotificationChannel();
            startForeground(NOTIFICATION_ID, createNotification());
            
            Log.d(TAG, "Service initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing service", e);
            stopSelf();
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service started");
        
        // 启动自动重连
        if (shouldReconnect) {
            reconnectHandler.removeCallbacks(reconnectRunnable);
            reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL);
        } else {
            // 尝试连接上次连接的设备
            startAutoReconnect();
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        
        // 释放资源
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        
        reconnectHandler.removeCallbacks(reconnectRunnable);
        disconnect();
        
        super.onDestroy();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
    
    public void setServiceCallback(ServiceCallback callback) {
        this.serviceCallback = callback;
    }
    
    private void initAutoReconnect() {
        reconnectRunnable = new Runnable() {
            @Override
            public void run() {
                if (shouldReconnect && connectedDevice != null && !isConnected()) {
                    Log.d(TAG, "Attempting to reconnect to device: " + connectedDevice.getAddress());
                    connectToDevice(connectedDevice);
                }
                
                if (shouldReconnect) {
                    reconnectHandler.postDelayed(this, RECONNECT_INTERVAL);
                }
            }
        };
    }
    
    public void connectToDevice(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Device is null");
            return;
        }
        
        connectedDevice = device;
        shouldReconnect = true;
        
        // 保存设备地址以便重连
        sharedPreferences.edit().putString(PREF_LAST_DEVICE, device.getAddress()).apply();
        
        if (bluetoothGatt != null) {
            bluetoothGatt.close();
        }
        
        bluetoothGatt = device.connectGatt(this, false, gattCallback);
    }
    
    public void disconnect() {
        shouldReconnect = false;
        reconnectHandler.removeCallbacks(reconnectRunnable);
        
        if (bluetoothGatt != null) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }
    }
    
    public void startAutoReconnect() {
        String lastDeviceAddress = sharedPreferences.getString(PREF_LAST_DEVICE, null);
        if (lastDeviceAddress != null && bluetoothAdapter != null) {
            try {
                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress);
                connectToDevice(device);
            } catch (Exception e) {
                Log.e(TAG, "Failed to get last connected device", e);
            }
        }
    }
    
    public boolean isConnected() {
        return bluetoothGatt != null && connectedDevice != null;
    }
    
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server");
                gatt.discoverServices();
                
                if (serviceCallback != null) {
                    serviceCallback.onConnectionStateChanged(true);
                }
                
                updateNotification("已连接到 iPhone");
                
                // 连接成功后停止重连定时器
                reconnectHandler.removeCallbacks(reconnectRunnable);
                
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server");
                
                if (serviceCallback != null) {
                    serviceCallback.onConnectionStateChanged(false);
                }
                
                updateNotification("连接已断开，将在3分钟后重连");
                
                // 清理GATT连接
                if (bluetoothGatt != null) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                }
                
                // 启动自动重连
                if (shouldReconnect && connectedDevice != null) {
                    reconnectHandler.removeCallbacks(reconnectRunnable);
                    reconnectHandler.postDelayed(reconnectRunnable, RECONNECT_INTERVAL);
                }
            }
        }
        
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                ancsService = gatt.getService(UUID.fromString(SERVICE_ANCS));
                
                if (ancsService != null) {
                    Log.i(TAG, "ANCS service found");
                    
                    notificationSourceChar = ancsService.getCharacteristic(UUID.fromString(CHAR_NOTIFICATION_SOURCE));
                    controlPointChar = ancsService.getCharacteristic(UUID.fromString(CHAR_CONTROL_POINT));
                    dataSourceChar = ancsService.getCharacteristic(UUID.fromString(CHAR_DATA_SOURCE));
                    
                    // 按照ANCS规范，先启用 Data Source 通知
                    setNotificationEnabled(dataSourceChar);
                    
                    updateNotification("ANCS 服务已就绪");
                } else {
                    Log.e(TAG, "ANCS service not found");
                    updateNotification("未找到 ANCS 服务");
                }
            }
        }
        
        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.getCharacteristic().getUuid().equals(UUID.fromString(CHAR_DATA_SOURCE))) {
                    // Data Source 启用后，启用 Notification Source
                    setNotificationEnabled(notificationSourceChar);
                } else if (descriptor.getCharacteristic().getUuid().equals(UUID.fromString(CHAR_NOTIFICATION_SOURCE))) {
                    Log.i(TAG, "ANCS notifications enabled");
                    updateNotification("正在接收 iPhone 通知");
                    
                    if (serviceCallback != null) {
                        serviceCallback.onServiceReady();
                    }
                }
            }
        }
        
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if (characteristic.getUuid().equals(UUID.fromString(CHAR_NOTIFICATION_SOURCE))) {
                handleNotificationSource(characteristic.getValue());
            } else if (characteristic.getUuid().equals(UUID.fromString(CHAR_DATA_SOURCE))) {
                handleDataSource(characteristic.getValue());
            }
        }
        
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful");
            } else {
                Log.e(TAG, "Characteristic write failed with status: " + status);
            }
        }
    };
    
    private void setNotificationEnabled(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null || characteristic == null) {
            return;
        }
        
        bluetoothGatt.setCharacteristicNotification(characteristic, true);
        
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(UUID.fromString(DESCRIPTOR_CONFIG));
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            bluetoothGatt.writeDescriptor(descriptor);
        }
    }
    
    private void handleNotificationSource(byte[] data) {
        if (data == null || data.length < 8) {
            Log.e(TAG, "Invalid notification source data");
            return;
        }
        
        // 解析通知基本信息
        byte eventId = data[0];
        byte eventFlags = data[1];
        byte categoryId = data[2];
        byte categoryCount = data[3];
        byte[] uid = {data[4], data[5], data[6], data[7]};
        
        Log.d(TAG, String.format("EventId:%d\nEventFlags:%02x\nCategory id:%d\nCategory Count:%d\nNotificationUId:%02X%02X%02X%02X",
            eventId & 0xFF, eventFlags & 0xFF, categoryId & 0xFF, categoryCount & 0xFF,
            uid[0] & 0xFF, uid[1] & 0xFF, uid[2] & 0xFF, uid[3] & 0xFF));
        
        NotificationHandler.NotificationInfo info = notificationHandler.parseNotificationSource(data);
        
        if (info != null) {
            // 只对新增和修改的通知获取详细信息
            if (info.eventId == NotificationHandler.EVENT_ID_NOTIFICATION_ADDED || 
                info.eventId == NotificationHandler.EVENT_ID_NOTIFICATION_MODIFIED) {
                
                // 使用示例代码中的命令格式获取更多通知信息
                getMoreAboutNotification(data);
                // 注意：不在这里显示通知，等待Data Source解析完成后再显示
            } else if (info.eventId == NotificationHandler.EVENT_ID_NOTIFICATION_REMOVED) {
                // 对于移除的通知，取消对应的Android通知
                cancelLocalNotification(info);
                
                // 通知UI更新
                if (serviceCallback != null) {
                    serviceCallback.onNotificationReceived(info);
                }
            }
        }
    }
    
    private void getMoreAboutNotification(byte[] nsData) {
        byte[] getNotificationAttribute = {
            (byte) 0x00,  // Command ID: Get Notification Attributes
            // UID
            nsData[4], nsData[5], nsData[6], nsData[7],
            // app id
            (byte) 0x00,
            // title
            (byte) 0x01, (byte) 0xff, (byte) 0xff,
            // message
            (byte) 0x03, (byte) 0xff, (byte) 0xff
        };
        
        if (bluetoothGatt != null && controlPointChar != null) {
            controlPointChar.setValue(getNotificationAttribute);
            boolean success = bluetoothGatt.writeCharacteristic(controlPointChar);
            Log.d(TAG, "Write get notification attributes command result: " + success);
        }
    }
    
    private void handleDataSource(byte[] data) {
        notificationHandler.parseDataSource(data);
        
        // 获取更新后的通知信息
        if (data.length >= 5) {
            String uid = String.format("%02X%02X%02X%02X", 
                data[1] & 0xFF, data[2] & 0xFF, data[3] & 0xFF, data[4] & 0xFF);
            
            NotificationHandler.NotificationInfo info = notificationHandler.getNotification(uid);
            if (info != null) {
                // 通知UI更新
                if (serviceCallback != null) {
                    serviceCallback.onNotificationReceived(info);
                }
                
                // 在Data Source解析完成后显示本地通知
                if (info.eventId == NotificationHandler.EVENT_ID_NOTIFICATION_ADDED || 
                    info.eventId == NotificationHandler.EVENT_ID_NOTIFICATION_MODIFIED) {
                    showLocalNotification(info);
                }
            }
        }
    }
    
    private void showLocalNotification(NotificationHandler.NotificationInfo info) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        
        String channelId = "iphone_notifications";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                channelId,
                "iPhone 通知",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            notificationManager.createNotificationChannel(channel);
        }
        
        String appName = ANCSConstants.getAppDisplayName(info.appId);
        String notificationTitle = "";
        String notificationContent = "";
        
        // 检查是否为应用程序类型的通知（除了系统通知类型外的其他类型）
        boolean isAppNotification = info.categoryId != NotificationHandler.CATEGORY_ID_INCOMING_CALL &&
                                   info.categoryId != NotificationHandler.CATEGORY_ID_MISSED_CALL &&
                                   info.categoryId != NotificationHandler.CATEGORY_ID_VOICEMAIL;
        
        if (isAppNotification) {
            // 对于应用程序类型，将消息内容的第一行作为标题，第二行作为消息内容
            String fullContent = "";
            if (info.title != null && !info.title.isEmpty()) {
                fullContent += info.title;
            }
            if (info.subtitle != null && !info.subtitle.isEmpty()) {
                if (!fullContent.isEmpty()) fullContent += "\n";
                fullContent += info.subtitle;
            }
            if (info.message != null && !info.message.isEmpty()) {
                if (!fullContent.isEmpty()) fullContent += "\n";
                fullContent += info.message;
            }
            
            if (!fullContent.isEmpty()) {
                String[] lines = fullContent.split("\n", 2);
                notificationTitle = lines[0];
                notificationContent = lines.length > 1 ? lines[1] : "";
            } else {
                // 如果没有有效内容，不推送通知
                return;
            }
        } else {
            // 对于系统通知类型，使用应用名称作为标题
            notificationTitle = appName;
            
            // 构建完整的通知内容
            String fullMessage = "";
            if (info.title != null && !info.title.isEmpty()) {
                fullMessage += info.title;
            }
            if (info.subtitle != null && !info.subtitle.isEmpty()) {
                if (!fullMessage.isEmpty()) fullMessage += "\n";
                fullMessage += info.subtitle;
            }
            if (info.message != null && !info.message.isEmpty()) {
                if (!fullMessage.isEmpty()) fullMessage += "\n";
                fullMessage += info.message;
            }
            
            if (fullMessage.isEmpty()) {
                // 如果没有有效内容，不推送通知
                return;
            }
            notificationContent = fullMessage;
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notificationTitle)
            .setContentText(notificationContent)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(notificationContent))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true);
        
        notificationManager.notify(info.uid.hashCode(), builder.build());
    }
    
    private void cancelLocalNotification(NotificationHandler.NotificationInfo info) {
        if (info == null) {
            return;
        }
        
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        // 使用与显示通知时相同的ID来取消通知
        notificationManager.cancel(info.uid.hashCode());
        
        Log.d(TAG, "Cancelled local notification for UID: " + info.uid);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "iPhone 桥接服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("保持与 iPhone 的连接");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }
    
    private Notification createNotification() {
        return updateNotification("服务运行中");
    }
    
    private Notification updateNotification(String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE
        );
        
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("iPhone 桥接")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .build();
        
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
        
        return notification;
    }
    
    public void performNotificationAction(String uid, boolean positive) {
        if (notificationHandler == null || controlPointChar == null || bluetoothGatt == null) {
            Log.e(TAG, "Cannot perform action - service not ready");
            return;
        }
        
        byte[] command = notificationHandler.createPerformActionCommand(uid, positive);
        controlPointChar.setValue(command);
        bluetoothGatt.writeCharacteristic(controlPointChar);
        
        Log.d(TAG, "Performed " + (positive ? "positive" : "negative") + " action for notification " + uid);
    }
    
    public NotificationHandler.NotificationInfo getNotificationInfo(String uid) {
        if (notificationHandler != null) {
            return notificationHandler.getNotification(uid);
        }
        return null;
    }
}