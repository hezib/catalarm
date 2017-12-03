package com.example.hezib.catalarm;

import android.util.SparseIntArray;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by hezib on 22/11/2017.
 */

public class Statistics {

    private static final String TAG = Statistics.class.getName();

    private List<CatEvent> eventList = new ArrayList<>();
    private int differentDays = -1;
    private int totalTimeSpent = -1;
    private SparseIntArray mostActiveDay;
    private SparseIntArray mostActiveHour;

    class CatEvent {

        int date;
        int time;
        int timeSpent;
        DatabaseDocument pointer;

        CatEvent(int date, int time, int timeSpent, DatabaseDocument doc) {
            this.date = date;
            this.time = time;
            this.timeSpent = timeSpent;
            this.pointer = doc;
        }

        void setTimeSpent(int timeSpent) {
            this.timeSpent = timeSpent;
        }
    }

    public Statistics(List<DatabaseDocument> docsList, int maxTimePerEvent) {
        if (docsList.size() == 0) {
            return;
        }
        mostActiveDay = new SparseIntArray(docsList.size());
        mostActiveHour = new SparseIntArray(docsList.size());
        DatabaseDocument currentDoc = docsList.get(0);
        int differentDays = 1;
        int currentDate = currentDoc.getDate();
        int currentTime = currentDoc.getTime();
        int timeSpent = 0;
        CatEvent currentEvent = new CatEvent(currentDate, currentTime, timeSpent, currentDoc);
        int totalTimeSpent = 0;

        for (int i = 1; i < docsList.size(); i++) {
            currentDoc = docsList.get(i);
            if (currentDoc.getDate() == currentDate) {
                int timeDiff = Math.abs(currentDoc.getTime() - currentTime);
                if (timeDiff < maxTimePerEvent) {
                    timeSpent += timeDiff;
                    currentTime -= timeDiff;
                    totalTimeSpent += timeDiff;
                } else {
                    currentEvent.setTimeSpent(timeSpent);
                    eventList.add(currentEvent);
                    int dayCount = mostActiveDay.get(currentDate, -1);
                    mostActiveDay.put(currentDate, dayCount > 0 ? dayCount + 1: 1);
                    int hour = Integer.parseInt(currentDoc.hour);
                    int hourCount = mostActiveHour.get(hour, -1);
                    mostActiveHour.put(hour, hourCount > 0 ? hourCount + 1 : 1);
                    currentTime = currentDoc.getTime();
                    timeSpent = 0;
                    currentEvent = new CatEvent(currentDate, currentTime, timeSpent, currentDoc);
                }
            } else {
                differentDays++;
                currentEvent.setTimeSpent(timeSpent);
                eventList.add(currentEvent);
                int dayCount = mostActiveDay.get(currentDate, -1);
                mostActiveDay.put(currentDate, dayCount > 0 ? dayCount + 1: 1);
                int hour = Integer.parseInt(currentDoc.hour);
                int hourCount = mostActiveHour.get(hour, -1);
                mostActiveHour.put(hour, hourCount > 0 ? hourCount + 1 : 1);
                currentDate = currentDoc.getDate();
                currentTime = currentDoc.getTime();
                timeSpent = 0;
                currentEvent = new CatEvent(currentDate, currentTime, timeSpent, currentDoc);
            }
        }
        currentEvent.setTimeSpent(timeSpent);
        eventList.add(currentEvent);
        int dayCount = mostActiveDay.get(currentDate, -1);
        mostActiveDay.put(currentDate, dayCount > 0 ? dayCount + 1: 1);
        int hour = Integer.parseInt(currentDoc.hour);
        int hourCount = mostActiveHour.get(hour, -1);
        mostActiveHour.put(hour, hourCount > 0 ? hourCount + 1 : 1);
        this.differentDays = differentDays;
        this.totalTimeSpent = totalTimeSpent;
    }

    public String getEventsTotal() {
        return String.valueOf(eventList.size());
    }

    public String getEventsPerDay() {
        return String.valueOf((float) eventList.size() / differentDays);
    }

    public String getTimeSpentPerEvent() {
        float timeSpentPerEvent = (float) totalTimeSpent / eventList.size();
        return String.format("%.02f", timeSpentPerEvent);
    }

    public String getMostActiveHour() {
        String res = null;
        if (mostActiveHour.size() == 0) return res;
        int resultKey = mostActiveHour.keyAt(0);
        int resultValue = mostActiveHour.valueAt(0);
        for(int i = 1 ; i < mostActiveHour.size() ; i++) {
            int currentKey = mostActiveHour.keyAt(i);
            int currentValue = mostActiveHour.valueAt(i);
            if(currentValue > resultValue) {
                resultKey = currentKey;
                resultValue = currentValue;
            }
        }
        return String.valueOf(resultKey) + ":00 - " + String.valueOf(resultKey + 1) + ":00";
    }

    public String getMostActiveDay() {
        String res = null;
        if (mostActiveDay.size() == 0) return res;
        int resultKey = mostActiveDay.keyAt(0);
        int resultValue = mostActiveDay.valueAt(0);
        for(int i = 1 ; i < mostActiveDay.size() ; i++) {
            int currentKey = mostActiveDay.keyAt(i);
            int currentValue = mostActiveDay.valueAt(i);
            if(currentValue > resultValue) {
                resultKey = currentKey;
                resultValue = currentValue;
            }
        }
        for (CatEvent event: eventList) {
            if(event.date == resultKey) {
                DatabaseDocument doc = event.pointer;
                String date = doc.day + "/" + doc.month + "/" + doc.year;
                res = date + " with " + resultValue;
            }
        }
        return res;
    }

}
