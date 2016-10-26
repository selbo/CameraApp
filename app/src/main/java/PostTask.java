package com.example.levyy.camera;

import android.os.AsyncTask;
import android.util.Log;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.NameValuePair;
import java.util.List;
import java.util.ArrayList;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.util.EntityUtils;
import java.io.IOException;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONException;

public class PostTask extends AsyncTask<String, String, String> {
    private String mTs;

    public void PrepareAndExecute(String data)
    {
        this.execute(data);
    }

    public void setTimestamp(String ts)
    {
        mTs = ts;
    }

    @Override
    protected String doInBackground(String... data) {
        // Create a new HttpClient and Post Header
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost("http://104.198.196.211:8001/api/capture");

        try {
            //add data
            List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>(1);
            nameValuePairs.add(new BasicNameValuePair("timestamp", mTs));
            nameValuePairs.add(new BasicNameValuePair("img", data[0]));
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
            //execute http post
            HttpResponse response = httpclient.execute(httppost);
            String responseString = EntityUtils.toString(response.getEntity());
            String statusString = response.getStatusLine().toString();

            //I/HTTP response: HTTP/1.1 200 OK
            //I/HTTP response: {"status":"OK","url_full":"http:...jpg","url_short":"http://..."}
            Log.i("HTTP response", statusString);
            Log.i("HTTP response", responseString);

        } catch (ClientProtocolException e) {
            Log.d("HTTP Clinet protocol Exception: " , e.getMessage());
        } catch (IOException e) {
            Log.d("HTTP Clinet IO Exception: " , e.getMessage());
        }
        return "";
    }
}