package timsterzel.de.soundpusher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.TextInputLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by tim on 13.03.16.
 */
public class DialogFragmentNewRecordEntry extends DialogFragment {

    private static final String TAG = DialogFragmentNewRecordEntry.class.getSimpleName();

    public static final String TAG_SHOWN = "DialogFragmentNewRecordEntry";


    private ImageView m_imageViewBackgroundImage;
    private TextView m_txtRecordTime;
    private RelativeLayout m_layoutMediaButtonBar;
    private MediaButton m_btnRecord;
    private MediaButton m_btnPlay;
    private MediaButton m_btnSave;
    private TextInputLayout m_edTxtLayRecordName;
    private EditText m_edTxtRecordName;

    private MediaHandler m_mediaHandler;


    // The dialog has to states, the record mode (where the user can record sounds) and a mode where the user can
    // give the sound a name and save it
    private boolean m_inRecordMode;

    private AlertDialog m_alertDialog;

    private DataHandlerDB m_dataHandlerDB;

    private Thread m_threadCntTime;
    private boolean m_threadRunning;
    private int m_recordTime;

    public interface OnNewRecordEntryCreatedListener {
        void onNewRecordEntryCreated(SoundEntry soundEntry);
    }

    private OnNewRecordEntryCreatedListener m_listener;


    public static DialogFragmentNewRecordEntry newInstance() {
        DialogFragmentNewRecordEntry fragment = new DialogFragmentNewRecordEntry();
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        LayoutInflater inflater = LayoutInflater.from(getActivity());
        View view = inflater.inflate(R.layout.dialog_fragment_new_record_entry, null);

        m_dataHandlerDB = new DataHandlerDB(getActivity());

        final AlertDialog.Builder builder = new AlertDialog.Builder(getActivity()/*, R.style.RecordDialogTheme*/);
        //builder.setTitle("Record");
        builder.setNegativeButton(getString(android.R.string.cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.setPositiveButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Save entry
                String name = m_edTxtRecordName.getText().toString();
                if (name.equals("")) {
                    // if user input no name, give sound a standard name
                    name = getString(R.string.txt_newSoundNameDummy);
                }
                String soundPath = m_mediaHandler.saveRecordPermanent(name);
                if (soundPath != null) {
                    SoundEntry soundEntry = new SoundEntry(0, soundPath, false, null, name, true);
                    m_dataHandlerDB.addSoundEntry(soundEntry);
                    m_listener.onNewRecordEntryCreated(soundEntry);
                }
            }
        });
        builder.setView(view);
        m_alertDialog = builder.create();

        m_mediaHandler = new MediaHandler(getActivity());
        /*m_mediaHandler.setOnPlayingCompleteListener(new MediaHandler.OnPlayingComplete() {
            @Override
            public void onPlayingComplete() {
                // If playback is completed, user can replay the sound or record a new one
                m_btnRecord.setEnabled(true);
                m_btnPlay.setActive(false);
            }
        });
        */

        m_imageViewBackgroundImage = (ImageView) view.findViewById(R.id.imageViewBackgroundImage);
        m_txtRecordTime = (TextView) view.findViewById(R.id.txtRecordTime);
        m_layoutMediaButtonBar = (RelativeLayout) view.findViewById(R.id.layoutMediaButtonBar);
        m_btnRecord = (MediaButton) view.findViewById(R.id.btnRecord);
        m_btnPlay = (MediaButton) view.findViewById(R.id.btnPlay);
        m_btnSave = (MediaButton) view.findViewById(R.id.btnSave);

        m_edTxtLayRecordName = (TextInputLayout) view.findViewById(R.id.edTxtLayRecordName);
        m_edTxtRecordName = (EditText) view.findViewById(R.id.edTxtRecordName);

        m_btnRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_mediaHandler.hasMicro()) {
                    onRecord(!m_mediaHandler.isRecording());
                    if (m_mediaHandler.isRecording()) {
                        m_btnRecord.setActive(true);
                    }
                    else {
                        m_btnRecord.setActive(false);
                    }
                }
                else {
                    // IMPORTANT: ADD DIALOG WHICH INFORM USER
                    Log.e(TAG, "No microphone detected");
                }

            }
        });

        m_btnPlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPlay(!m_mediaHandler.isPlaying());
                if (m_mediaHandler.isPlaying()) {
                    m_btnPlay.setActive(true);
                } else {
                    m_btnPlay.setActive(false);
                }
            }
        });

        m_btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_layoutMediaButtonBar.setVisibility(View.GONE);
                m_edTxtLayRecordName.setVisibility(View.VISIBLE);
                m_inRecordMode = false;
                // Now we can show the positive button so user can save the audio
                m_alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                //m_alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);

                //m_edTxtRecordName.setVisibility(View.VISIBLE);
            }
        });
        // User can only save or play record if something was recorded
        m_btnSave.setEnabled(false);
        m_btnPlay.setEnabled(false);

        m_inRecordMode = true;
        return m_alertDialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        // The positive button save the recorded audio ,so it only should be shown after a audio was
        // recorded
        if (m_inRecordMode) {
            m_alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            m_listener = (OnNewRecordEntryCreatedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnNewRecordEntryCreatedListener");
        }
    }

    private void onRecord(boolean start) {
        if (start) {
            m_mediaHandler.startRecording();
            // if something is recorded user cant play something
            m_btnPlay.setEnabled(false);
            // User can now save record while it is recording
            m_btnSave.setEnabled(false);


            m_imageViewBackgroundImage.setImageResource(R.drawable.ic_mic_dialog_bg_red);
            // Show the recording time
            m_txtRecordTime.setVisibility(View.VISIBLE);
            // Set the recording time to 00:00
            m_txtRecordTime.setText(getString(R.string.txt_recordTime, 0, 0, 0, 0));
            // Start counting and showing recording time
            initCountThread();
            m_threadCntTime.start();
        }
        else {
            m_threadRunning = false;
            m_mediaHandler.stopRecording();
            // If recording is finished, user can play sound
            m_btnPlay.setEnabled(true);
            // User can save the sound now
            m_btnSave.setEnabled(true);
            m_imageViewBackgroundImage.setVisibility(View.VISIBLE);
            m_imageViewBackgroundImage.setImageResource(R.drawable.ic_mic_dialog_bg);
            m_btnRecord.setActive(false);
        }
    }

    private void onPlay(boolean start) {
        if (start) {
            m_mediaHandler.startPlaying(m_mediaHandler.getTmpFilePath(), new MediaHandler.OnPlayingComplete() {
                @Override
                public void onPlayingComplete() {
                    // If playback is completed, user can replay the sound or record a new one
                    m_btnRecord.setEnabled(true);
                    m_btnPlay.setActive(false);
                }
            });
            // If something is played, user can not record something
            m_btnRecord.setEnabled(false);
        }
        else {
            m_mediaHandler.stopPlaying();
            // If playback is completed, user can replay the sound or record a new one
            m_btnRecord.setEnabled(true);
            m_btnPlay.setActive(false);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (m_mediaHandler.isPlaying()) {
            onPlay(false);
        }
        else if (m_mediaHandler.isRecording()) {
            onRecord(false);
        }
        Log.d(TAG, "TEST onPause");
    }

    void initCountThread() {
        m_threadCntTime = new Thread(new Runnable() {
            @Override
            public void run() {
                m_recordTime = 0;
                m_threadRunning = true;
                // Timer for counting the seconds of recording
                long startTimeSec = System.currentTimeMillis();
                // Timer for determine when record image change (from visible to invisible)
                long startTimeBg = System.currentTimeMillis();
                while (m_threadRunning) {
                    long elapsedTime = System.currentTimeMillis() - startTimeSec;
                    // Update time every 1 Second
                    if (elapsedTime >= 1000) {
                        m_recordTime += 1;
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                int seconds = m_recordTime % 60;
                                int minutes = m_recordTime / 60;

                                int secondsRight = seconds % 10;
                                int secondsLeft = seconds / 10;
                                int minutesRight = minutes % 10;
                                int minutesLeft = minutes / 10;
                                // Show the elapsed tim in Format mm:ss
                                m_txtRecordTime.setText(getString(R.string.txt_recordTime, minutesLeft, minutesRight, secondsLeft, secondsRight));
                            }
                        });
                        startTimeSec = System.currentTimeMillis();
                    }
                    elapsedTime = System.currentTimeMillis() - startTimeBg;
                    if (elapsedTime >= 800) {
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (m_imageViewBackgroundImage.getVisibility() == View.VISIBLE) {
                                    m_imageViewBackgroundImage.setVisibility(View.INVISIBLE);
                                }
                                else {
                                    m_imageViewBackgroundImage.setVisibility(View.VISIBLE);
                                }
                            }
                        });
                        startTimeBg = System.currentTimeMillis();
                    }
                }
            }
        });
    }


}
