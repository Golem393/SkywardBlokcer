package com.example.skywardblocker;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MdmApiClient {

    private static final String TAG = "SkywardDebug";
    private static final String BASE_URL = "https://mdm-backend-i4b0.onrender.com/api";

    public interface MdmCallback {
        void onSuccess(String memberId);
        void onError(String errorMessage);
    }

    public static void finalizeDeviceSetup(String targetSerialNumber, MdmCallback callback) {
        new Thread(() -> {
            try {
                Log.d(TAG, "Starting finalization workflow for Target Serial: [" + targetSerialNumber + "]");

                // ==========================================
                // CALL 1: Get all devices from Kiosk Group
                // ==========================================
                URL getUrl = new URL(BASE_URL + "/kiosk-devices");
                Log.d(TAG, "CALL 1 Send -> GET " + getUrl);

                HttpURLConnection getConn = (HttpURLConnection) getUrl.openConnection();
                getConn.setConnectTimeout(15000);
                getConn.setReadTimeout(15000);
                getConn.setRequestMethod("GET");
                getConn.setRequestProperty("Accept", "application/json");

                int getResponseCode = getConn.getResponseCode();
                Log.d(TAG, "CALL 1 Response Status Code: " + getResponseCode);

                if (getResponseCode != 200) {
                    callback.onError("Failed to fetch kiosk devices. Code: " + getResponseCode);
                    return;
                }

                BufferedReader in = new BufferedReader(new InputStreamReader(getConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                String rawResponseString = response.toString();
                // Logs the entire raw JSON payload returned from your backend node proxy
                Log.d(TAG, "CALL 1 Raw JSON Output:\n" + rawResponseString);

                // ==========================================
                // PARSE: Find matching device_id by Serial
                // ==========================================
                JSONObject jsonResponse = new JSONObject(rawResponseString);
                JSONArray devices = jsonResponse.optJSONArray("devices");

                String memberId = null;

                if (devices != null) {
                    Log.d(TAG, "Parsing array of " + devices.length() + " managed device(s)...");
                    for (int i = 0; i < devices.length(); i++) {
                        JSONObject device = devices.getJSONObject(i);
                        String currentSerial = device.optString("serial_number");
                        String deviceName = device.optString("device_name");

                        Log.d(TAG, String.format("Checking index %d: Name=[%s], Serial=[%s]", i, deviceName, currentSerial));

                        if (targetSerialNumber != null && targetSerialNumber.equalsIgnoreCase(currentSerial)) {
                            memberId = device.optString("device_id");
                            Log.i(TAG, "Match Found! Device Name: [" + deviceName + "] mapped to MemberID: [" + memberId + "]");
                            break;
                        }
                    }
                } else {
                    Log.w(TAG, "No 'devices' key or array found inside the JSON object root structure.");
                }

                if (memberId == null || memberId.isEmpty()) {
                    String err = "Device target serial " + targetSerialNumber + " not matched inside the Kiosk group list.";
                    Log.e(TAG, err);
                    callback.onError(err);
                    return;
                }

                // ==========================================
                // CALL 2: Move device to Official Group
                // ==========================================
                URL putUrl = new URL(BASE_URL + "/move-member-to-official");
                String jsonInputString = "{\"memberId\": \"" + memberId + "\"}";

                Log.d(TAG, "CALL 2 Send -> PUT " + putUrl);
                Log.d(TAG, "CALL 2 Payload Body: " + jsonInputString);

                HttpURLConnection putConn = (HttpURLConnection) putUrl.openConnection();
                putConn.setConnectTimeout(15000);
                putConn.setReadTimeout(15000);
                putConn.setRequestMethod("PUT");
                putConn.setRequestProperty("Content-Type", "application/json");
                putConn.setDoOutput(true);

                try (OutputStream os = putConn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int putResponseCode = putConn.getResponseCode();
                Log.d(TAG, "CALL 2 Response Status Code: " + putResponseCode);

                if (putResponseCode == 200) {
                    Log.i(TAG, "Successfully execution completed on Node proxy backend Server.");
                    callback.onSuccess(memberId);
                } else {
                    // Try to catch error body stream info if available
                    BufferedReader errReader = new BufferedReader(new InputStreamReader(
                            putResponseCode >= 400 ? putConn.getErrorStream() : putConn.getInputStream()
                    ));
                    StringBuilder errResponse = new StringBuilder();
                    while ((line = errReader.readLine()) != null) {
                        errResponse.append(line);
                    }
                    errReader.close();

                    Log.e(TAG, "CALL 2 Failed Output: " + errResponse.toString());
                    callback.onError("Failed to switch groups. HTTP Code: " + putResponseCode);
                }

            } catch (Exception e) {
                Log.e(TAG, "Exception caught during API Execution Pipeline chain", e);
                callback.onError(e.getMessage());
            }
        }).start();
    }

    // Completely replace your old moveDeviceToKiosk with this stripped-down version
    public static void moveDeviceToKiosk(String memberId, MdmCallback callback) {
        if (memberId == null) {
            callback.onError("No cached memberId found. Cannot rollback.");
            return;
        }

        new Thread(() -> {
            try {
                URL putUrl = new URL(BASE_URL + "/move-member-to-kiosk");
                String jsonInputString = "{\"memberId\": \"" + memberId + "\"}";

                HttpURLConnection putConn = (HttpURLConnection) putUrl.openConnection();
                putConn.setConnectTimeout(30000);
                putConn.setReadTimeout(30000);
                putConn.setRequestMethod("PUT");
                putConn.setRequestProperty("Content-Type", "application/json");
                putConn.setDoOutput(true);

                try (OutputStream os = putConn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int putResponseCode = putConn.getResponseCode();
                if (putResponseCode == 200) {
                    callback.onSuccess(memberId);
                } else {
                    callback.onError("Failed to revert group. HTTP Code: " + putResponseCode);
                }
            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        }).start();
    }

}