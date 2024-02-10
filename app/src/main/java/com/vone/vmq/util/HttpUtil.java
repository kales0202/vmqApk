package com.vone.vmq.util;

import android.util.Log;
import com.vone.vmq.Utils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class HttpUtil {
    private static final String TAG = "HttpUtil";

    public static void get(String url, Consumer<String> success) {
        get(url, success, null);
    }

    public static void get(String url, Consumer<String> success, Consumer<Throwable> error) {
        Log.d(TAG, "request by get: " + url);
        Request request = new Request.Builder().url(url).build();
        HttpUtil.getOkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                if (Objects.nonNull(error)) {
                    error.accept(e);
                }
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                ResponseBody body = response.body();
                if (!response.isSuccessful() || null == body) {
                    if (Objects.nonNull(error)) {
                        error.accept(new RuntimeException(response.message()));
                    }
                    return;
                }
                if (Objects.nonNull(success)) {
                    success.accept(body.string());
                }
            }
        });
    }

    private static final class OkHttpClientHolder {
        static final OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .addInterceptor(new UserAgentInterceptor())
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS)
                .build();

        static class UserAgentInterceptor implements Interceptor {
            @Override
            public Response intercept(Chain chain) throws IOException {
                long now = System.currentTimeMillis();
                Request request = chain.request().newBuilder()
                        .header("Vpay-Account", VpayConstant.SERVER_ACCOUNT)
                        .header("Vpay-Time", String.valueOf(now))
                        .header("Vpay-Sign", Utils.md5(now + VpayConstant.SERVER_KEY))
                        .build();
                return chain.proceed(request);
            }
        }
    }

    public static OkHttpClient getOkHttpClient() {
        return OkHttpClientHolder.okHttpClient;
    }
}
