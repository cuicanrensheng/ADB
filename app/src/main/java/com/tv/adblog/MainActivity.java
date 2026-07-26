package com.tv.adblog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.tananaev.adblib.AdbConnection;
import com.tananaev.adblib.AdbCrypto;
import com.tananaev.adblib.AdbStream;
import java.io.File;
import java.io.FileWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private EditText etIp;
    private TextView tvLog;
    private Button btnConnect, btnStartLog, btnSave;
    private AdbConnection adbConnection;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private StringBuilder logBuffer = new StringBuilder();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isRunningLog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        etIp = findViewById(R.id.et_ip);
        tvLog = findViewById(R.id.tv_log);
        btnConnect = findViewById(R.id.btn_connect);
        btnStartLog = findViewById(R.id.btn_start_log);
        btnSave = findViewById(R.id.btn_save);

        btnConnect.setOnClickListener(v -> connectAdb());
        btnStartLog.setOnClickListener(v -> startCaptureLog());
        btnSave.setOnClickListener(v -> saveLogToFile());
    }

    private void connectAdb() {
        String ip = etIp.getText().toString().trim();
        if(ip.isEmpty()){
            showToast("请填写电视IP地址");
            return;
        }
        executor.execute(() -> {
            try {
                Socket socket = new Socket(ip,5555);
                AdbCrypto crypto = AdbCrypto.generateAdbKeyPair();
                adbConnection = AdbConnection.create(socket,crypto);
                adbConnection.connect();
                runOnUiThread(() -> appendLog("✅ADB连接成功：" + ip));
            }catch (Exception e){
                runOnUiThread(() -> appendLog("❌连接失败："+e.getMessage()+"\n确认电视已开启ADB调试"));
            }
        });
    }

    private void startCaptureLog(){
        if(adbConnection == null || !adbConnection.isConnected()){
            showToast("请先连接电视");
            return;
        }
        if(isRunningLog){
            showToast("日志正在抓取中");
            return;
        }
        isRunningLog = true;
        executor.execute(() -> {
            try {
                //执行logcat 命令，抓取电视完整系统日志
                AdbStream stream = adbConnection.open("shell:logcat -v time");
                while (!Thread.currentThread().isInterrupted()){
                    byte[] buf = stream.read();
                    if(buf != null){
                        String text = new String(buf);
                        logBuffer.append(text);
                        mainHandler.post(() -> appendLog(text));
                    }
                }
            }catch (Exception ex){
                runOnUiThread(()->appendLog("日志采集异常："+ex.getMessage()));
            }
        });
    }

    private void saveLogToFile(){
        if(logBuffer.length() ==0){
            showToast("暂无日志可保存");
            return;
        }
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA);
            String fileName = "tv_log_"+sdf.format(new Date())+".txt";
            File saveFile = new File(getExternalFilesDir(null),fileName);
            FileWriter writer = new FileWriter(saveFile);
            writer.write(logBuffer.toString());
            writer.flush();
            writer.close();
            showToast("日志保存成功："+saveFile.getAbsolutePath());
        }catch (Exception e){
            showToast("保存失败"+e.getMessage());
        }
    }

    private void appendLog(String msg){
        tvLog.append(msg);
    }

    private void showToast(String text){
        runOnUiThread(()-> Toast.makeText(MainActivity.this,text,Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try{
            if(adbConnection !=null) adbConnection.close();
        }catch (Exception e){}
        executor.shutdownNow();
    }
}
