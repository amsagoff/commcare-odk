/*
 * Copyright (C) 2009 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore.Video;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
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
 * Widget that allows user to take pictures, sounds or video and add them to the form.
 * 
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class VideoWidget extends QuestionWidget implements IBinaryWidget {
    private final static String t = "MediaWidget";

    private final Button mCaptureButton;
    private final Button mPlayButton;
    private final Button mChooseButton;

    private String mBinaryName;

    private final String mInstanceFolder;

    private boolean mWaitingForData;

    public VideoWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        mWaitingForData = false;
        mInstanceFolder =
                FormEntryActivity.mInstancePath.substring(0,
                        FormEntryActivity.mInstancePath.lastIndexOf("/") + 1);

        setOrientation(LinearLayout.VERTICAL);

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);
        // setup capture button
        mCaptureButton = new Button(getContext());
        WidgetUtils.setupButton(mCaptureButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.capture_video),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        mCaptureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE);
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                        Video.Media.EXTERNAL_CONTENT_URI.toString());
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.VIDEO_CAPTURE);
                    mWaitingForData = true;
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "capture video"),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup capture button
        mChooseButton = new Button(getContext());
        WidgetUtils.setupButton(mChooseButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.choose_video),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // launch capture intent on click
        mChooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("video/*");
                // Intent i =
                // new Intent(Intent.ACTION_PICK,
                // android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
                mWaitingForData = true;
                try {
                    ((Activity)getContext()).startActivityForResult(i,
                            FormEntryActivity.VIDEO_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "choose video "),
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // setup play button
        mPlayButton = new Button(getContext());
        WidgetUtils.setupButton(mPlayButton,
                StringUtils.getStringSpannableRobust(getContext(), R.string.play_video),
                mAnswerFontsize,
                !prompt.isReadOnly());

        // on play, launch the appropriate viewer
        mPlayButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent("android.intent.action.VIEW");
                File f = new File(mInstanceFolder + "/" + mBinaryName);
                i.setDataAndType(Uri.fromFile(f), "video/*");
                try {
                    getContext().startActivity(i);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(getContext(),
                            StringUtils.getStringSpannableRobust(getContext(),
                                    R.string.activity_not_found, "video video"),
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
            Log.e(t, "Failed to delete " + f);
        }

        // clean up variables
        mBinaryName = null;
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#clearAnswer()
     */
    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();

        // reset buttons
        mPlayButton.setEnabled(false);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#getAnswer()
     */
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
        // you are replacing an answer. remove the media.
        if (mBinaryName != null) {
            deleteMedia();
        }

        // get the file path and create a copy in the instance folder
        String binaryPath = UriToFilePath.getPathFromUri(CommCareApplication._(),
                (Uri)binaryuri);
        String extension = binaryPath.substring(binaryPath.lastIndexOf("."));
        String destVideoPath = mInstanceFolder + "/" + System.currentTimeMillis() + extension;

        File source = new File(binaryPath);
        File newVideo = new File(destVideoPath);
        FileUtils.copyFile(source, newVideo);
        
        checkFileSize(newVideo);

        if (newVideo.exists()) {
            // Add the copy to the content provier
            ContentValues values = new ContentValues(6);
            values.put(Video.Media.TITLE, newVideo.getName());
            values.put(Video.Media.DISPLAY_NAME, newVideo.getName());
            values.put(Video.Media.DATE_ADDED, System.currentTimeMillis());
            values.put(Video.Media.DATA, newVideo.getAbsolutePath());

            Uri VideoURI =
                getContext().getContentResolver().insert(Video.Media.EXTERNAL_CONTENT_URI, values);
            Log.i(t, "Inserting VIDEO returned uri = " + VideoURI.toString());
        } else {
            Log.e(t, "Inserting Video file FAILED");
        }

        mBinaryName = newVideo.getName();
        mWaitingForData = false;
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setFocus(android.content.Context)
     */
    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
            (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.IBinaryWidget#isWaitingForBinaryData()
     */
    @Override
    public boolean isWaitingForBinaryData() {
        return mWaitingForData;
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#setOnLongClickListener(android.view.View.OnLongClickListener)
     */
    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        mCaptureButton.setOnLongClickListener(l);
        mChooseButton.setOnLongClickListener(l);
        mPlayButton.setOnLongClickListener(l);
    }


    /*
     * (non-Javadoc)
     * @see org.odk.collect.android.widgets.QuestionWidget#cancelLongPress()
     */
    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        mCaptureButton.cancelLongPress();
        mChooseButton.cancelLongPress();
        mPlayButton.cancelLongPress();
    }

}
