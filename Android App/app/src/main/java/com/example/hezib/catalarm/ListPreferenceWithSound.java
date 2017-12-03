package com.example.hezib.catalarm;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.media.AudioManager;
import android.media.SoundPool;
import android.preference.ListPreference;
import android.support.v7.app.AppCompatActivity;
import android.util.AttributeSet;
import android.util.SparseBooleanArray;

import static android.content.Context.AUDIO_SERVICE;

/**
 * Created by hezib on 21/11/2017.
 */

public class ListPreferenceWithSound extends ListPreference {

    private Context context;
    private int mClickedDialogEntryIndex;
    private SoundPool soundPool;
    private int[] soundID = new int[5];
    private SparseBooleanArray loaded = new SparseBooleanArray();

    private void loadSoundPool(AppCompatActivity context) {
        context.setVolumeControlStream(AudioManager.STREAM_MUSIC);
        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        soundPool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
            @Override
            public void onLoadComplete(SoundPool soundPool, int sampleId, int status) {
                if (status == 0)
                    loaded.put(sampleId, true);
            }
        });
        soundID[0] = soundPool.load(context, R.raw.airhorn, 1);
        soundID[1] = soundPool.load(context, R.raw.foghorn, 1);
        soundID[2] = soundPool.load(context, R.raw.woop, 1);
        soundID[3] = soundPool.load(context, R.raw.rooster, 1);
        soundID[4] = soundPool.load(context, R.raw.dogbark, 1);
    }

    public ListPreferenceWithSound(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
        if(context instanceof AppCompatActivity) {
            loadSoundPool((AppCompatActivity)context);
        }
    }

    public ListPreferenceWithSound(Context context) {
        this(context, null);
    }

    private int getValueIndex() {
        return findIndexOfValue(this.getValue() + "");
    }


    @Override
    protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {
        super.onPrepareDialogBuilder(builder);

        mClickedDialogEntryIndex = getValueIndex();
        builder.setSingleChoiceItems(this.getEntries(), mClickedDialogEntryIndex, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                mClickedDialogEntryIndex = which;
                AudioManager audioManager = (AudioManager) context.getSystemService(AUDIO_SERVICE);
                if (audioManager != null) {
                    float actualVolume = (float) audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                    float maxVolume = (float) audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                    float volume = actualVolume / maxVolume;
                    if (loaded.get(soundID[0])) {
                        soundPool.play(soundID[which], volume, volume, 1, 0, 1f);
                    }
                }
            }
        });
        builder.setPositiveButton(context.getString(R.string.ok), this);
        builder.setNegativeButton(context.getString(android.R.string.cancel), this);
    }

    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            this.setValue(this.getEntryValues()[mClickedDialogEntryIndex] + "");
        }
    }

}
