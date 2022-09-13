package com.huawei.audiodevicekit.bluetoothsample.view;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.giftedcat.wavelib.view.WaveView;
import com.huawei.audiobluetooth.api.Cmd;
import com.huawei.audiobluetooth.api.data.Acc;
import com.huawei.audiobluetooth.api.data.Gyro;
import com.huawei.audiobluetooth.api.data.SensorData;
import com.huawei.audiobluetooth.layer.protocol.mbb.DeviceInfo;
import com.huawei.audiobluetooth.utils.DateUtils;
import com.huawei.audiobluetooth.utils.LocaleUtils;
import com.huawei.audiobluetooth.utils.LogUtils;
import com.huawei.audiodevicekit.R;
import com.huawei.audiodevicekit.bluetoothsample.contract.SampleBtContract;
import com.huawei.audiodevicekit.bluetoothsample.data.Label;
import com.huawei.audiodevicekit.bluetoothsample.presenter.SampleBtPresenter;
import com.huawei.audiodevicekit.bluetoothsample.view.adapter.SingleChoiceAdapter;
import com.huawei.audiodevicekit.mvp.view.support.BaseAppCompatActivity;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

public class SampleBtActivity
        extends BaseAppCompatActivity<SampleBtContract.Presenter, SampleBtContract.View>
        implements SampleBtContract.View {
    private static final String TAG = "SampleBtActivity";

    private TextView tvDevice;

    private TextView tvStatus;

    private ListView listView;

    private TextView tvSendCmdResult;

    private Button btnSearch;

    private Button btnConnect;

    private Button btnDisconnect;

    private Button btnStartRecord;

    private Button btnEndRecord;

    private Spinner spinner;

    private Spinner labelSpinner;

    private Button btnSendCmd;

    private RecyclerView rvFoundDevice;

    private SingleChoiceAdapter mAdapter;

    private Cmd mATCmd = Cmd.VERSION;

    private String mMac;

    private List<Map<String, String>> maps;

    private SimpleAdapter simpleAdapter;

    private TextView tvDataCount;

    private String fileSuffix = "";

    private String label = Label.Nothing.name();

    private final String ACC = "ACC";

    private final String GYRO = "GYRO";

    private StringBuilder acc = new StringBuilder();
    private StringBuilder gyro = new StringBuilder();

    private int count =0;
    private WaveView accWaveView1;
    private WaveView accWaveView2;
    private WaveView accWaveView3;
    private WaveView gyroWaveView1;
    private WaveView gyroWaveView2;
    private WaveView gyroWaveView3;

    @Override
    public Context getContext() {
        return this;
    }

    @Override
    public SampleBtContract.Presenter createPresenter() {
        return new SampleBtPresenter();
    }

    @Override
    public SampleBtContract.View getUiImplement() {
        return this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected int getResId() {
        return R.layout.activity_main;
    }

    MediaRecorder recorder;
    AudioManager mAudioManager;

    @Override
    protected void initView() {
        tvDevice = findViewById(R.id.tv_device);
        tvStatus = findViewById(R.id.tv_status);
        tvDataCount = findViewById(R.id.tv_data_count);
        listView = findViewById(R.id.listview);
        tvSendCmdResult = findViewById(R.id.tv_send_cmd_result);
        btnSearch = findViewById(R.id.btn_search);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        spinner = findViewById(R.id.spinner);
        btnSendCmd = findViewById(R.id.btn_send_cmd);
        rvFoundDevice = findViewById(R.id.found_device);
        btnStartRecord = findViewById(R.id.btn_start_record);
        labelSpinner = findViewById(R.id.label_spinner);
       // initLineChart();
        initRecyclerView();
        initLabelSpinner();
        maps = new ArrayList<>();
        simpleAdapter = new SimpleAdapter(this, maps, android.R.layout.simple_list_item_1,
                new String[]{"data"}, new int[]{android.R.id.text1});
        listView.setAdapter(simpleAdapter);

        checkPermission();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

    }

    @Override
    public void onSensorDataChanged(SensorData sensorData) {

        Map<String, String> map = new HashMap<>();
        map.put("data", sensorData.toString());
        processDataTest(sensorData);
        maps.add(0, map);
//60  ; 12
        if (count>= 6 *10){
            endRecord();
        }


        runOnUiThread(() -> {
            // drawLine(sensorData);
            if (count % 12 == 0){
                tvDataCount.setText(getString(R.string.sensor_data, maps.size()));
            }

//            simpleAdapter.notifyDataSetChanged();

        });
    }


    private void initLabelSpinner() {
        List<Map<String, String>> data = new ArrayList<>();
        for (Label label : Label.values()) {
            HashMap<String, String> map = new HashMap<>();
            map.put("title", label.name());
            data.add(map);
        }
        labelSpinner.setAdapter(
                new SimpleAdapter(this, data, R.layout.item_spinner, new String[]{"title"},
                        new int[]{R.id.tv_name}));
        labelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                label = data.get(position).get("title");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void initSpinner() {
        List<Map<String, String>> data = new ArrayList<>();
        for (Cmd cmd : Cmd.values()) {
            if (cmd.isEnable()) {
                HashMap<String, String> map = new HashMap<>();
                Boolean isChinese = LocaleUtils.isChinese(this);
                String name = isChinese ? cmd.getNameCN() : cmd.getName();
                map.put("title", cmd.getType() + "-" + name);
                data.add(map);
            }
        }
        spinner.setAdapter(
                new SimpleAdapter(this, data, R.layout.item_spinner, new String[]{"title"},
                        new int[]{R.id.tv_name}));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LogUtils.i(TAG, "onItemSelected position = " + position);
                String title = data.get(position).get("title");
                String type = Objects.requireNonNull(title).split("-")[0];
                try {
                    int typeValue = Integer.parseInt(type);
                    mATCmd = Cmd.getATCmdByType(typeValue);
                } catch (NumberFormatException e) {
                    LogUtils.e(TAG, "parseInt fail e = " + e);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                LogUtils.i(TAG, "onNothingSelected parent = " + parent);
            }
        });
    }

    private void initRecyclerView() {
        SingleChoiceAdapter.SaveOptionListener mOptionListener = new SingleChoiceAdapter.SaveOptionListener() {
            @Override
            public void saveOption(String optionText, int pos) {
                LogUtils.i(TAG, "saveOption optionText = " + optionText + ",pos = " + pos);
                mMac = optionText.substring(1, 18);
                boolean connected = getPresenter().isConnected(mMac);
                if (connected) {
                    getPresenter().disConnect(mMac);
                } else {
                    getPresenter().connect(mMac);
                }
            }

            @Override
            public void longClickOption(String optionText, int pos) {
                LogUtils.i(TAG, "longClickOption optionText = " + optionText + ",pos = " + pos);
            }
        };
        mAdapter = new SingleChoiceAdapter(this, new ArrayList<>());
        mAdapter.setSaveOptionListener(mOptionListener);
        rvFoundDevice.setLayoutManager(new LinearLayoutManager(this, RecyclerView.VERTICAL, false));
        rvFoundDevice.setAdapter(mAdapter);
    }

    @Override
    protected void initData() {
        getPresenter().initBluetooth(this);
    }

    @Override
    protected void setOnclick() {
        super.setOnclick();
        btnConnect.setOnClickListener(v -> {
            getPresenter().connect(mMac);
        });
        btnDisconnect.setOnClickListener(v -> getPresenter().disConnect(mMac));
        btnSendCmd.setOnClickListener(v -> getPresenter().sendCmd(mMac, mATCmd.getType()));
        btnSearch.setOnClickListener(v -> getPresenter().checkLocationPermission(this));

        btnStartRecord.setOnClickListener(v -> {
            if (fileSuffix.equals("")) {

                btnStartRecord.setText(R.string.end_record);
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM_dd_HH_mm_ss");// HH:mm:ss
                Date date = new Date(System.currentTimeMillis());
                fileSuffix =label +"/" +simpleDateFormat.format(date);
                maps.clear();
                getPresenter().sendCmd(mMac, 19);
                //start();
            } else {
//               endRecord();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        getPresenter().processLocationPermissionsResult(requestCode, grantResults);
    }

    @Override
    public void onDeviceFound(DeviceInfo info) {
        if (mAdapter == null) {
            return;
        }
        runOnUiThread(() -> mAdapter
                .pushData(String.format("[%s] %s", info.getDeviceBtMac(), "HUAWEI Eyewear")));
    }

    @Override
    public void onStartSearch() {
        if (mAdapter != null) {
            runOnUiThread(() -> mAdapter.clearData());
        }
    }

    @Override
    public void onDeviceChanged(BluetoothDevice device) {
        if (tvDevice != null) {
//            release();
            runOnUiThread(() -> tvDevice
                    .setText(String.format("[%s] %s", device.getAddress(), "HUAWEI Eyewear")));

        }
    }

    @Override
    public void onConnectStateChanged(String stateInfo) {
        if (tvStatus != null) {
            runOnUiThread(() -> {
                tvStatus.setText(stateInfo);
            });
        }
    }


    @Override
    public void onSendCmdSuccess(Object result) {
        runOnUiThread(() -> {
            String info = DateUtils.getCurrentDate() + "\n" + result.toString();
            tvSendCmdResult.setText(info);
        });
    }

    @Override
    public void onError(String errorMsg) {
        runOnUiThread(
                () -> Toast.makeText(SampleBtActivity.this, errorMsg, Toast.LENGTH_LONG).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        getPresenter().deInit();
    }

    private void initLineChart() {
        findViewById(R.id.wave_view).setVisibility(View.VISIBLE);
        accWaveView1 = findViewById(R.id.wave_view_acc1);
        accWaveView2 = findViewById(R.id.wave_view_acc2);
        accWaveView3 = findViewById(R.id.wave_view_acc3);
        gyroWaveView1 = findViewById(R.id.wave_view_gyro1);
        gyroWaveView2 = findViewById(R.id.wave_view_gyro2);
        gyroWaveView3 = findViewById(R.id.wave_view_gyro3);

        accWaveView1.setMaxValue(4);
        accWaveView2.setMaxValue(4);
        accWaveView3.setMaxValue(4);

        gyroWaveView1.setMaxValue(4000);
        gyroWaveView2.setMaxValue(4000);
        gyroWaveView3.setMaxValue(4000);

    }


    private void drawLine(SensorData data) {
        if (data.accTimeStamp == 0)
            return;

        float accBase = 4096;
        float gyroBase = 16.384f;
        for (int i = 0; i < data.accelDataLen; i++) {
            Acc item = data.accelData[i];
            accWaveView1.showLine(item.x / accBase);
            accWaveView2.showLine(item.y / accBase);
            accWaveView3.showLine(item.z / accBase);
        }


        for (int i = 0; i < data.getGyroDataLen(); i++) {
            Gyro item = data.gyroData[i];
            gyroWaveView1.showLine(item.roll / gyroBase);
            gyroWaveView2.showLine(item.pitch / gyroBase);
            gyroWaveView3.showLine(item.yaw / gyroBase);
        }
    }

    private void processDataTest(SensorData data) {// one data per line
        if (fileSuffix.equals(""))
            return;
        else if (data.accTimeStamp == 0)
            return;
        float accBase = 4096;
        float gyroBase = 16.384f;
        char separator = ',';

        for (int i = 0; i < 20; i++) {
            Acc item = data.accelData[i];
            acc.append(item.getX() / accBase).append(separator)
                    .append(item.getY() / accBase).append(separator)
                    .append(item.getZ() / accBase).append("\n");
        }

        for (int i = 0; i < 20; i++) {
            Gyro item = data.gyroData[i];
            gyro.append(item.pitch / gyroBase).append(separator)
                    .append(item.roll / gyroBase).append(separator)
                    .append(item.yaw / gyroBase).append("\n");
        }
        count++;
    }


    private void processData(SensorData data) {//25 data per line
        if (fileSuffix.equals(""))
            return;
        else if (data.accTimeStamp == 0)
            return;
        float accBase = 4096;
        float gyroBase = 16.384f;
        char separator = ',';
        acc.append(data.time).append(separator);
        for (int i = 0; i < 20; i++) {
            Acc item = data.accelData[i];
            acc.append('[')
                    .append(item.getX() / accBase).append(" ")
                    .append(item.getY() / accBase).append(" ")
                    .append(item.getZ() / accBase).append("]").append(separator);
        }
        acc.append('\n');


        gyro.append(data.time).append(separator);
        for (int i = 0; i < 20; i++) {
            Gyro item = data.gyroData[i];
            gyro.append('[')
                    .append(item.pitch / gyroBase).append(" ")
                    .append(item.roll / gyroBase).append(" ")
                    .append(item.yaw / gyroBase).append("]").append(separator);
        }
        gyro.append('\n');
        count++;
    }

    protected void checkPermission() {
        List<String> requesList = new ArrayList<String>();


//        if (ContextCompat.checkSelfPermission(SampleBtActivity.this, Manifest.permission.RECORD_AUDIO)
//                != PackageManager. PERMISSION_GRANTED) {
//            ActivityCompat. requestPermissions( this, new String[]{Manifest.permission.RECORD_AUDIO },
//                    2);
//        }

        requesList.add(Manifest.permission.RECORD_AUDIO);
        requesList.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        requesList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requesList.add(Manifest.permission.BLUETOOTH_SCAN);
            requesList.add(Manifest.permission.BLUETOOTH_ADVERTISE);
            requesList.add(Manifest.permission.BLUETOOTH_CONNECT);

        }

        ActivityCompat.requestPermissions(SampleBtActivity.this,
                requesList.toArray(new String[0]),
                1);

//        if (ContextCompat.checkSelfPermission(SampleBtActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
//                != PackageManager. PERMISSION_GRANTED) {
//            ActivityCompat. requestPermissions( this, new String[]{Manifest.permission. WRITE_EXTERNAL_STORAGE,Manifest.permission.READ_EXTERNAL_STORAGE },
//                    1000);
//        }


    }


    private File getFile(String filename) {
        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        File file = new File(dir + "/mobileHCI/" + fileSuffix + "/" + filename);
        if (!file.exists()) {
            File parent = file.getParentFile();
            if (!parent.exists())
                parent.mkdirs();
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return file;
    }

    public void saveFile(String str, String type) {

        String dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
        try {
            File file = new File(dir + "/mobileHCI/" + fileSuffix + "/" + type + ".csv");

            if (!file.exists()) {
                File parent = file.getParentFile();
                if (!parent.exists())
                    parent.mkdirs();

                file.createNewFile();
                String head = "";

                switch (type) {
                    case ACC:
                        head = "x,y,z\n";
                        break;
                    case GYRO:
                        head = "pitch,roll,yaw\n";
                        break;

                }
                FileOutputStream outStream = new FileOutputStream(file);
                outStream.write(head.getBytes());
                outStream.close();
            }
            FileOutputStream outStream = new FileOutputStream(file, true);
            outStream.write(str.getBytes());
            outStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void release(){
        btnStartRecord.setText(R.string.start_record);
        getPresenter().sendCmd(mMac, 20);
    }
    private void endRecord(){
        release();
        //  stop();
        //    tvSendCmdResult.setText("File saved in Download/mobileHCI/"+fileSuffix+"/");
        saveFile(acc.toString(), ACC);
        saveFile(gyro.toString(), GYRO);
        acc.delete(0, acc.length());
        gyro.delete(0, gyro.length());
        count=0;
        fileSuffix = "";
    }



}




