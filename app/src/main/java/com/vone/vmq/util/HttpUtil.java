package com.vone.vmq.util;

import android.util.Log;
import com.vone.vmq.Utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

public class HttpUtil {
    private static final String TAG = "HttpUtil";

    public static void get(String url, Consumer<String> success) {
        get(url, success, null);
    }

    public static void get(String url, Consumer<String> success, Consumer<Throwable> error) {
        Log.d(TAG, "request by get: " + url);
        Request request = new Request.Builder().url(url).build();
        Utils.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (Objects.nonNull(error)) {
                    error.accept(e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                if (Objects.nonNull(success)) {
                    ResponseBody body = response.body();
                    if (null != body) {
                        success.accept(body.string());
                    } else {
                        success.accept(null);
                    }
                }
            }
        });
    }
}
