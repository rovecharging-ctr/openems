package com.mkyong.http;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.ssl.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.openems.common.utils.JsonUtils;

public class Java11HttpClientExample {

    // one instance, reuse
    private HttpClient httpClient;

    static SSLContext insecureContext() {

        TrustManager[] noopTrustManager = new TrustManager[]{

                new X509TrustManager() {

                    public void checkClientTrusted(X509Certificate[] xcs, String string) {
                    }

                    public void checkServerTrusted(X509Certificate[] xcs, String string) {
                    }

                    public X509Certificate[] getAcceptedIssuers() {
                        return null;
                    }
                }
        };
        try {
            SSLContext sc = SSLContext.getInstance("ssl");
            sc.init(null, noopTrustManager, null);
            return sc;
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            return null;
        }
    }

    public static void main(String[] args) throws Exception {
        var properties = System.getProperties();
        properties.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());


        Java11HttpClientExample obj = new Java11HttpClientExample();

//        System.out.println("Testing 1 - Send Http GET request");
//        obj.sendGet();

        System.out.println("Testing 2 - Send Http POST request");
        obj.sendPost();

    }

    private void sendGet() throws Exception {

        HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create("https://httpbin.org/get")).setHeader("User-Agent", "Java 11 HttpClient Bot").build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // print status code
        System.out.println(response.statusCode());

        // print response body
        System.out.println(response.body());

        return JsonUtils.parse(response.body().toString()) ;

    }

    private void sendPost() throws Exception {

        this.httpClient = HttpClient.newBuilder()
                .sslContext(insecureContext())
                .build();

        // form parameters
        Map<Object, Object> data = new HashMap<>();
        data.put("key", "UgDbdmNY51G48cTEyAu2WjOsFz0pLR");

        HttpRequest request = HttpRequest.newBuilder().POST(buildFormDataFromMap(data)).uri(URI.create("https://192.168.68.122/Endpoints/ReadLoadBalancing/")).setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
                .header("Content-Type", "application/x-www-form-urlencoded").build();

        HttpResponse<String> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // print status code
        System.out.println(response.statusCode());

        // print response body
        System.out.println(response.body());

    }

    private static HttpRequest.BodyPublisher buildFormDataFromMap(Map<Object, Object> data) {
        var builder = new StringBuilder();
        for (Map.Entry<Object, Object> entry : data.entrySet()) {
            if (builder.length() > 0) {
                builder.append("&");
            }
            builder.append(URLEncoder.encode(entry.getKey().toString(), StandardCharsets.UTF_8));
            builder.append("=");
            builder.append(URLEncoder.encode(entry.getValue().toString(), StandardCharsets.UTF_8));
        }
        System.out.println(builder.toString());
        return HttpRequest.BodyPublishers.ofString(builder.toString());
    }

}

