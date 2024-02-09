package com.vone.vmq;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.zxing.activity.CaptureActivity;
import com.vone.qrcode.R;
import com.vone.vmq.util.API;
import com.vone.vmq.util.Constant;
import com.vone.vmq.util.VpayConstant;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    int id = 0;
    private TextView txthost;
    private TextView txtkey;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txthost = findViewById(R.id.txt_host);
        txtkey = findViewById(R.id.txt_key);

        // 检测通知使用权是否启用
        if (!isNotificationListenersEnabled()) {
            // 跳转到通知使用权页面
            gotoNotificationAccessSetting();
        } else if (!Utils.checkBatteryWhiteList(this)) {
            Utils.gotoBatterySetting(this);
        }
        // 重启监听服务
        if (!NeNotificationService2.isRunning) {
            toggleNotificationListenerService(this);
        }
        // 读入保存的配置数据并显示
        SharedPreferences read = getSharedPreferences("vone", MODE_PRIVATE);
        VpayConstant.SERVER_HOST = read.getString("host", "");
        VpayConstant.SERVER_KEY = read.getString("key", "");

        if (!VpayConstant.SERVER_HOST.equals("") && !VpayConstant.SERVER_KEY.equals("")) {
            txthost.setText(String.format(" 通知地址：%s", VpayConstant.SERVER_HOST));
            txtkey.setText(String.format(" 通讯密钥：%s", VpayConstant.SERVER_KEY));
            VpayConstant.isOk = true;
        }
        Toast.makeText(MainActivity.this, "v免签开源免费免签系统 v1.8.1", Toast.LENGTH_SHORT).show();
    }

    // 扫码配置
    public void startQrCode(View v) {
        // 申请相机权限
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, Constant.REQ_PERM_CAMERA);
            return;
        }
        // 申请文件读写权限（部分朋友遇到相册选图需要读写权限的情况，这里一并写一下）
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // 申请权限
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, Constant.REQ_PERM_EXTERNAL_STORAGE);
            return;
        }
        // 二维码扫码
        Intent intent = new Intent(MainActivity.this, CaptureActivity.class);
        startActivityForResult(intent, Constant.REQ_QR_CODE);
    }

    // 手动配置
    public void doInput(View v) {
        final EditText inputServer = new EditText(this);
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入配置数据").setView(inputServer)
                .setNegativeButton("取消", null);
        builder.setPositiveButton("确认", (dialog, which) -> {
            String scanResult = inputServer.getText().toString();

            String[] tmp = scanResult.split("/");
            if (tmp.length != 2) {
                Toast.makeText(MainActivity.this, "数据错误，请您输入网站上显示的配置数据!", Toast.LENGTH_SHORT).show();
                return;
            }
            VpayConstant.SERVER_HOST = tmp[0];
            VpayConstant.SERVER_KEY = tmp[1];

            API.heartbeat(data -> Log.d(TAG, "onResponse: " + data));

            if (tmp[0].contains("localhost")) {
                Toast.makeText(MainActivity.this, "配置信息错误，本机调试请访问 本机局域网IP:8080(如192.168.1.101:8080) 获取配置信息进行配置!", Toast.LENGTH_LONG).show();
                return;
            }
            // 将扫描出的信息显示出来
            txthost.setText(String.format(" 通知地址：%s", tmp[0]));
            txtkey.setText(String.format(" 通讯密钥：%s", tmp[1]));

            SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
            editor.putString("host", VpayConstant.SERVER_HOST);
            editor.putString("key", VpayConstant.SERVER_KEY);
            editor.apply();
        });
        builder.show();

    }

    // 检测心跳
    public void doStart(View view) {
        if (!VpayConstant.isOk) {
            Toast.makeText(getApplicationContext(), "请您先配置!", Toast.LENGTH_SHORT).show();
            return;
        }

        API.heartbeat(
                data -> handler.post(() -> Toast.makeText(MainActivity.this, "心跳返回：" + data, Toast.LENGTH_LONG).show()),
                error -> handler.post(() -> Toast.makeText(MainActivity.this, "心跳状态错误，请检查配置是否正确!", Toast.LENGTH_SHORT).show())
        );
    }

    // 检测监听
    public void checkPush(View v) {
        Notification mNotification;
        NotificationManager mNotificationManager;
        mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("1",
                    "Channel1", NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableLights(true);
            channel.setLightColor(Color.GREEN);
            channel.setShowBadge(true);
            mNotificationManager.createNotificationChannel(channel);

            Notification.Builder builder = new Notification.Builder(this, "1");

            mNotification = builder
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        } else {
            mNotification = new Notification.Builder(MainActivity.this)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setTicker("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .setContentTitle("V免签测试推送")
                    .setContentText("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")
                    .build();
        }
        // Toast.makeText(MainActivity.this, "已推送信息，如果权限，那么将会有下一条提示！", Toast.LENGTH_SHORT).show();

        mNotificationManager.notify(id++, mNotification);
    }

    // 各种权限的判断
    private void toggleNotificationListenerService(Context context) {
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        pm.setComponentEnabledSetting(new ComponentName(context, NeNotificationService2.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);

        // 不要每次打开都显示
        // Toast.makeText(MainActivity.this, "监听服务启动中...", Toast.LENGTH_SHORT).show();
    }

    public boolean isNotificationListenersEnabled() {
        String pkgName = getPackageName();
        final String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (!TextUtils.isEmpty(flat)) {
            final String[] names = flat.split(":");
            for (String name : names) {
                final ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null) {
                    if (TextUtils.equals(pkgName, cn.getPackageName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    protected boolean gotoNotificationAccessSetting() {
        try {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return true;
        } catch (ActivityNotFoundException e) {// 普通情况下找不到的时候需要再特殊处理找一次
            try {
                Intent intent = new Intent();
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName cn = new ComponentName("com.android.settings", "com.android.settings.Settings$NotificationAccessSettingsActivity");
                intent.setComponent(cn);
                intent.putExtra(":settings:show_fragment", "NotificationAccessSettings");
                startActivity(intent);
                return true;
            } catch (Exception e1) {
                e1.printStackTrace();
            }
            Toast.makeText(this, "对不起，您的手机暂不支持", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            return false;
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 扫描结果回调
        if (requestCode == Constant.REQ_QR_CODE && resultCode == RESULT_OK) {
            Bundle bundle = data.getExtras();
            String scanResult = bundle.getString(Constant.INTENT_EXTRA_KEY_QR_SCAN);

            String[] tmp = scanResult.split("/");
            if (tmp.length != 2) {
                Toast.makeText(MainActivity.this, "二维码错误，请您扫描网站上显示的二维码!", Toast.LENGTH_SHORT).show();
                return;
            }
            VpayConstant.SERVER_HOST = tmp[0];
            VpayConstant.SERVER_KEY = tmp[1];

            API.heartbeat(res -> VpayConstant.isOk = true);

            // 将扫描出的信息显示出来
            txthost.setText(String.format(" 通知地址：%s", tmp[0]));
            txtkey.setText(String.format(" 通讯密钥：%s", tmp[1]));

            SharedPreferences.Editor editor = getSharedPreferences("vone", MODE_PRIVATE).edit();
            editor.putString("host", VpayConstant.SERVER_HOST);
            editor.putString("key", VpayConstant.SERVER_KEY);
            editor.apply();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case Constant.REQ_PERM_CAMERA:
                // 摄像头权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的相机访问权限", Toast.LENGTH_LONG).show();
                }
                break;
            case Constant.REQ_PERM_EXTERNAL_STORAGE:
                // 文件读写权限申请
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // 获得授权
                    startQrCode(null);
                } else {
                    // 被禁止授权
                    Toast.makeText(MainActivity.this, "请至权限中心打开本应用的文件读写权限", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }
}
