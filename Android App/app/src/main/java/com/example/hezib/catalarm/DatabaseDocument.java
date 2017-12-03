package com.example.hezib.catalarm;

import android.os.Parcel;
import android.os.Parcelable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by hezib on 18/11/2017.
 */

public class DatabaseDocument implements Parcelable{

    public String id;
    public String rev;

    public String year;
    public String month;
    public String day;
    public String hour;
    public String min;
    public String sec;

    public DatabaseDocument(String id, String rev) {
        this.id = id;
        this.rev = rev;
        parseID();
    }

    public DatabaseDocument(Parcel in){
        String[] data = new String[8];

        in.readStringArray(data);
        // the order needs to be the same as in writeToParcel() method
        this.id = data[0];
        this.rev = data[1];

        this.year = data[2];
        this.month = data[3];
        this.day = data[4];
        this.hour = data[5];
        this.min = data[6];
        this.sec = data[7];
    }

    private void parseID() {
        String[] tokens = id.split("-");
        this.year = tokens[0];
        this.month = tokens[1];
        this.day = tokens[2];
        String time = tokens[3];
        tokens = time.split("_");
        this.hour = tokens[0];
        this.min = tokens[1];
        this.sec = tokens[2].split("\\.")[0];
    }

    public int getDate() {
        Integer res = null;
        try {
            res = Integer.parseInt(this.year + this.month + this.day);
        } catch (NumberFormatException e){
            e.printStackTrace();
            res = -1;
        }
        return res;
    }

    public int getTime() {
        Integer res = null;
        try {
            res = Integer.parseInt(this.hour + this.min + this.sec);
        } catch (NumberFormatException e){
            e.printStackTrace();
            res = -1;
        }
        return res;
    }

    public static List<DatabaseDocument> getListFromJson(String jsonString) {
        List<DatabaseDocument> list = new ArrayList<>();
        try {
            JSONObject json = new JSONObject(jsonString);
            final int totalDocs = json.getInt("total_rows");
            JSONArray arrayDocs = json.getJSONArray("rows");
            for(int i = 0 ; i < totalDocs ; i++) {
                JSONObject docJson = arrayDocs.getJSONObject(i);
                DatabaseDocument doc = new DatabaseDocument(docJson.getString("id"), docJson.getJSONObject("value").getString("rev"));
                list.add(doc);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return list;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(new String[] {this.id, this.rev, this.year, this.month, this.day, this.hour, this.min, this.sec});
    }

    public static final Parcelable.Creator CREATOR = new Parcelable.Creator() {
        public DatabaseDocument createFromParcel(Parcel in) {
            return new DatabaseDocument(in);
        }

        public DatabaseDocument[] newArray(int size) {
            return new DatabaseDocument[size];
        }
    };
}
