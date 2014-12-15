package com.bigbug.app.msdmobile;

import android.text.TextUtils;
import android.util.Log;
import android.widget.BaseAdapter;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.VolleyLog;
import com.android.volley.toolbox.JsonArrayRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by bigbug on 12/11/14.
 */
public class RecordsRequester {

    public static final String TAG = RecordsRequester.class.getSimpleName();

    /**
     * Request records with the query string, get the json array and parse the it
     * into records associated with the input adapter. When the request is over,
     * notify the view bound to the adapter if possible.
     *
     * @param query     The query string.
     * @param records   The records list, must be non-null.
     * @param adapter   The adapter consume the records.
     */
    public static void request(String query, final Records<?> records, final BaseAdapter adapter,
                               final OnParsingRecordListener listener) {

        // make json array request where response starts with [
        JsonArrayRequest request = new JsonArrayRequest(query,
            new Response.Listener<JSONArray>() {
                @Override
                public void onResponse(JSONArray response) {
                    Log.d(TAG, response.toString());

                    try {
                        // Clear the old records
                        records.clear();

                        // Parsing json array response
                        // loop through each json object
                        for (int i = 0; i < response.length(); i++) {
                            final Object record;
                            if (listener != null) {
                                record = listener.onParsingRecord((JSONObject) response.get(i));
                            } else {
                                record = response.get(i);
                            }

                            if (record != null) {
                                ((Records<Object>) records).add(record);
                            }
                        }

                        // Update UI
                        adapter.notifyDataSetChanged();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    VolleyLog.d(TAG, "Error: " + error.getMessage());
                }
            }
        );

        // Adding request to request queue
        MSDApplication.getInstance().addToRequestQueue(request);
    }

    public interface OnParsingRecordListener {
        public Object onParsingRecord(JSONObject record);
    }
}