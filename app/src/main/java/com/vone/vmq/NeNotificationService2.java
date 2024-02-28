package com.vone.vmq;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import com.vone.qrcode.R;
import com.vone.vmq.util.Api;
import org.json.JSONException;
import org.json.JSONObject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

public class NeNotificationService2 extends NotificationListenerService {
    public static boolean isRunning;
    private static String TAG = "NeNotificationService2";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Thread newThread = null;
    private PowerManager.WakeLock mWakeLock = null;

    /**
     * 如果出现无法通知的情况，进入前台，然后主动打开通知
     */
    public static void enterForeground(Context context, String title, String text, String extra) {
        if (context == null) return;
        Log.i(TAG, "enter fore ground");
        Intent intent = new Intent(context, ForegroundServer.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(ForegroundServer.GET_NOTIFY_TITLE, title == null ? "" : title);
        intent.putExtra(ForegroundServer.GET_NOTIFY_TEXT, text == null ? "" : text);
        intent.putExtra(ForegroundServer.GET_NOTIFY_EXTRA, extra == null ? "" : extra);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    public static void exitForeground(Context context) {
        if (context == null) return;
        Log.i(TAG, "exitForeground");

        Intent intent1 = new Intent();
        intent1.setAction(Constant.FINISH_FOREGROUND_SERVICE);
        context.sendBroadcast(intent1);
    }

    public static String getMoney(String content) {
        List<String> ss = new ArrayList<>();
        for (String sss : content.replaceAll(",", "")
                .replaceAll("[^0-9.]", ",").split(",")) {
            if (sss.length() > 0)
                ss.add(sss);
        }
        if (ss.size() < 1) {
            return null;
        } else {
            return ss.get(ss.size() - 1);
        }
    }

    public static String md5(String string) {
        if (TextUtils.isEmpty(string)) {
            return "";
        }
        MessageDigest md5 = null;
        try {
            md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) {
                    temp = "0" + temp;
                }
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    // 申请设备电源锁
    @SuppressLint("InvalidWakeLockTag")
    public void acquireWakeLock(final Context context) {
        handler.post(() -> {
            if (null == mWakeLock) {
                PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                if (pm != null) {
                    mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "WakeLock");
                }
            }
            if (null != mWakeLock) {
                mWakeLock.acquire(5000);
            }
        });
    }

    // 释放设备电源锁
    public void releaseWakeLock() {
        handler.post(() -> {
            if (null != mWakeLock) {
                mWakeLock.release();
                mWakeLock = null;
            }
        });
    }

    // 心跳进程
    public void initAppHeart() {
        Log.d(TAG, "开始启动心跳线程");
        newThread = new Thread(() -> {
            Log.d(TAG, "心跳线程启动！");
            while (isRunning && newThread == Thread.currentThread()) {
                Api.heartbeat(
                        data -> Log.d(TAG, "heartbeat response data:" + data),
                        error -> foregroundHeart(Api.getUrlHeartbeat())
                );
                try {
                    Thread.sleep(30 * 1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        newThread.start(); // 启动线程
    }

    // 当收到一条消息的时候回调，sbn是收到的消息
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "接受到通知消息");
        writeNotifyToFile(sbn);
        // 微信支付部分通知，会调用两次，导致统计不准确
        if ((sbn.getNotification().flags & Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "群组摘要通知，忽略");
            return;
        }

        Notification notification = sbn.getNotification();
        String pkg = sbn.getPackageName();
        if (notification != null) {
            Bundle extras = notification.extras;
            if (extras != null) {
                CharSequence _title = extras.getCharSequence(NotificationCompat.EXTRA_TITLE, "");
                CharSequence _content = extras.getCharSequence(NotificationCompat.EXTRA_TEXT, "");
                Log.d(TAG, "**********************");
                Log.d(TAG, "包名:" + pkg);
                Log.d(TAG, "标题:" + _title);
                Log.d(TAG, "内容:" + _content);
                Log.d(TAG, "**********************");
                // to string (企业微信之类的 getString 会出错，换getCharSequence)
                String title = _title.toString();
                String content = _content.toString();
                if (Constant.PKG_ALIPAY.equals(pkg)) {
                    if (content.equals("")) {
                        return;
                    }
                    String[] matches = {"通过扫码向你付款", "成功收款", "店员通"};
                    if (Arrays.stream(matches).anyMatch(title::contains)
                            || Arrays.stream(matches).anyMatch(content::contains)) {
                        String money;
                        // 新版支付宝，会显示积分情况下。先匹配标题上的金额
                        if (content.contains("商家积分")) {
                            money = getMoney(title);
                            if (money == null) {
                                money = getMoney(content);
                            }
                        } else {
                            money = getMoney(content);
                            if (money == null) {
                                money = getMoney(title);
                            }
                        }
                        if (money != null) {
                            Log.d(TAG, "onAccessibilityEvent: 匹配成功： 支付宝 到账 " + money);
                            appPush(2, Double.parseDouble(money));
                        } else {
                            handler.post(() -> Toast.makeText(getApplicationContext(), "监听到支付宝消息但未匹配到金额！", Toast.LENGTH_SHORT).show());
                        }
                    }
                } else if (Constant.PKG_WECHAT.equals(pkg) || Constant.PKG_WEWORK.equals(pkg)) {
                    if (content.equals("")) {
                        return;
                    }
                    if (title.equals("微信支付") || title.equals("微信收款助手") || title.equals("微信收款商业版")
                            || (title.equals("对外收款") || title.equals("企业微信")) &&
                            (content.contains("成功收款") || content.contains("收款通知"))) {
                        String money = getMoney(content);
                        if (money != null) {
                            Log.d(TAG, "onAccessibilityEvent: 匹配成功： 微信到账 " + money);
                            try {
                                appPush(1, Double.parseDouble(money));
                            } catch (Exception e) {
                                Log.e(TAG, "app push 错误！！！", e);
                            }
                        } else {
                            handler.post(() -> Toast.makeText(getApplicationContext(), "监听到微信消息但未匹配到金额！", Toast.LENGTH_SHORT).show());
                        }
                    }
                } else if ("com.vone.qrcode".equals(pkg)) {
                    if (content.equals("这是一条测试推送信息，如果程序正常，则会提示监听权限正常")) {
                        handler.post(() -> Toast.makeText(getApplicationContext(), "监听正常，如无法正常回调请联系作者反馈！", Toast.LENGTH_SHORT).show());
                    }
                }
            }
        }
    }

    // 当移除一条消息的时候回调，sbn是被移除的消息
    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {

    }

    // 当连接成功时调用，一般在开启监听后会回调一次该方法
    @Override
    public void onListenerConnected() {
        isRunning = true;
        // 开启心跳线程
        initAppHeart();

        handler.post(new Runnable() {
            public void run() {
                Toast.makeText(getApplicationContext(), "监听服务开启成功！", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        isRunning = false;
        if (newThread != null) {
            newThread.interrupt();
        }
        newThread = null;
    }

    private void writeNotifyToFile(StatusBarNotification sbn) {
        if (!sbn.isClearable()) {
            return;
        }
        Log.i(TAG, "write notify message to file");
        // 具有写入权限，否则不写入
        CharSequence notificationTitle = null;
        CharSequence notificationText = null;
        CharSequence subText = null;

        Bundle extras = sbn.getNotification().extras;
        if (extras != null) {
            notificationTitle = extras.getCharSequence(Notification.EXTRA_TITLE);
            notificationText = extras.getCharSequence(Notification.EXTRA_TEXT);
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        }
        String packageName = sbn.getPackageName();
        String time = Utils.formatTime(Calendar.getInstance().getTime());

        String writText = "\n" + "[" + time + "]" + "[" + packageName + "]" + "\n" +
                "[" + notificationTitle + "]" + "\n" + "[" + notificationText + "]" + "\n" +
                "[" + subText + "]" + "\n";

        // 使用 post 异步的写入
        Utils.putStr(this, writText);
    }

    /**
     * 通知服务器收款到账
     */
    public void appPush(int type, double price) {
        acquireWakeLock(this);
        Api.push(type, price,
                data -> {
                    Log.d(TAG, "push response data: " + data);
                    releaseWakeLock();
                },
                error -> {
                    Log.e(TAG, "push response error", error);
                    foregroundPost(Api.getUrlPush() + "type=" + type + "&price=" + price + "&force_push=true");
                    releaseWakeLock();
                }
        );
    }

    private void foregroundHeart(String url) {
        final Context context = NeNotificationService2.this;
        if (isRunning) {
            final JSONObject extraJson = new JSONObject();
            try {
                extraJson.put("url", url);
                extraJson.put("show", false);
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    enterForeground(context,
                            context.getString(R.string.app_name),
                            context.getString(R.string.app_is_heart), extraJson.toString());
                }
            });
        }
    }

    /**
     * 当通知失败的时候，前台强制通知
     */
    private void foregroundPost(String url) {
        final Context context = NeNotificationService2.this;
        if (isRunning) {
            final JSONObject extraJson = new JSONObject();
            try {
                extraJson.put("url", url);
                extraJson.put("try_count", 5);
            } catch (JSONException jsonException) {
                jsonException.printStackTrace();
            }
            handler.post(new Runnable() {
                @Override
                public void run() {
                    enterForeground(context,
                            context.getString(R.string.app_name),
                            context.getString(R.string.app_is_post), extraJson.toString());
                }
            });
        }
    }

}
