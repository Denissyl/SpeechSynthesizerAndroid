package com.speechsynthesizer.ui.recordSyllable;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.speechsynthesizer.DatabaseHelper;
import com.speechsynthesizer.R;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;


import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static com.speechsynthesizer.ui.synthesis.SynthesisFragment.splitIntoSyllables;

public class RecordSyllableFragment extends Fragment {

    Button buttonRecord, buttonListen, buttonStop, buttonCheckSyllables;
    EditText editTextRecord, editTextWordForSyllables;
    TextView textViewStatusRecord, textViewSyllablesOutput;


    private MediaPlayer mediaPlayer = new MediaPlayer();

    public static final int RequestPermissionCode = 1;

    private static final int RECORDER_BPP = 16;
    private static final String AUDIO_RECORDER_FILE_EXT_WAV = ".wav";
    private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";
    private static final String AUDIO_RECORDER_TEMP_FILE = "record_temp.raw";
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord recorder = null;
    private int bufferSize = 0;
    private Thread recordingThread = null;
    private boolean isRecording = false;

    String path1;
    private DatabaseHelper mDBHelper;
    private SQLiteDatabase mDb;


    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_recordsyllable, container, false);

        buttonRecord = (Button) root.findViewById(R.id.buttonRecord);
        buttonStop = (Button) root.findViewById(R.id.buttonStop);
        buttonListen = (Button) root.findViewById(R.id.buttonListen);
        buttonCheckSyllables = (Button) root.findViewById(R.id.buttonCheckSyllables);
        editTextRecord = (EditText) root.findViewById(R.id.editTextRecord);
        editTextWordForSyllables = (EditText) root.findViewById(R.id.editTextWordForSyllables);

        textViewStatusRecord = (TextView) root.findViewById(R.id.textViewStatusRecord);
        textViewSyllablesOutput = (TextView) root.findViewById(R.id.textViewSyllablesOutput);

        buttonRecord.setEnabled(false);
        buttonStop.setEnabled(false);
        buttonListen.setEnabled(false);
        buttonCheckSyllables.setEnabled(false);

        mDBHelper = new DatabaseHelper(getContext());

        try {
            mDBHelper.updateDataBase();
        } catch (IOException mIOException) {
            throw new Error("UnableToUpdateDatabase");
        }

        try {
            mDb = mDBHelper.getWritableDatabase();
        } catch (SQLException mSQLException) {
            throw mSQLException;
        }

        bufferSize = AudioRecord.getMinBufferSize(8000,
                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                AudioFormat.ENCODING_PCM_16BIT);


        editTextWordForSyllables.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                buttonCheckSyllables.setEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //check isAllFill and show button
                if(editTextWordForSyllables.getText().toString().trim().length() > 0){
                    buttonCheckSyllables.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });


        editTextRecord.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                buttonRecord.setEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //check isAllFill and show button
                if(editTextRecord.getText().toString().trim().length() > 0){
                    buttonRecord.setEnabled(true); //show view
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });


        buttonCheckSyllables.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String strEditTextWordForSyllables = editTextWordForSyllables.getText().toString().toLowerCase();
                List splited_text = splitIntoSyllables(strEditTextWordForSyllables);
                String result = String.join("-", splited_text);
                textViewSyllablesOutput.setText(result);
            }
        });


        buttonRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if(checkPermission()) {
                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                            RECORDER_SAMPLERATE, RECORDER_CHANNELS,RECORDER_AUDIO_ENCODING, bufferSize);

                    int i = recorder.getState();
                    if(i==1)
                        recorder.startRecording();

                    isRecording = true;

                    recordingThread = new Thread(new Runnable() {

                        @Override
                        public void run() {
                            writeAudioDataToFile();
                        }
                    },"AudioRecorder Thread");

                    recordingThread.start();

                    buttonRecord.setEnabled(false);
                    buttonStop.setEnabled(true);

                    Toast.makeText(getContext(), "Запись начата",
                            Toast.LENGTH_LONG).show();

                    textViewStatusRecord.setTextColor(Color.RED);
                    textViewStatusRecord.setText("Статус: Запись начата");
                } else {
                    requestPermission();
                }

            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(null != recorder){
                    isRecording = false;

                    int i = recorder.getState();
                    if(i==1)
                        recorder.stop();
                    recorder.release();

                    recorder = null;
                    recordingThread = null;
                }

                copyWaveFile(getTempFilename(),getFilename());
                deleteTempFile();
                soundToDataBase();

                buttonStop.setEnabled(false);
                buttonListen.setEnabled(true);
                buttonRecord.setEnabled(true);


                Toast.makeText(getContext(), "Запись завершена",
                        Toast.LENGTH_LONG).show();

                textViewStatusRecord.setTextColor(Color.GREEN);
                textViewStatusRecord.setText("Статус: Запись завершена");
            }
        });

        buttonListen.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                buttonStop.setEnabled(false);
                try {

                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    BufferedInputStream in = new BufferedInputStream(new FileInputStream(path1));

                    int read;
                    byte[] buff = new byte[1024];
                    while ((read = in.read(buff)) > 0)
                    {
                        out.write(buff, 0, read);
                    }
                    out.flush();
                    byte[] audioBytes = out.toByteArray();

                    File temp3Mp3 = File.createTempFile("temp3", "mp3", getContext().getCacheDir());
                    temp3Mp3.deleteOnExit();
                    FileOutputStream fos = new FileOutputStream(temp3Mp3);


                    fos.write(audioBytes);
                    fos.close();
                    in.close();

                    mediaPlayer.reset();

                    FileInputStream fis = new FileInputStream(temp3Mp3);

                    mediaPlayer.setDataSource(fis.getFD());
                    mediaPlayer.prepare();
                    mediaPlayer.start();
                } catch (IOException ex) {
                    String ser = ex.toString();
                    ex.printStackTrace();
                }
                Toast.makeText(getContext(), "Запись прослушивается",
                        Toast.LENGTH_LONG).show();

                textViewStatusRecord.setTextColor(Color.BLUE);
                textViewStatusRecord.setText("Статус: Запись прослушивается");
            }
        });
        return root;
    }



    private String getFilename(){
        String filepath = Environment.getDataDirectory().getPath();
        File file = new File(filepath, AUDIO_RECORDER_FOLDER);
        if(!file.exists()){
            file.mkdirs();
        }
        file.deleteOnExit();
        path1 = (getContext().getCacheDir() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
        return (getContext().getCacheDir() + "/" + System.currentTimeMillis() + AUDIO_RECORDER_FILE_EXT_WAV);
    }

    private String getTempFilename(){
        String filepath = Environment.getExternalStorageDirectory().getPath();
        File file = new File(filepath,AUDIO_RECORDER_FOLDER);

        if(!file.exists()){
            file.mkdirs();
        }

        File tempFile = new File(filepath,AUDIO_RECORDER_TEMP_FILE);
        file.deleteOnExit();
        if(tempFile.exists())
            tempFile.delete();

        return (file.getAbsolutePath() + "/" + AUDIO_RECORDER_TEMP_FILE);
    }


    private void writeAudioDataToFile(){
        byte data[] = new byte[bufferSize];
        String filename = getTempFilename();
        FileOutputStream os = null;
        try {
            os = new FileOutputStream(filename);
        } catch (FileNotFoundException e) {
// TODO Auto-generated catch block
            e.printStackTrace();
        }

        int read = 0;

        if(null != os){
            while(isRecording){
                read = recorder.read(data, 0, bufferSize);

                if(AudioRecord.ERROR_INVALID_OPERATION != read){
                    try {
                        os.write(data);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            try {
                os.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private void deleteTempFile() {
        File file = new File(getTempFilename());
        file.delete();
    }

    private void copyWaveFile(String inFilename,String outFilename){
        long totalAudioLen = 0;
        long totalDataLen = totalAudioLen + 36;
        long longSampleRate = RECORDER_SAMPLERATE;
        int channels = 2;
        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels/8;

        byte[] data = new byte[bufferSize];

        try {
            FileInputStream in = null;
            FileOutputStream out = null;
            in = new FileInputStream(inFilename);
            out = new FileOutputStream(outFilename);
            totalAudioLen = in.getChannel().size();
            totalDataLen = totalAudioLen + 36;

//            AppLog.logString("File size: " + totalDataLen);

            WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                    longSampleRate, channels, byteRate);

            while(in.read(data) != -1){
                out.write(data);
            }


            in.close();
            out.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void WriteWaveFileHeader(
            FileOutputStream out, long totalAudioLen,
            long totalDataLen, long longSampleRate, int channels,
            long byteRate) throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R'; // RIFF/WAVE header
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f'; // 'fmt ' chunk
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16; // 4 bytes: size of 'fmt ' chunk
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1; // format = 1
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (longSampleRate & 0xff);
        header[25] = (byte) ((longSampleRate >> 8) & 0xff);
        header[26] = (byte) ((longSampleRate >> 16) & 0xff);
        header[27] = (byte) ((longSampleRate >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = (byte) (2 * 16 / 8); // block align
        header[33] = 0;
        header[34] = RECORDER_BPP; // bits per sample
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (totalAudioLen & 0xff);
        header[41] = (byte) ((totalAudioLen >> 8) & 0xff);
        header[42] = (byte) ((totalAudioLen >> 16) & 0xff);
        header[43] = (byte) ((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }

    public void soundToDataBase() {
        ContentValues cv = new ContentValues();
        cv.put("letter", editTextRecord.getText().toString().toLowerCase());
        try {
            byte[] byteSound1 = null;
            FileInputStream instream = new FileInputStream(path1);
            BufferedInputStream bif = new BufferedInputStream(instream);
            byteSound1 = new byte[bif.available()];
            bif.read(byteSound1);
            cv.put("sound", byteSound1);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mDb.insert("sounds", null, cv);
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(getActivity(), new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length> 0) {
                    boolean StoragePermission = grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;
                    boolean RecordPermission = grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        Toast.makeText(getContext(), "Разрешение предоставлено",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(),"В разрешении Отказано",Toast.LENGTH_LONG).show();
                    }
                }
                break;
        }
    }

    public boolean checkPermission() {
        int result = ContextCompat.checkSelfPermission(getActivity(),
                WRITE_EXTERNAL_STORAGE);
        int result1 = ContextCompat.checkSelfPermission(getActivity(),
                RECORD_AUDIO);
        return result == PackageManager.PERMISSION_GRANTED &&
                result1 == PackageManager.PERMISSION_GRANTED;
    }
}