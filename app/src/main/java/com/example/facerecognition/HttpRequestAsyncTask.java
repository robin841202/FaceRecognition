package com.example.facerecognition;

import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Created by user on 2017/5/25.
 */



public class HttpRequestAsyncTask extends AsyncTask<String, Void, Void> {

    JSONObject postData;
    RequestType type;
    CustomCallback callback;
    final String TAG = "HttpRequestAsyncTask";

    public HttpRequestAsyncTask(JSONObject postData, RequestType type, CustomCallback callback){
        this.type = type;
        this.callback = callback;
        if (postData != null){
            this.postData = postData;
        }
    }

    @Override
    protected Void doInBackground(String... urls) {
        try{
            URL url = new URL(urls[0]);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setReadTimeout(8000);
            urlConnection.setConnectTimeout(13000);
            urlConnection.setRequestProperty("Content-Type","application/json");
            urlConnection.setRequestMethod(type.toString());
            urlConnection.setDoInput(true);

            try {
                if (this.postData != null){
                    urlConnection.setDoOutput(true);
                    OutputStreamWriter writer = new OutputStreamWriter(urlConnection.getOutputStream());
                    writer.write(postData.toString());
                    writer.flush();
                }

                int statusCode = urlConnection.getResponseCode();
                Log.i("HttpRequestAsyncTask", "statusCode:"+statusCode);

                if (statusCode ==  200) {

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                    String response = bufferedReader.readLine();
                    bufferedReader.close();

                    if (callback != null){
                        callback.completionHandler(true,type,response);
                    }

                    // From here you can convert the string to JSON with whatever JSON parser you like to use
                    // After converting the string to JSON, I call my custom callback. You can follow this process too, or you can implement the onPostExecute(Result) method
                } else if (statusCode == 204){
                    callback.completionHandler(true,type,null);
                }else {
                    // Status code is not 200 or 204
                    // Do something to handle the error
                    callback.completionHandler(false,type,statusCode);
                }
            }finally {
                urlConnection.disconnect();
            }


        }catch (Exception e){
            Log.d(TAG, e.getLocalizedMessage());
        }
        return null;
    }


}

