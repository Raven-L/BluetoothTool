package com.Raven_L.bluetoothtool;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static android.content.ContentValues.TAG;


public class BluetoothTool {
    private Boolean BLEMode;
    private Context mContext;
    private BluetoothAdapter mBtAdapter;
    private BTStateListener btStateListener;

    //状态
    private IntentFilter statusFilter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
    private BroadcastReceiver bluetoothStateReceiver;

    //寻找
    //classic BT
    private IntentFilter scanningFilter ;
    private BroadcastReceiver scanningBroadcastReceiver;
    private BTScanningListener btScanningListener;
    private List<BluetoothDevice> deviceList =new ArrayList<>();
    //BLE
    private BluetoothLeScanner BLEScanner;
    private ScanCallback BLEScanCallback;

    //连接
    private DeviceStateListener deviceStateListener;
    //classic BT
    public UUID CLassic_UUID_SerialPortServiceClass  = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    public UUID BLE_UUID_ClientCharacteristicConfiguration  = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private ConnectedThread connectedThread;//数据收发进程
    private int maxTransimitSize,maxReceiveSize;
    private  FutureTask<byte[]> readTask;
    private boolean isConnect=false;
    private int lastAvailable=0;

    //BLE
    private BluetoothGatt BLEGatt;
    private BluetoothGattCallback BLEGattCallback;
    private BluetoothGattService BLEService;
    private BluetoothGattCharacteristic BLECharacter;
    private boolean isCharacteristicChanged=true;

    public BluetoothTool(Context context, Boolean isBLEMode){
        this.mContext=context;
        BLEMode=isBLEMode;
        mBtAdapter= BluetoothAdapter.getDefaultAdapter();
        scanningFilter= new IntentFilter(BluetoothDevice.ACTION_FOUND);
        scanningFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        scanningFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);




    }

    public void setBTStateListener(BTStateListener listener){
        bluetoothStateReceiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                //确定状态广播
                if (!Objects.equals(intent.getAction(), BluetoothAdapter.ACTION_STATE_CHANGED)) {
                    return;
                }
                //判断状态
                switch (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, 0)) {
                    case BluetoothAdapter.STATE_OFF:
                        btStateListener.onBTOff();
                        break;
                    case BluetoothAdapter.STATE_ON:
                        btStateListener.onBTOn();
                        break;
                    case BluetoothAdapter.STATE_TURNING_OFF:
                        btStateListener.onBTTurningOFF();
                        break;
                    case BluetoothAdapter.STATE_TURNING_ON:
                        btStateListener.onBTTurningON();
                        break;
                }
            }
        };

        mContext.registerReceiver(bluetoothStateReceiver, statusFilter);//注册状态广播
        btStateListener=listener;

    }

    public interface BTStateListener{
        void onBTOff(); // 蓝牙断开
        void onBTOn(); // 蓝牙打开
        void onBTTurningOFF(); // 蓝牙正在断开
        void onBTTurningON(); // 蓝牙正在打开
    }

    //申请打开蓝牙
    public void applyForEnableBT(Activity activity,int REQUEST_PERMISSION_CODE){
        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        activity.startActivityForResult(enableBtIntent,REQUEST_PERMISSION_CODE);
    }
    /**
     * 申请BLE扫描所需位置权限,要在外重写 onRequestPermissionsResult
     */
    public void applyForBLEPermission(Activity activity,int REQUEST_PERMISSION_CODE) {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.M) {
            int permissionCheck = 0;
            permissionCheck = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionCheck += activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions( // 请求授权
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION},
                        REQUEST_PERMISSION_CODE);// 自定义常量,任意整型
            } else {
                // 已经获得权限
            }
        }
    }

//扫描

    public BluetoothAdapter getBluetoothAdapter(){
        return mBtAdapter;
    }


    public void scanDevices(int lastingTime, TimeUnit timeUnit, final BTScanningListener listener){
        deviceList.clear();//清空上一次结果
        btScanningListener=listener;
        if (BLEMode){
            BLEScanner=mBtAdapter.getBluetoothLeScanner();
            BLEScanCallback=new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    listener.onFoundDevice(result.getDevice());
                }

                @Override
                public void onBatchScanResults(List<ScanResult> results) {
                    super.onBatchScanResults(results);
                    for(ScanResult result:results){
                        deviceList.add(result.getDevice());
                    }
                    listener.onScanFinished(deviceList);
                }

                @Override
                public void onScanFailed(int errorCode) {
                    super.onScanFailed(errorCode);
                }
            };
            BLEScanner.startScan(BLEScanCallback);
            listener.onScanStarted();

        }
        else {
            scanningBroadcastReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {

                    switch (Objects.requireNonNull(intent.getAction())) {
                        case BluetoothDevice.ACTION_FOUND: {
                            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                            deviceList.add(device);
                            btScanningListener.onFoundDevice(device);
                            break;
                        }
                        case BluetoothAdapter.ACTION_DISCOVERY_STARTED: {

                            btScanningListener.onScanStarted();
                            break;
                        }
                        case BluetoothAdapter.ACTION_DISCOVERY_FINISHED: {
                            mContext.unregisterReceiver(this);//取消广播
                            btScanningListener.onScanFinished(deviceList);
                            break;
                        }

                    }

                }
            };
            mContext.registerReceiver(scanningBroadcastReceiver, scanningFilter);//注册广播
            mBtAdapter.startDiscovery();//开始扫描
        }

        stopScanningAfterSetting(lastingTime,timeUnit);

    }

    public void scanDevices(int lastingTime, TimeUnit timeUnit, final BTScanningListener listener, List<ScanFilter> filters, ScanSettings scanSettings){
        deviceList.clear();//清空上一次结果
        btScanningListener=listener;
        if (BLEMode){
            BLEScanner=mBtAdapter.getBluetoothLeScanner();
            BLEScanCallback=new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    deviceList.add(result.getDevice());
                    listener.onFoundDevice(result.getDevice());
                }


            };
            BLEScanner.startScan(filters,scanSettings,BLEScanCallback);
            listener.onScanStarted();
            stopScanningAfterSetting(lastingTime,timeUnit);
        }

    }

    private void stopScanningAfterSetting(int lastingTime, TimeUnit timeUnit){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                stopScanDevices();
            }
        }, timeUnit.toMillis(lastingTime));//超时停止扫描
    }

    public void stopScanDevices() {
        if (BLEMode) {
            //测试,不知是否报错

            BLEScanner.stopScan(BLEScanCallback);
            btScanningListener.onScanFinished(deviceList);
        } else {
            if (mBtAdapter.isDiscovering()) {
                mBtAdapter.cancelDiscovery();
                //mContext.unregisterReceiver(scanningBroadcastReceiver);//取消广播,测试一下是否会冲突
            }
        }
    }

    public interface BTScanningListener {
        void onScanStarted(); // 扫描开始

        void onFoundDevice(BluetoothDevice device);  // 扫描到设备

        void onScanFinished(List<BluetoothDevice> devices);//扫描结束


    }




    //连接
    //Classic BT
    public interface DeviceStateListener{
        void onConnected(); // 设备已连接
        void onConnecting(); // 设备正在连接
        void onDisconnected(); // 设备连接断开
        void onReceivedData(int dataLength);//接收到数据
    }

    public boolean connectClassicDevice(BluetoothDevice device,UUID uuid,DeviceStateListener Listener){

        BluetoothSocket socket;
        InputStream inputStream;
        OutputStream outputStream;
        deviceStateListener=Listener;
        deviceStateListener.onConnecting();

        try {
            socket=device.createRfcommSocketToServiceRecord(uuid);//创建端口
            socket.connect();//连接端口
            inputStream=socket.getInputStream();
            outputStream=socket.getOutputStream();
            maxTransimitSize=socket.getMaxTransmitPacketSize();
            maxReceiveSize=socket.getMaxReceivePacketSize();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        connectedThread=new ConnectedThread(socket,inputStream,outputStream);
        connectedThread.start();
        return true;

    }




    private static byte[] subBytes(byte[] src, int begin, int count) {//数组截取
        byte[] bs = new byte[count];
        System.arraycopy(src, begin, bs, 0, count);
        return bs;
    }

    private class ConnectedThread extends Thread {//收发数据线程
        BluetoothSocket socket;
        InputStream inputStream;
        OutputStream outputStream;
        @Override
        public void run(){
            deviceStateListener.onConnected();
            while (isConnect) {
                try {
                    int nowAvailable = inputStream.available();
                    if (nowAvailable == lastAvailable) continue;//相同则跳过
                    if (nowAvailable > 0 && nowAvailable - lastAvailable > 1) {//大于1是为了等到全部都收到触发回调
                        deviceStateListener.onReceivedData(nowAvailable);//大于0说明收到数据
                    }
                    lastAvailable = nowAvailable;

                } catch (IOException e) {
                    e.printStackTrace();
                    isConnect = false;
                    deviceStateListener.onDisconnected();
                }

            }
        }
        private ConnectedThread(BluetoothSocket socket, InputStream inputStream, OutputStream outputStream) {
            this.socket = socket;
            this.inputStream = inputStream;
            this.outputStream = outputStream;
            isConnect = true;




            }

        public byte[] read() {
            byte[] buff = new byte[maxReceiveSize];
            int bytes;
            try {
                bytes = inputStream.read(buff);
                return subBytes(buff, 0, bytes);
            } catch (IOException e) {
                e.printStackTrace();
                isConnect=false;
                deviceStateListener.onDisconnected();
                return null;
            }

        }


        public boolean write(byte[] bytes) {
            try {
                outputStream.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
                isConnect=false;
                deviceStateListener.onDisconnected();
                return false;
            }
            return true;
        }

        public boolean cancel(boolean isTriggeredCallback) {
            try {
                inputStream.close();
                outputStream.close();
                isConnect=false;
                if (isTriggeredCallback)deviceStateListener.onDisconnected();
                socket.close();
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        }


    }


    //BLE
    public BluetoothGatt connectBLEDevice(final BluetoothDevice device, Boolean autoConnect, final DeviceStateListener Listener, final UUID ServiceUUID, final UUID CharacterUUID, final UUID DescriptorUUID){
        if (device==null)return null;
        deviceStateListener=Listener;
        BLEGattCallback = new BluetoothGattCallback() {

            @Override
            public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                                int newState) {
                super.onConnectionStateChange(gatt, status, newState);
                switch (newState) {
                    case BluetoothProfile.STATE_CONNECTED:
                        if (ServiceUUID==null ||CharacterUUID==null ||DescriptorUUID==null)return;//没有设置就跳过
                        gatt.discoverServices(); //搜索连接设备所支持的service

                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Listener.onDisconnected();
                        break;
                    case BluetoothProfile.STATE_CONNECTING:
                        break;
                    case BluetoothProfile.STATE_DISCONNECTING:
                        break;
                }

            }

            @Override
            public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                super.onServicesDiscovered(gatt, status);

                if (status == BluetoothGatt.GATT_SUCCESS) {
                if (ServiceUUID==null && CharacterUUID==null && DescriptorUUID!=null)
                {
                    outside:
                    for (BluetoothGattService Service:gatt.getServices()){
                        for (BluetoothGattCharacteristic Chara:Service.getCharacteristics())
                            for (BluetoothGattDescriptor description:Chara.getDescriptors()){
                                if (description.getUuid().equals(DescriptorUUID)){
                                    BLESetCharacteristicNotification(gatt,Service.getUuid(),Chara.getUuid(),description.getUuid());
                                    break outside;
                                }
                            }
                    }

                }else {
                    BLESetCharacteristicNotification(gatt,ServiceUUID,CharacterUUID,DescriptorUUID);
                }

                    deviceStateListener.onConnected();
                }

            }

            @Override
            public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicRead(gatt, characteristic, status);

            }

            @Override
            public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                super.onCharacteristicWrite(gatt, characteristic, status);

            }

            @Override
            public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
                super.onCharacteristicChanged(gatt, characteristic);
//            这里是可以监听到设备自身或者手机改变设备的一些数据修改通知
                isCharacteristicChanged=true;
                deviceStateListener.onReceivedData(characteristic.getValue().length);


            }
        };

        BLEGatt=device.connectGatt(mContext,autoConnect,BLEGattCallback);
        deviceStateListener.onConnecting();
        maxTransimitSize = 20;
        return BLEGatt;
    }

    private void BLESetCharacteristicNotification( BluetoothGatt gatt,UUID ServiceUUID,  UUID CharacterUUID,  UUID DescriptorUUID){
        BLEService=gatt.getService(ServiceUUID);
        BLECharacter=BLEService.getCharacteristic(CharacterUUID);

        BLEGatt.setCharacteristicNotification(BLECharacter, true);

        BluetoothGattDescriptor descriptor = BLECharacter.getDescriptor(DescriptorUUID);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        BLEGatt.writeDescriptor(descriptor);
    }

    public String getBLEDeviceAllServiceAndCharaUuid(BluetoothGatt BLEGatt, Boolean showINLoge) {
        List<BluetoothGattService> bluetoothGattServices = BLEGatt.getServices();
        StringBuilder AllString = new StringBuilder();
        for (BluetoothGattService bluetoothGattService : bluetoothGattServices) {
            StringBuilder string = new StringBuilder();
            List<BluetoothGattCharacteristic> characteristics = bluetoothGattService.getCharacteristics();
            string.append("*----------------------------------------------------*\nServicesUUID:").append(bluetoothGattService.getUuid());

            for (BluetoothGattCharacteristic characteristic : characteristics) {
                string.append("\n\tCharacteristicUUID:").append(characteristic.getUuid()).append("\n\t\tProperties:");

                int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_BROADCAST) > 0) {
                    string.append("PROPERTY_BROADCAST    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_EXTENDED_PROPS) > 0) {
                    string.append("PROPERTY_EXTENDED_PROPS    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    string.append("PROPERTY_INDICATE    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    string.append("PROPERTY_NOTIFY    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    string.append("PROPERTY_READ    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_SIGNED_WRITE) > 0) {
                    string.append("PROPERTY_SIGNED_WRITE    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    string.append("PROPERTY_WRITE    ");
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    string.append("PROPERTY_WRITE_NO_RESPONSE    ");
                }

                string.append("\n\t\tPermissions:");
                int characteristicPermissions= characteristic.getPermissions();
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_READ) > 0) {
                    string.append("PERMISSION_READ    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED) > 0) {
                    string.append("PERMISSION_READ_ENCRYPTED    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED_MITM) > 0) {
                    string.append("PERMISSION_READ_ENCRYPTED_MITM    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_WRITE) > 0) {
                    string.append("PERMISSION_WRITE    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED) > 0) {
                    string.append("PERMISSION_WRITE_ENCRYPTED    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED_MITM) > 0) {
                    string.append("PERMISSION_WRITE_ENCRYPTED_MITM    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED) > 0) {
                    string.append("PERMISSION_WRITE_SIGNED    ");
                }
                if ((characteristicPermissions & BluetoothGattCharacteristic.PERMISSION_WRITE_SIGNED_MITM) > 0) {
                    string.append("PERMISSION_WRITE_SIGNED_MITM    ");
                }

                for (BluetoothGattDescriptor Descriptor:characteristic.getDescriptors()) {
                    string.append("\n\t\t\tDescriptorUUID:").append(Descriptor.getUuid()).append("\n\t\t\t\tPermissions:");

                    int DescriptorProp = Descriptor.getPermissions();
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_READ) > 0) {
                        string.append("PERMISSION_READ    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED) > 0) {
                        string.append("PERMISSION_READ_ENCRYPTED    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED_MITM) > 0) {
                        string.append("PERMISSION_READ_ENCRYPTED_MITM    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_WRITE) > 0) {
                        string.append("PERMISSION_WRITE    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED) > 0) {
                        string.append("PERMISSION_WRITE_ENCRYPTED    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED_MITM) > 0) {
                        string.append("PERMISSION_WRITE_ENCRYPTED_MITM    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED) > 0) {
                        string.append("PERMISSION_WRITE_SIGNED    ");
                    }
                    if ((DescriptorProp & BluetoothGattDescriptor.PERMISSION_WRITE_SIGNED_MITM) > 0) {
                        string.append("PERMISSION_WRITE_SIGNED_MITM    ");
                    }
                }

            }
            string.append("\n*----------------------------------------------------*");
            AllString.append(string.toString());
            if (showINLoge)Log.e(TAG, string.toString() );
        }
        return AllString.toString();
    }

    public boolean isConnected(){
        return BLEMode?BLECharacter!=null:connectedThread!=null;
    }


    public boolean setMaxWritePacketSize(int size){

        if (size>0){
            if (BLEMode && size>20){
                maxTransimitSize = 20;
                return true;
            }else {
                maxTransimitSize = size;
                return true;
            }
        }
        else return false;

    }
    public boolean setMaxReceivePacketSize(int size){
        if (size>0){
            maxReceiveSize=size;return true;}
        else return false;

    }





    public byte[] readFromDevice(int timeout,TimeUnit timeUnit ) throws TimeoutException{
        if (BLEMode){
            if (BLECharacter==null && deviceStateListener!=null ){
                deviceStateListener.onDisconnected();
                }
        }else if (connectedThread==null && deviceStateListener!=null){
            deviceStateListener.onDisconnected();
            }

        byte[] result;
        readTask=new FutureTask<byte[]>(new Callable() {
            @Override
            public byte[] call()   {
                if (BLEMode){
                    while(!isCharacteristicChanged);//等待数据改变后再读取
                    isCharacteristicChanged=false;
                    return BLECharacter.getValue();
                }
                else return connectedThread.read();
            }
        });
        try {
            readTask.run();
            result = readTask.get(timeout, timeUnit);
            readTask.cancel(true);
            return result;
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;

    }
    private boolean BLEwirte(byte[] bytes){
        return BLECharacter.setValue(bytes) && BLEGatt.writeCharacteristic(BLECharacter);

    }

    public boolean writeToDevice(byte[] bytes){
        //判断是否已连接
        if (BLEMode){
            if (BLECharacter==null){
                if (deviceStateListener!=null)deviceStateListener.onDisconnected();
                return false;
            }
        }else if (connectedThread==null){
            if (deviceStateListener!=null)deviceStateListener.onDisconnected();
            return false;
        }

        if (maxTransimitSize==0||bytes.length<=maxTransimitSize)
            return BLEMode?BLEwirte(bytes):connectedThread.write(bytes);
        else{//分多次发送
            for (int i=0;i<(int) Math.ceil(bytes.length / maxTransimitSize);++i){
                int from=i*maxTransimitSize, to=from+maxTransimitSize;
                if (to > bytes.length) //防止超出
                    to = bytes.length;

                if (!(BLEMode?BLEwirte(Arrays.copyOfRange(bytes, from, to)):connectedThread.write(Arrays.copyOfRange(bytes, from, to))))return false;//有一次发送失败就返回假

            }
            return true;

        }

    }

//断开连接

    public boolean disconnectDevice(boolean isTriggeredCallback){
        if (BLEMode){
            BLEGatt.disconnect();
            if (isTriggeredCallback)deviceStateListener.onDisconnected();
            return true;
        }
        else return connectedThread.cancel(isTriggeredCallback);

    }

}




