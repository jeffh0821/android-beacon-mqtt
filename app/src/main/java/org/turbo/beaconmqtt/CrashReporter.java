package org.turbo.beaconmqtt;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@SuppressLint("StaticFieldLeak")
public class CrashReporter extends AsyncTask<String, String, String> {
    private final static String TAG = "CrashReporter";
    private final static String BASE_URL = "http://lynx.nsk.ru:8088/mailer.php";
    private final static String SHARED_SECRET = "superpupersecret";

    private final Context context;


    public CrashReporter(Context context) {
        this.context = context;
    }

    private static String md5(String s) {
        MessageDigest m;
        byte[] digest = null;
        try {
            m = MessageDigest.getInstance("MD5");
            m.update(s.getBytes(), 0, s.length());
            digest = m.digest();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return new BigInteger(1, digest).toString(16);
    }

    private String getPostDataString(HashMap<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (first)
                first = false;
            else
                result.append("&");

            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
        }

        return result.toString();
    }

    @Override
    protected String doInBackground(String... strings) {
        try {
            BeaconApplication application = (BeaconApplication) context.getApplicationContext();
            PackageInfo packageInfo = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            String versionName = packageInfo.versionName;
            int versionBuild = packageInfo.versionCode % 100;

            HashMap<String, String> postDataParams = new HashMap<>();
            postDataParams.put("ANDROID_VERSION", Build.VERSION.RELEASE);
            postDataParams.put("PACKAGE_NAME", context.getPackageName());
            postDataParams.put("PHONE_MODEL", Build.MODEL);
            postDataParams.put("APP_VERSION_NAME",
                    context.getString(R.string.app_name));
            postDataParams.put("APP_VERSION_CODE",
                    context.getString(R.string.app_version, versionName, versionBuild));
            postDataParams.put("DATE", new Date().toString());
            postDataParams.put("STACK_TRACE", strings[0]);
            postDataParams.put("CUSTOM_DATA", strings[1]);
            postDataParams.put("APPLICATION_LOG", application.getDebugLog());

            OutputStream out;

            URL url = new URL(getUrl());
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestMethod("POST");

            out = new BufferedOutputStream(urlConnection.getOutputStream());

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
            writer.write(getPostDataString(postDataParams));
            writer.flush();
            writer.close();
            out.close();

            urlConnection.connect();

            int responseCode = urlConnection.getResponseCode();
            Log.i(TAG, "urlConnection.connect() returns " + responseCode);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private String getUrl() {
        String token = getToken();
        String key = getKey(token);
        return String.format("%s?token=%s&key=%s&", BASE_URL, token, key);
    }

    private String getKey(String token) {
        return md5(String.format("%s+%s", SHARED_SECRET, token));
    }

    private String getToken() {
        return md5(UUID.randomUUID().toString());
    }
}
