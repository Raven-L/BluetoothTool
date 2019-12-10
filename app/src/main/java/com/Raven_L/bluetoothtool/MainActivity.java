package com.Raven_L.bluetoothtool;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    Button button ;
    CheckBox checkBox;
    ListView listView;
    List<String> list;
    List<BluetoothDevice> deviceList;
    BluetoothTool BT;
    BluetoothTool.BTScanningListener listener;
    ArrayAdapter<String> adapter;
    Boolean isBLE;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

         button = findViewById(R.id.Button);
        checkBox =findViewById(R.id.checkbox);
         listView=findViewById(R.id.list);

         checkBox.setOnClickListener(new View.OnClickListener() {
             @Override
             public void onClick(View view) {
                 if (checkBox.isChecked()){

                 }else{

                 }
             }
         });

         listener=new BluetoothTool.BTScanningListener() {
            @Override
            public void onScanStarted() {
                Log.e( "onScanStarted: ","开始扫描" );
                button.setText("扫描中...");
                list = new ArrayList<String>();
                deviceList=new ArrayList<>();
            adapter=new ArrayAdapter<String>(MainActivity.this,R.layout.support_simple_spinner_dropdown_item,list);

                listView.setAdapter(adapter);

            }

            @Override
            public void onFoundDevice(BluetoothDevice device) {
                Log.e( "onFoundDevice: ","扫描到"+device.getName() );
                String name=device.getName();
                list.add("Name:"+((name==null)?"null":name)+"\n Mac:"+device.getAddress());
                deviceList.add(device);
                adapter.notifyDataSetChanged();//实时刷新列表

            }

            @Override
            public void onScanFinished(List<BluetoothDevice> devices) {
                button.setText("扫描完毕");
            }
        };

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isBLE=checkBox.isChecked();
                    BT = new BluetoothTool(MainActivity.this, isBLE);
                    BT.applyForBLEPermission(MainActivity.this,123);
                    BT.scanDevices(10, TimeUnit.SECONDS,listener );
            }
        });

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                BT.stopScanDevices();
                Intent intent= new Intent(MainActivity.this,DeviceActivity.class);
                intent.putExtra("device",deviceList.get(i));
                startActivity(intent);
            }
        });

    }
}
