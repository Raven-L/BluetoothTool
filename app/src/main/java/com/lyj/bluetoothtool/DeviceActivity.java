package com.lyj.bluetoothtool;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.example.bluetoothtool.BluetoothTool;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


public class DeviceActivity extends AppCompatActivity {
    TextView state,readText;
    Button disconnect,sendButton;
    EditText writeText;
BluetoothDevice device;
    Boolean isBLE;
    BluetoothTool BT;
    BluetoothTool.DeviceStateListener deviceListener;

    //HC-42BLE的UUID
    public static final UUID SearchUUID = UUID.fromString("0000fff0-0000-1000-8000-00805f9b34fb");//搜素用
    public static final  UUID ServiceUUID = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");//服务用
    private static  final UUID PassthroughCharacterUUID = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");//透传数据用
    private static  final UUID receiveDescriptorUUID=UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") ;//接收数据用
    BluetoothGatt bluetoothGatt;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        state =findViewById(R.id.state);
        disconnect=findViewById(R.id.disconnectButton);
        sendButton=findViewById(R.id.sendButton);
        readText=findViewById(R.id.readText);
        writeText=findViewById(R.id.writeText);

        readText.setMovementMethod(ScrollingMovementMethod.getInstance());
disconnect.setEnabled(false);

        device=getIntent().getParcelableExtra("device");
        isBLE=device.getType()==BluetoothDevice.DEVICE_TYPE_LE;
        Log.e( "onCreate: ",""+isBLE );
        BT=new BluetoothTool(DeviceActivity.this,isBLE);
        deviceListener=new BluetoothTool.DeviceStateListener() {
            @Override
            public void onConnected() {
                Log.e( "onConnected: ","已连接" );
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        state.setText("状态:已连接");
                        disconnect.setEnabled(true);
                    }
                });
                BT.getBLEDevicecAllServiceAndCharaUuid(bluetoothGatt,true);

            }

            @Override
            public void onConnecting() {
                Log.e( "onConnecting: ","连接中" );
                state.setText("状态:连接中...");
            }

            @Override
            public void onDisconnected() {
                Log.e( "onDisconnected: ","断开连接" );
                state.setText("状态:断开连接");
            }

            @Override
            public void onReceivedData(int dataLength) {
                StringBuilder string= new StringBuilder();
                string.append(new SimpleDateFormat("HH时mm分ss秒 接收：").format(new Date()));
                try {
                    string.append( new String( BT.readFromDevice(2, TimeUnit.SECONDS)));


                } catch (TimeoutException e) {
                    e.printStackTrace();
                    string.append("读取超时");
                }
                string.append("\n");
                refreshTextView(string.toString());

            }
        };

        if (isBLE){
             bluetoothGatt=BT.connectBLEDevice(device,true,deviceListener,ServiceUUID,PassthroughCharacterUUID,receiveDescriptorUUID);

        }else{
            BT.connectClassicDevice(device,BT.CLassic_UUID_SerialPortServiceClass,deviceListener);
        }

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String text=writeText.getText().toString();
                if (text.equals("")){
                    Toast.makeText(DeviceActivity.this,"输入为空",Toast.LENGTH_SHORT).show();
                }else{
                    if(BT.writeToDevice(text.getBytes())){

                        StringBuilder string= new StringBuilder();
                        string.append(new SimpleDateFormat("HH时mm分ss秒 发送：").format(new Date()));
                        string.append(text);
                        string.append("\n");
                        refreshTextView(string.toString());
                    }else{
                        Toast.makeText(DeviceActivity.this,"发送失败",Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        disconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                BT.disconnecDevice(false);
                //finish();
            }
        });

    }



    void refreshTextView(String msg){
           readText.append(msg);
          int offset=readText.getLineCount()*readText.getLineHeight();
          if(offset>readText.getHeight()){
              readText.scrollTo(0,offset);
               }

         }


}
