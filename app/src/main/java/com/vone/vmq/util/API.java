package com.vone.vmq.util;

import java.util.function.Consumer;

public class API {

    public static String getUrlHeartbeat() {
        return "http://" + VpayConstant.SERVER_HOST + "/sign/heartbeat";
    }

    public static void heartbeat(Consumer<String> success, Consumer<Throwable> error) {
        HttpUtil.get(getUrlHeartbeat(), success, error);
    }

    public static void heartbeat(Consumer<String> success) {
        HttpUtil.get(getUrlHeartbeat(), success, null);
    }
}
