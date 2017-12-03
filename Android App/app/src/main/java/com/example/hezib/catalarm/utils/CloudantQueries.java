package com.example.hezib.catalarm.utils;

import com.example.hezib.catalarm.DatabaseDocument;

/**
 * Created by hezib on 21/11/2017.
 */

public class CloudantQueries {

    private static final String TAG = CloudantQueries.class.getName();
    private static final String GLOBAL_QUERY = "_all_docs";
    private static final String DBNAME = "catalarm";
    private static final String USERNAME = "<Your-Cloudant-Username>";
    private static final String CRED_ENCODED = "<Your-Credentials-Encoded>"; // base 64 encoding of your "username:password"
    private static final String HOSTNAME = "cloudant.com";
    private static final String DBURL = "https://" + USERNAME + "." + HOSTNAME + "/" + DBNAME;
    private static final String ORDER_DESCENDING = "descending=true";

    private CloudantQueriesEvents events;

    public CloudantQueries(CloudantQueriesEvents events) { this.events = events; }

    public interface CloudantQueriesEvents {

        void onError(String errorMessage);

        void onGetAllResponse(String response);

        void onGetDocResponse(String response);

        void onDeleteResponse(String response);
    }

    public void getAllFromDatabase() {
        String url = DBURL + "/" + GLOBAL_QUERY + "/?" + ORDER_DESCENDING;
        AsyncHttpURLConnection connection = new AsyncHttpURLConnection("GET", url, null, new AsyncHttpURLConnection.AsyncHttpEvents() {

            @Override
            public void onHttpError(String errorMessage) {
                events.onError(errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                events.onGetAllResponse(response);
            }
        });
        connection.setAuthentication(CRED_ENCODED);
        connection.send();
    }

    public void getDocFromDatabase(DatabaseDocument document) {
        String url = DBURL + "/" + document.id;
        AsyncHttpURLConnection connection = new AsyncHttpURLConnection("GET", url, null, new AsyncHttpURLConnection.AsyncHttpEvents() {

            @Override
            public void onHttpError(String errorMessage) {
                events.onError(errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                events.onGetDocResponse(response);
            }
        });
        connection.setAuthentication(CRED_ENCODED);
        connection.send();
    }

    public void deleteFromDatabase(DatabaseDocument doc) {
        String url = DBURL + "/" + doc.id + "/?rev=" + doc.rev;
        AsyncHttpURLConnection connection = new AsyncHttpURLConnection("DELETE", url, null, new AsyncHttpURLConnection.AsyncHttpEvents() {

            @Override
            public void onHttpError(String errorMessage) {
                events.onError(errorMessage);
            }

            @Override
            public void onHttpComplete(String response) {
                events.onDeleteResponse(response);
            }
        });
        connection.setAuthentication(CRED_ENCODED);
        connection.send();
    }
}
