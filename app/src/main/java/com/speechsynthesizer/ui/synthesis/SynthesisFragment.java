package com.speechsynthesizer.ui.synthesis;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.PlaybackParams;
import android.os.Bundle;
import android.os.Environment;
import android.speech.tts.TextToSpeech;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.speechsynthesizer.DatabaseHelper;
import com.speechsynthesizer.R;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public class SynthesisFragment extends Fragment {

    Button button_play;
    TextView textView;
    EditText editText_play;
    Switch switchRealTimeSynthesis;
    float progressFloat = 1;
    private TextToSpeech textToSpeechSystem;
    String[] data_spinner = {"Denis TTS", "Google TTS"};
    private byte[] output;
    private static final int RECORDER_SAMPLERATE = 44100;
    private static final int RECORDER_BPP = 16;

    private DatabaseHelper mDBHelper;
    private SQLiteDatabase mDb;

    private MediaPlayer mediaPlayer = new MediaPlayer();

    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_synthesis, container, false);

        button_play = (Button) root.findViewById(R.id.button_play);
        textView = (TextView) root.findViewById(R.id.textView);
        editText_play = (EditText) root.findViewById(R.id.editText_play);
        switchRealTimeSynthesis = (Switch) root.findViewById(R.id.switchRealTimeSynthesis);


        button_play.setEnabled(false);


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


        Map<String,byte[]> sounds = new HashMap<String,byte[]>();


        Cursor cursor = mDb.rawQuery("SELECT * FROM sounds", null);
        while (cursor.moveToNext()) {
            String letter = cursor.getString(0);
            byte[] sound = cursor.getBlob(1);

            sounds.put(letter, sound);

        }
        cursor.close();


        SeekBar seekBarSpeed = (SeekBar) root.findViewById(R.id.seekBarSpeed);
        TextView textViewSpeed = (TextView) root.findViewById(R.id.textViewSpeed);
        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float progressFloat = progress;
                textViewSpeed.setText("Скорость воспроизведения: " + String.valueOf(progressFloat / 4) + "x");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        editText_play.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                button_play.setEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //check isAllFill and show button
                if(editText_play.getText().toString().trim().length() > 0){
                    button_play.setEnabled(true); //show view
                    if (switchRealTimeSynthesis.isChecked()) {
                        editText_play.getText().toString().charAt(editText_play.getText().toString().length() - 1);
                        String letter = String.valueOf(editText_play.getText().toString().charAt(editText_play.getText().toString().length() - 1)).toLowerCase();

                        byte[] sound = sounds.get(letter);

                        try {
                            File temp1Mp3 = File.createTempFile("temp1", "mp3", getContext().getCacheDir());
                            temp1Mp3.deleteOnExit();
                            FileOutputStream fos = new FileOutputStream(temp1Mp3);
                            fos.write(sound);
                            fos.close();
                            mediaPlayer.reset();
                            FileInputStream fis = new FileInputStream(temp1Mp3);
                            mediaPlayer.setDataSource(fis.getFD());
                            mediaPlayer.prepare();
                            mediaPlayer.start();
                        } catch (IOException ex) {
                            String se = ex.toString();
                            ex.printStackTrace();
                        }
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        // адаптер
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(getContext(), android.R.layout.simple_spinner_item, data_spinner);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Spinner spinner = (Spinner) root.findViewById(R.id.spinner);
        spinner.setAdapter(adapter);
        // выделяем элемент
        spinner.setSelection(0);
        // устанавливаем обработчик нажатия
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view,
                                       int position, long id) {
                // показываем позиция нажатого элемента
                Toast.makeText(getActivity(), spinner.getSelectedItem().toString(), Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

        button_play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String strEditText_play = editText_play.getText().toString().toLowerCase();

                if (spinner.getSelectedItem().toString() == "Google TTS") {
                    textToSpeechSystem = new TextToSpeech(getContext(), new TextToSpeech.OnInitListener() {
                        @Override
                        public void onInit(int status) {
                            if (status == TextToSpeech.SUCCESS) {
                                String textToSay = strEditText_play;
                                Locale locale = new Locale("ru");
                                textToSpeechSystem.setLanguage(locale);
                                textToSpeechSystem.speak(textToSay, TextToSpeech.QUEUE_ADD, null);
                            }
                        }
                    });
                }
                else {
                    List splited_text = splitIntoSyllables(strEditText_play);
                    System.out.println(splited_text);
                    List<byte[]> desired_letters_syllables = new ArrayList<byte[]>();
                    for(Object syllable : splited_text) {
                        if (!sounds.containsKey(String.valueOf(syllable))) {
                            for(char letter2 : (String.valueOf(syllable)).toCharArray()) {
                                String strLetter2 = Character.toString(letter2);
                                byte[] sound2 = sounds.get(strLetter2);
                                desired_letters_syllables.add(sound2);
                            }
                        }
                        else {
                            byte[] sound2 = sounds.get(syllable);
                            desired_letters_syllables.add(sound2);
                        }
                    }

                    List soundFiles = new ArrayList();

                    for(byte[] sound3 : desired_letters_syllables) {
                        if (sound3 != null) {
                            try {
                                String path = getActivity().getCacheDir() + "/" + (int) (Math.random() * 1000000000) + ".wav";
                                writeBytesToFile(path, sound3);
                                soundFiles.add(path);

                            } catch (IOException ex) {
                                String s = ex.toString();
                                ex.printStackTrace();
                            }
                        }
                    }

                    System.out.println(soundFiles);

                    if (soundFiles.size() == 1) {
                        try {
                            mediaPlayer.reset();
                            mediaPlayer.setDataSource(soundFiles.get(0).toString());
                            mediaPlayer.prepare();
                            mediaPlayer.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    else {
                        FileInputStream in1 = null, in2 = null;
                        FileOutputStream out = null;
                        long totalAudioLen = 0;
                        long totalDataLen = totalAudioLen + 36;
                        long longSampleRate = RECORDER_SAMPLERATE;
                        int channels = 2;
                        long byteRate = RECORDER_BPP * RECORDER_SAMPLERATE * channels / 8;
                        int bufferSize = 0;
                        bufferSize = AudioRecord.getMinBufferSize(8000,
                                AudioFormat.CHANNEL_CONFIGURATION_STEREO,
                                AudioFormat.ENCODING_PCM_16BIT);

                        byte[] data = new byte[bufferSize];

                        for (int b = 0; b <= soundFiles.size(); b++) {
                            if (b + 1 >= soundFiles.size()) {
                                break;
                            } else {
                                try {
                                    if (b > 0) {
                                        in1 = new FileInputStream(getActivity().getCacheDir() + "/" + "join_wav_files_temp" + ".wav");
                                    } else {
                                        in1 = new FileInputStream(soundFiles.get(b).toString());
                                    }
                                    in2 = new FileInputStream(soundFiles.get(b + 1).toString());

                                    out = new FileOutputStream(getActivity().getCacheDir() + "/" + "join_wav_files" + ".wav");

                                    totalAudioLen = in1.getChannel().size() + in2.getChannel().size();
                                    totalDataLen = totalAudioLen + 36;

                                    WriteWaveFileHeader(out, totalAudioLen, totalDataLen,
                                            longSampleRate, channels, byteRate);

                                    while (in1.read(data) != -1) {
                                        out.write(data);
                                    }
                                    while (in2.read(data) != -1) {
                                        out.write(data);
                                    }

                                    out.close();
                                    in1.close();
                                    in2.close();

                                    InputStream is = null;
                                    OutputStream os = null;
                                    try {
                                        is = new FileInputStream(getActivity().getCacheDir() + "/" + "join_wav_files" + ".wav");
                                        os = new FileOutputStream(getActivity().getCacheDir() + "/" + "join_wav_files_temp" + ".wav");
                                        byte[] buffer = new byte[1024];
                                        int length;
                                        while ((length = is.read(buffer)) > 0) {
                                            os.write(buffer, 0, length);
                                        }
                                    } finally {
                                        is.close();
                                        os.close();
                                    }

                                } catch (FileNotFoundException e) {
                                    e.printStackTrace();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }


                        if (getActivity().getCacheDir().isDirectory()) {
                            for (File child : getActivity().getCacheDir().listFiles()) {
                                if (!child.toString().equals(getActivity().getCacheDir() + "/" + "join_wav_files" + ".wav")) {
                                    child.delete();
                                }
                            }
                        }


                        try {
                            mediaPlayer.reset();

                            mediaPlayer.setDataSource(getActivity().getCacheDir() + "/" + "join_wav_files" + ".wav");
                            mediaPlayer.prepare();

                            PlaybackParams playbackParams = new PlaybackParams();
                            float floatSpeed = seekBarSpeed.getProgress();
                            playbackParams.setSpeed(floatSpeed / 4);
                            mediaPlayer.setPlaybackParams(playbackParams);
                            mediaPlayer.start();

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        return root;
    }


    private void WriteWaveFileHeader(FileOutputStream out, long totalAudioLen,
                                     long totalDataLen, long longSampleRate, int channels, long byteRate)
            throws IOException {

        byte[] header = new byte[44];

        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte)(totalDataLen & 0xff);
        header[5] = (byte)((totalDataLen >> 8) & 0xff);
        header[6] = (byte)((totalDataLen >> 16) & 0xff);
        header[7] = (byte)((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = 16;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte)(longSampleRate & 0xff);
        header[25] = (byte)((longSampleRate >> 8) & 0xff);
        header[26] = (byte)((longSampleRate >> 16) & 0xff);
        header[27] = (byte)((longSampleRate >> 24) & 0xff);
        header[28] = (byte)(byteRate & 0xff);
        header[29] = (byte)((byteRate >> 8) & 0xff);
        header[30] = (byte)((byteRate >> 16) & 0xff);
        header[31] = (byte)((byteRate >> 24) & 0xff);
        header[32] = (byte)(2 * 16 / 8);
        header[33] = 0;
        header[34] = RECORDER_BPP;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte)(totalAudioLen & 0xff);
        header[41] = (byte)((totalAudioLen >> 8) & 0xff);
        header[42] = (byte)((totalAudioLen >> 16) & 0xff);
        header[43] = (byte)((totalAudioLen >> 24) & 0xff);

        out.write(header, 0, 44);
    }


    private static void writeBytesToFile(String fileOutput, byte[] bytes)
            throws IOException {

        try (FileOutputStream fos = new FileOutputStream(fileOutput)) {
            fos.write(bytes);
        }

    }

    public static List splitIntoSyllables(String text) {
        List<String> consonants = Arrays.asList("б", "в", "г", "д", "ж", "з", "й", "к", "л", 
                "м", "н", "п", "р", "с", "т", "ф", "х", "ц", "ч", "ш", "щ");
        List<String> vowels = Arrays.asList("а", "у", "о", "ы", "и", "э", "я", "ю", "ё", "е");
        List<String> thud = Arrays.asList("к", "п", "с", "т", "ф", "х", "ц", "ч", "ш", "щ");
        List<String> sonorous = Arrays.asList("м", "н", "р", "л");
        String[] splited_text = text.split(" ");
        List splited_words = new ArrayList();

        if (text.length() == 1){
            return Arrays.asList(text);
        }
        for(String word : splited_text) {
            word = word.toLowerCase();
            if (!word.equals("@")) {
                StringBuilder splited_word = new StringBuilder();
                StringBuilder syllable = new StringBuilder();
                String letter2 = "";
                String letter1 = "";
                int i = 0;
                while (i < word.length()) {
                    String current_letter = Character.toString(word.charAt(i));
                    syllable.append(current_letter);
                    if (vowels.contains(current_letter)) {
                        if (i + 1 < word.length()) {
                            letter1 = Character.toString(word.charAt(i + 1));
                            if (consonants.contains(letter1)) {
                                if (i + 1 >= word.length() - 1) {
                                    syllable.append(letter1);
                                    i += 1;
                                }
                                else {
                                    letter2 = Character.toString(word.charAt(i + 2));

                                    if (letter2.equals("й") | letter2.equals("Й") & (consonants.contains(letter2))) {
                                        syllable.append(letter1);
                                        i += 1;
                                    }
                                    else if (sonorous.contains(letter1) & thud.contains(letter2)) {
                                        syllable.append(letter1);
                                        i += 1;
                                    }
                                    else if (i + 3 > word.length()) {
                                        if ((sonorous.contains(letter1) & consonants.contains(letter2) & vowels.contains(Character.toString(word.charAt(i + 3))) & !letter1.equals(letter2))) {
                                            syllable.append(letter1);
                                            i += 1;
                                        }
                                    }
                                    else if (letter2.equals("ь") | letter2.equals("ъ")) {
                                        i += 2;
                                        syllable.append(letter1);
                                        syllable.append(letter2);
                                    }
                                    else if (i + 2 >= word.length() - 1 & consonants.contains(letter2)) {
                                        i += 2;
                                        syllable.append(letter1);
                                        syllable.append(letter2);
                                    }
                                }
                            }
                        }
                        splited_word.append(syllable);
                        if (i + 1 < word.length()) {
                            splited_word.append("-");
                        }
                        syllable = new StringBuilder();
                    }
                    i += 1;
                }
                String[] splited_word2 = splited_word.toString().split("-");
                if (splited_word.toString().equals("")) {
                    return Arrays.asList(text);
                }
                else {
                    for (String word1 : splited_word2) {
                        splited_words.add(word1);
                    }
                }
            }
        }
        return splited_words;
    }
}