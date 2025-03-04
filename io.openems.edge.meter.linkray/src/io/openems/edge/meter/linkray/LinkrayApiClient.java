package io.openems.edge.meter.linkray;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.stream.Collectors;

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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import io.openems.common.exceptions.OpenemsError.OpenemsNamedException;
import io.openems.common.exceptions.OpenemsException;
import io.openems.common.utils.JsonUtils;

/**
 * Client for the Linkray API (<a href=
 * "https://api.discovergy.com/docs/">https://api.discovergy.com/docs/</a>).
 */
public class LinkrayApiClient {

	private static final String BASE_URL = "http://127.0.0.1:18080";

	private final String url;

	private final String apiKey;
	
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


	public LinkrayApiClient(String url, String apiKey) {
		// Store the API key
		this.url = url;

		// Store the API key
		this.apiKey = apiKey;
	}

	/**
	 * Returns all meters that the user has access to.
	 *
	 * <p>
	 * See https://api.discovergy.com/docs/ for details.
	 *
	 * @return the Meters as a JsonArray.
	 * @throws OpenemsNamedException on error
	 * @throws InterruptedException  on error
	 */
	public JsonObject getData() throws OpenemsNamedException, InterruptedException {
		return JsonUtils.getAsJsonObject(this.sendPostRequest(this.apiKey));
	}

	/**
	 * Sends a get request to the Linkray API.
	 *
	 * @param apiKey the API key
	 * @return JsonElement the post request result
	 * @throws OpenemsNamedException on error
	 * @throws InterruptedException on error
	 */

	private JsonElement sendPostRequest(String apiKey) throws OpenemsNamedException, InterruptedException {

		var properties = System.getProperties();
		properties.setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());


		this.httpClient = HttpClient.newBuilder()
				.sslContext(insecureContext())
				.build();

		// form parameters
		Map<Object, Object> data = new HashMap<>();
		data.put("key", apiKey);


		HttpRequest request = HttpRequest.newBuilder().POST(buildFormDataFromMap(data)).uri(URI.create(this.url + "/Endpoints/ReadLoadBalancing/")).setHeader("User-Agent", "Java 11 HttpClient Bot") // add request header
						.header("Content-Type", "application/x-www-form-urlencoded").build();
				
		HttpResponse<String> response; 
		

		try {
			 response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());


		} catch (IOException e) {
			throw new OpenemsException(
					"Unable to read from Linkray API. " + e.getClass().getSimpleName() + ": " + e.getMessage());
		}

		return JsonUtils.parse(response.body().toString());
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
		return HttpRequest.BodyPublishers.ofString(builder.toString());
	}

}







