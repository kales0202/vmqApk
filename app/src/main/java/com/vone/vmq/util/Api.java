package com.vone.vmq.util;

import java.util.function.Consumer;

public class Api {

    public static String getUrlHeartbeat() {
        return VpayConstant.SERVER_HOST + "/sign/app/heartbeat";
    }

    public static String getUrlPush() {
        return VpayConstant.SERVER_HOST + "/sign/app/push";
    }

    public static void heartbeat(Consumer<String> success, Consumer<Throwable> error) {
        HttpUtil.get(getUrlHeartbeat(), success, error);
    }

    public static void heartbeat(Consumer<String> success) {
        HttpUtil.get(getUrlHeartbeat(), success, null);
    }

    public static void push(int type, double price, Consumer<String> success, Consumer<Throwable> error) {
        String url = getUrlPush() + "?type=" + type + "&price=" + price;
        HttpUtil.get(url, success, error);
    }
}
