package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.commcare.android.util.StringUtils;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.dalvik.utils.UriToFilePath;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.utilities.FileUtils;

import java.io.File;

/**
 * Widget that allows user to take pictures, sounds or video and add them to
 * the form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */

public class AudioWidget extends QuestionWidget implements IBinaryWidget {
    private final static String t = "MediaWidget";

    private final Button mCaptureButton;
    private final Button mPlayButton;
    private final Button mChooseButton;

    private String mBinaryName;
    private final String mInstanceFolder;

    private boolean mWaitingForData;

    public AudioWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        mWaitingForData = false;
        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_audio),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.MediaStore.Audio.Media.RECORD_SOUND_ACTION);
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString());
                mWaitingForData = true;
                try {
                    ((Activity)getContext())
                            .startActivityForResult(i, FormEntryActivity.AUDIO_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "audio capture"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup capture button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_sound),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("audio/*");
                mWaitingForData = true;
                try {
                    ((Activity)getContext())
                            .startActivityForResult(i, FormEntryActivity.AUDIO_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "choose audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_audio),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("android.intent.action.VIEW");
                File f = new File(mInstanceFolder + "/" + mBinaryName);
                i.setDataAndType(Uri.fromFile(f), "audio/*");
                try {
                    ((Activity)getContext()).startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found,
                                    "play audio"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // retrieve answer from data model and update ui
        mBinaryName = prompt.getAnswerText();
        if (mBinaryName != null) {
            mPlayButton.setEnabled(true);
            File f = new File(mInstanceFolder + "/" + mBinaryName);

            checkFileSize(f);
        } else {
            mPlayButton.setEnabled(false);
        }

        // finish complex layout
        addView(mCaptureButton);
        addView(mChooseButton);
        String acq = prompt.getAppearanceHint();
        if ((QuestionWidget.ACQUIREFIELD.equalsIgnoreCase(acq))) {
            mChooseButton.setVisibility(View.GONE);
        }
        addView(mPlayButton);
    }

    private void deleteMedia() {
        // get the file path and delete the file
        File f = new File(mInstanceFolder + "/" + mBinaryName);
        if (!f.delete()) {
            Log.i(t, "Failed to delete " + f);
        }

        // clean up variables
        mBinaryName = null;
    }

    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();

        // reset buttons
        mPlayButton.setEnabled(false);
    }

    @Override
    public IAnswerData getAnswer() {
        if (mBinaryName != null) {
            return new StringData(mBinaryName);
        } else {
            return null;
        }
    }

    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.IBinaryWidget#setBinaryData(java.lang.Object)
     */
    @Override
    public void setBinaryData(Object binaryuri) {
        // when replacing an answer. remove the current media.
        if (mBinaryName != null) {
            deleteMedia();
        }

        // get the file path and create a copy in the instance folder
        String binaryPath = UriToFilePath.getPathFromUri(CommCareApplication._(),
                (Uri)binaryuri);
        String extension = binaryPath.substring(binaryPath.lastIndexOf("."));
        String destAudioPath = mInstanceFolder + "/" + System.currentTimeMillis() + extension;

        File source = new File(binaryPath);
        File newAudio = new File(destAudioPath);
        FileUtils.copyFile(source, newAudio);

        checkFileSize(newAudio);

        if (newAudio.exists()) {
            // Add the copy to the content provier
            ContentValues values = new ContentValues(6);
            values.put(Audio.Media.TITLE, newAudio.getName());
            values.put(Audio.Media.DISPLAY_NAME, newAudio.getName());
            values.put(Audio.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Audio.Media.DATA, newAudio.getAbsolutePath());

            Uri AudioURI =
                    getContext().getContentResolver().insert(Audio.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(t, "Inserting AUDIO returned uri = " + AudioURI.toString());
        } else {
            Log.e(t, "Inserting Audio file FAILED");
        }

        mBinaryName = newAudio.getName();
        mWaitingForData = false;
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public boolean isWaitingForBinaryData() {
        return mWaitingForData;
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        mPlayButton.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        mPlayButton.cancelLongPress();
    }
}
