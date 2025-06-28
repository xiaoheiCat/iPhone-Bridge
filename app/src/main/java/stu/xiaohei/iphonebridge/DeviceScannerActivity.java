package stu.xiaohei.iphonebridge;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceScannerActivity extends AppCompatActivity {
    public static final String EXTRA_DEVICE_ADDRESS = "device_address";
    
    private BluetoothLeScanner mBluetoothLeScanner;
    private DeviceListAdapter mDeviceListAdapter;
    private Handler mHandler = new Handler();
    private boolean mScanning = false;
    
    private Button mScanButton;
    private ProgressBar mProgressBar;
    private ListView mDeviceList;
    
    private static final long SCAN_PERIOD = 10000;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_scanner);
        
        setTitle("选择 iPhone 设备");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        
        mScanButton = findViewById(R.id.scanButton);
        mProgressBar = findViewById(R.id.progressBar);
        mDeviceList = findViewById(R.id.deviceList);
        
        mDeviceListAdapter = new DeviceListAdapter();
        mDeviceList.setAdapter(mDeviceListAdapter);
        
        mDeviceList.setOnItemClickListener((parent, view, position, id) -> {
            DeviceInfo device = mDeviceListAdapter.getDevice(position);
            if (device != null) {
                stopScan();
                
                Intent result = new Intent();
                result.putExtra(EXTRA_DEVICE_ADDRESS, device.device.getAddress());
                setResult(RESULT_OK, result);
                finish();
            }
        });
        
        mScanButton.setOnClickListener(v -> {
            if (mScanning) {
                stopScan();
            } else {
                startScan();
            }
        });
        
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            mBluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
        
        // 自动开始扫描
        startScan();
    }
    
    private void startScan() {
        if (mBluetoothLeScanner == null) {
            return;
        }
        
        mDeviceListAdapter.clear();
        
        mHandler.postDelayed(this::stopScan, SCAN_PERIOD);
        
        mScanning = true;
        mScanButton.setText("停止扫描");
        mProgressBar.setVisibility(View.VISIBLE);
        
        ScanSettings settings = new ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build();
            
        mBluetoothLeScanner.startScan(null, settings, mScanCallback);
    }
    
    private void stopScan() {
        if (mBluetoothLeScanner == null || !mScanning) {
            return;
        }
        
        mScanning = false;
        mScanButton.setText("开始扫描");
        mProgressBar.setVisibility(View.GONE);
        
        mBluetoothLeScanner.stopScan(mScanCallback);
    }
    
    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            runOnUiThread(() -> {
                mDeviceListAdapter.addDevice(result.getDevice(), result.getRssi());
            });
        }
        
        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult result : results) {
                runOnUiThread(() -> {
                    mDeviceListAdapter.addDevice(result.getDevice(), result.getRssi());
                });
            }
        }
    };
    
    @Override
    protected void onPause() {
        super.onPause();
        stopScan();
    }
    
    // 设备信息类
    static class DeviceInfo {
        BluetoothDevice device;
        int rssi;
        boolean isPaired;
        
        DeviceInfo(BluetoothDevice device, int rssi) {
            this.device = device;
            this.rssi = rssi;
            this.isPaired = device.getBondState() == BluetoothDevice.BOND_BONDED;
        }
    }
    
    // 设备列表适配器
    class DeviceListAdapter extends BaseAdapter {
        private List<DeviceInfo> mDevices = new ArrayList<>();
        private Map<String, DeviceInfo> mDeviceMap = new HashMap<>();
        private LayoutInflater mInflater = getLayoutInflater();
        
        public void addDevice(BluetoothDevice device, int rssi) {
            if (device.getName() == null) {
                return;
            }
            
            String address = device.getAddress();
            if (mDeviceMap.containsKey(address)) {
                // 更新RSSI
                mDeviceMap.get(address).rssi = rssi;
            } else {
                DeviceInfo info = new DeviceInfo(device, rssi);
                mDevices.add(info);
                mDeviceMap.put(address, info);
            }
            
            notifyDataSetChanged();
        }
        
        public DeviceInfo getDevice(int position) {
            if (position >= 0 && position < mDevices.size()) {
                return mDevices.get(position);
            }
            return null;
        }
        
        public void clear() {
            mDevices.clear();
            mDeviceMap.clear();
            notifyDataSetChanged();
        }
        
        @Override
        public int getCount() {
            return mDevices.size();
        }
        
        @Override
        public Object getItem(int position) {
            return mDevices.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.item_device, parent, false);
                holder = new ViewHolder();
                holder.name = convertView.findViewById(R.id.deviceName);
                holder.address = convertView.findViewById(R.id.deviceAddress);
                holder.rssi = convertView.findViewById(R.id.deviceRssi);
                holder.paired = convertView.findViewById(R.id.devicePaired);
                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }
            
            DeviceInfo info = mDevices.get(position);
            holder.name.setText(info.device.getName());
            holder.address.setText(info.device.getAddress());
            holder.rssi.setText(String.format("信号: %d dBm", info.rssi));
            holder.paired.setVisibility(info.isPaired ? View.VISIBLE : View.GONE);
            
            return convertView;
        }
        
        class ViewHolder {
            TextView name;
            TextView address;
            TextView rssi;
            TextView paired;
        }
    }
}