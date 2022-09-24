package com.huawei.audiodevicekit.bluetoothsample.view;

import android.Manifest;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
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

import org.pytorch.IValue;
import org.pytorch.Tensor;
import org.pytorch.Module;
import org.pytorch.LiteModuleLoader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SampleBtActivity
        extends BaseAppCompatActivity<SampleBtContract.Presenter, SampleBtContract.View>
        implements SampleBtContract.View {
    private static final String TAG = "SampleBtActivity";

    private TextView tvDevice;

    private TextView tvStatus;


    private TextView tvSendCmdResult;

    private Button btnSearch;

    private Button btnConnect;

    private Button btnDisconnect;

    private Button btnPrediction;

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

    private TextView tvPrediction;
    private String fileSuffix = "";

    private String label = Label.Nothing.name();

    private final String ACC = "ACC";

    private final String GYRO = "GYRO";

    private StringBuilder acc = new StringBuilder();
    private StringBuilder gyro = new StringBuilder();

    private int data_len = 380;
//    private float[] inputData = new float[data_len *6];
    private ArrayDeque<Float>[] inputData = new ArrayDeque[]{
         new ArrayDeque<Float>(), new ArrayDeque<Float>(), new ArrayDeque<Float>(),
        new ArrayDeque<Float>(), new ArrayDeque<Float>(), new ArrayDeque<Float>()
    };

    private Module model;
    private String predictedResult;
    private String last_state = "Nothing";


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
        tvSendCmdResult = findViewById(R.id.tv_send_cmd_result);
        btnSearch = findViewById(R.id.btn_search);
        btnConnect = findViewById(R.id.btn_connect);
        btnDisconnect = findViewById(R.id.btn_disconnect);
        spinner = findViewById(R.id.spinner);
        btnSendCmd = findViewById(R.id.btn_send_cmd);
        rvFoundDevice = findViewById(R.id.found_device);
        btnPrediction = findViewById(R.id.btn_prediction);
        tvPrediction = findViewById(R.id.tv_prediction);
       // initLineChart();
        initRecyclerView();
//        initLabelSpinner();
        maps = new ArrayList<>();
        simpleAdapter = new SimpleAdapter(this, maps, android.R.layout.simple_list_item_1,
                new String[]{"data"}, new int[]{android.R.id.text1});

        checkPermission();
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        try {
            model = LiteModuleLoader.load(assetFilePath(this, "data_24_edge_output_converted_augmented.ptl"));

        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public static String assetFilePath(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
//        if (file.exists() && file.length() > 0) {
//            return file.getAbsolutePath();
//        }

        try (InputStream is = context.getAssets().open(assetName)) {
            try (OutputStream os = new FileOutputStream(file)) {
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }

    @Override
    public void onSensorDataChanged(SensorData sensorData) {

        Map<String, String> map = new HashMap<>();
        map.put("data", sensorData.toString());
        processDataTest(sensorData);
        maps.add(0, map);
//60  ; 12
        Log.d("COUNT", String.valueOf(inputData[0].size()) + "  "+ String.valueOf(count));
        if (count > data_len/20){
            count=0;
        }
        if (count== 8 & inputData[0].size()>=data_len){


            Tensor input = Tensor.fromBlob(integrateArray(inputData) ,new long[]{1,6,20,19});
            float[] a = input.getDataAsFloatArray();
            final Tensor outputTensor = model.forward(IValue.from(input)).toTensor();
            // getting tensor content as java array of floats
            final float[] scores = outputTensor.getDataAsFloatArray();
            Log.d("OUTPUT", Arrays.toString(scores));
            // searching for the index with maximum score
            float maxScore = -Float.MAX_VALUE;
            int maxScoreIdx = -1;
            for (int i = 0; i < scores.length; i++) {
                if (scores[i] > maxScore) {
                    maxScore = scores[i];
                    maxScoreIdx = i;
                }
            }

//            String[]  labels = {"Water", "Chip", "Hamburg", "Nothing", "TripleClick", "DoubleClick"};
            String[]  labels = {"Nuggets", "Hamburg","Nothing","Chew","Grind","DoubleClick"};
            predictedResult = labels[maxScoreIdx];

            if (!predictedResult.equals(last_state)){
               if( (last_state.equals("Nothing")) | predictedResult.equals("Nothing"))
               {
                    //do nothing
//                   if (last_state.equals("Nuggets") | last_state.equals("Hamburg")){
//                       predictedResult =last_state;
//                   }
               }

               else {
                   predictedResult = last_state;
               }
            }

            last_state =predictedResult;
//            inputData = new float[data_len *6];
            count=0;

        }


        runOnUiThread(() -> {
            // drawLine(sensorData);
            if (count % (data_len/40) == 0){
                tvDataCount.setText(getString(R.string.sensor_data, maps.size()));
            }


            tvPrediction.setText(predictedResult);
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

        btnPrediction.setOnClickListener(v -> {

            if (fileSuffix .equals("")) {
                fileSuffix="1";
                btnPrediction.setText(R.string.end_prediction);
                maps.clear();
                getPresenter().sendCmd(mMac, 19);
            } else {
                fileSuffix="";
                release();
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

        double [] mean ={-0.957993, 0.35382167, -0.0040243436, 0.009622897, 0.012073863, -0.0003459249} ;
       double[] std ={0.02470078, 0.032860123, 0.023139982, 0.19096272, 0.25724426, 0.33188197};

        if (fileSuffix.equals(""))
            return;
        else if (data.accTimeStamp == 0)
            return;
        float accBase = 4096;
        float gyroBase = 16.384f*1000;
        char separator = ',';


        //count ->12
        for (int i = 0; i < 20; i++) {
            Acc item = data.accelData[i];
            addElementToDeque(inputData[0],(float) ((item.getX()/accBase - mean[0] )/std[0]));
            addElementToDeque(inputData[1],(float) ((item.getY()/accBase - mean[1] )/std[1]));
            addElementToDeque(inputData[2],(float) ((item.getZ()/accBase - mean[2] )/std[2]));

        }

        for (int i = 0; i < 20; i++) {
            Gyro item = data.gyroData[i];
            addElementToDeque(inputData[3],(float) ((item.getPitch()/gyroBase - mean[3] )/std[3]));
            addElementToDeque(inputData[4],(float) ((item.getRoll()/gyroBase -  mean[4] )/std[4]));
            addElementToDeque(inputData[5],(float) ((item.getYaw()/gyroBase -   mean[5] )/std[5]));
        }
        count++;
    }

    private void processDataCollecting(SensorData data) {// one data per line
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
        btnPrediction.setText(R.string.prediction);
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

    private void addElementToDeque(ArrayDeque<Float> input, Float element ){
        if(input.size() >= data_len){
            input.removeFirst();
        }
        input.add(element);
    }


    private  float[] integrateArray(ArrayDeque<Float>[] list){
        ArrayDeque<Float> res= new ArrayDeque<>();
        for (int i = 0; i<list.length; i++){
            res.addAll(list[i]);
        }
        float[] res_array = new float[res.size()];
        Float[] res_F = res.toArray(new Float[res.size()]);
        for (int i =0; i< res.size();i++){
            res_array[i] = res_F[i];
        }
        return res_array;
    }


}




