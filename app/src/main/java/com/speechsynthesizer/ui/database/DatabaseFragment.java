package com.speechsynthesizer.ui.database;

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.speechsynthesizer.CustomAdapter;
import com.speechsynthesizer.DatabaseHelper;
import com.speechsynthesizer.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseFragment extends Fragment {

    private DatabaseHelper mDBHelper;
    private SQLiteDatabase mDb;


    public View onCreateView(@NonNull LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {

        View root = inflater.inflate(R.layout.fragment_database, container, false);

        Button buttonOutputAll = (Button) root.findViewById(R.id.buttonOutputAll);
        Button buttonOutput = (Button) root.findViewById(R.id.buttonOutput);
        Button buttonDelete = (Button) root.findViewById(R.id.buttonDelete);
        EditText editTextSyllable = (EditText) root.findViewById(R.id.editTextSyllable);
        TextView textViewStatus = (TextView) root.findViewById(R.id.textViewStatus);


        buttonDelete.setEnabled(false);
        buttonOutput.setEnabled(false);

        editTextSyllable.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                buttonOutput.setEnabled(false);
                buttonDelete.setEnabled(false);
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                //check isAllFill and show button
                if(editTextSyllable.getText().toString().trim().length() > 0){
                    buttonOutput.setEnabled(true); //show view
                    buttonDelete.setEnabled(true);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        RecyclerView recyclerView = root.findViewById(R.id.recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL));

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


        buttonOutput.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor cursor = mDb.rawQuery("SELECT * FROM sounds", null);
                List letters = new ArrayList();
                while (cursor.moveToNext()) {
                    String letter = cursor.getString(0);
                    letters.add(letter);
                }
                cursor.close();

                List letter = new ArrayList();
                if (letters.contains(editTextSyllable.getText().toString().toLowerCase())) {
                    letter.add(editTextSyllable.getText().toString().toLowerCase());
                    recyclerView.setAdapter(new CustomAdapter(generateData(letter)));
                }
                else {
                    recyclerView.setAdapter(new CustomAdapter(generateData(null)));
                }
            }
        });

        buttonOutputAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Cursor cursor = mDb.rawQuery("SELECT * FROM sounds", null);
                List letters = new ArrayList();
                while (cursor.moveToNext()) {
                    String letter = cursor.getString(0);
                    letters.add(letter);
                }
                cursor.close();
                recyclerView.setAdapter(new CustomAdapter(generateData(letters)));
            }
        });

        buttonDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean foundRow = false;
                Cursor cursor = mDb.rawQuery("SELECT * FROM sounds where letter = ?", new String[] {editTextSyllable.getText().toString().toLowerCase()}, null);
                if(cursor.getCount() <= 0){
                    cursor.close();
                    foundRow = false;
                }
                else {
                    cursor.close();
                    foundRow = true;
                }
                if (foundRow) {
                    mDb.delete("sounds", "letter = ?", new String[] {editTextSyllable.getText().toString().toLowerCase()});
                    textViewStatus.setText("Запись “" + editTextSyllable.getText().toString().toLowerCase() + "” удалена");
                }
                else {
                    textViewStatus.setText("Записи со звуком“" + editTextSyllable.getText().toString().toLowerCase() + "”не найдено");
                }

            }
        });
        return root;
    }
    private List<String> generateData(List letters) {
        List<String> data = new ArrayList<>();
        if (letters != null) {
            for(int i = 0; i < letters.size(); i++) {
                data.add((i + 1) + " " + String.valueOf(letters.get(i)));
            }
        }
        else {
            data.add("Не найдено");
        }
        return data;
    }
}