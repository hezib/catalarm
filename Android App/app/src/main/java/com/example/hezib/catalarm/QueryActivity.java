package com.example.hezib.catalarm;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.design.widget.Snackbar;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.hezib.catalarm.utils.CloudantQueries;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class QueryActivity extends AppCompatActivity implements QueriesAdapter.QueriesAdapterOnClickHandler, CloudantQueries.CloudantQueriesEvents {

    private static final String TAG = QueryActivity.class.getName();

    private Bitmap mImageBitmap;
    private ImageView mImageView;
    private TextView mTextView;
    private RecyclerView mRecyclerView;
    private QueriesAdapter mQueriesAdapter;
    private String id;
    private ArrayList<DatabaseDocument> mDocsList;
    private CloudantQueries queriesHandler;
    private boolean isChanged = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_query);

        mTextView = (TextView) findViewById(R.id.textView);
        mImageView = (ImageView) findViewById(R.id.imageView);
        mRecyclerView = (RecyclerView) findViewById(R.id.recyclerView);

        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
        mRecyclerView.setLayoutManager(linearLayoutManager);
        mQueriesAdapter = new QueriesAdapter(this);
        mRecyclerView.setAdapter(mQueriesAdapter);

        queriesHandler = new CloudantQueries(this);

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder viewHolder, int swipeDir) {
                DatabaseDocument document = (DatabaseDocument) viewHolder.itemView.getTag();
                Snackbar snackbar = Snackbar.make(mTextView, "Item has been deleted", Snackbar.LENGTH_SHORT)
                        .setAction("UNDO", new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                            }
                        });
                snackbar.addCallback(new Snackbar.Callback() {
                    @Override
                    public void onDismissed(Snackbar transientBottomBar, int event) {
                        super.onDismissed(transientBottomBar, event);
                        if (event != DISMISS_EVENT_ACTION) {
                            queriesHandler.deleteFromDatabase(document);
                            isChanged = true;
                        } else {
                            queriesHandler.getAllFromDatabase();
                        }
                    }
                });
                snackbar.show();
            }
        }).attachToRecyclerView(mRecyclerView);

        if (savedInstanceState == null && getIntent().getExtras() != null && getIntent().getExtras().containsKey(MainActivity.SAVE_ARRAYLIST_EXTRA)) {
            mDocsList = getIntent().getExtras().getParcelableArrayList(MainActivity.SAVE_ARRAYLIST_EXTRA);
            mQueriesAdapter.setDocumentsList(mDocsList);
        } else if (savedInstanceState == null || !savedInstanceState.containsKey(MainActivity.SAVE_ARRAYLIST_EXTRA)) {
            queriesHandler.getAllFromDatabase();
        } else {
            mDocsList = savedInstanceState.getParcelableArrayList(MainActivity.SAVE_ARRAYLIST_EXTRA);
            mQueriesAdapter.setDocumentsList(mDocsList);
        }
    }

    @Override
    public void onClick(DatabaseDocument document) {
        queriesHandler.getDocFromDatabase(document);
    }

    private void updateTopImage(String jsonString) {
        try {
            JSONObject jsonObject = new JSONObject(jsonString);
            this.id = jsonObject.getString("_id");
            String b64Image = jsonObject.getString("payload");
            byte[] imageByes = Base64.decode(b64Image, Base64.NO_WRAP);
            Bitmap imageBitmap = BitmapFactory.decodeByteArray(imageByes, 0, imageByes.length);
            mTextView.setVisibility(View.GONE);
            mImageView.setVisibility(View.VISIBLE);
            mImageView.setImageBitmap(imageBitmap);
            mImageBitmap = imageBitmap;
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.queries_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_item_share) {
            shareImage();
            return true;
        } else if (item.getItemId() == R.id.menu_item_store) {
            storeImage();
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    private void shareImage() {
        Bitmap icon = mImageBitmap;
        if (icon == null) {
            Toast.makeText(this, "Please choose an item first", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            File cachePath = new File(getCacheDir(), "images");
            cachePath.mkdirs();
            FileOutputStream stream = new FileOutputStream(cachePath + "/image.png"); // overwrites this image every time
            icon.compress(Bitmap.CompressFormat.PNG, 100, stream);
            stream.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        File imagePath = new File(getCacheDir(), "images");
        File newFile = new File(imagePath, "image.png");
        Uri contentUri = FileProvider.getUriForFile(this, "com.example.hezib.catalarm.fileprovider", newFile);

        if (contentUri != null) {
            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); // temp permission for receiving app to read this file
            shareIntent.setDataAndType(contentUri, getContentResolver().getType(contentUri));
            shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
            startActivity(Intent.createChooser(shareIntent, "Choose an app"));
        }

    }

    private void storeImage() {
        Bitmap icon = mImageBitmap;
        if (icon == null || id == null) {
            Toast.makeText(this, "Please choose an item first", Toast.LENGTH_SHORT).show();
            return;
        }

        String filename = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + "IMG_" + id + ".png";
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(filename);
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (out != null) {
                    out.close();
                    Toast.makeText(this, "Image saved at\n" + filename, Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putParcelableArrayList(MainActivity.SAVE_ARRAYLIST_EXTRA, mDocsList);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onError(String errorMessage) {
        Log.e(TAG, "Error querying database: " + errorMessage);
    }

    @Override
    public void onGetAllResponse(String response) {
        List<DatabaseDocument> list = DatabaseDocument.getListFromJson(response);
        if (list instanceof ArrayList)
            mDocsList = (ArrayList<DatabaseDocument>) list;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mQueriesAdapter.setDocumentsList(list);
            }
        });
    }

    @Override
    public void onGetDocResponse(String response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateTopImage(response);
            }
        });
    }

    @Override
    public void onDeleteResponse(String response) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                queriesHandler.getAllFromDatabase();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if(isChanged) {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putParcelableArrayList(MainActivity.SAVE_ARRAYLIST_EXTRA, mDocsList);
            intent.putExtras(bundle);
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED);
        }

        super.onBackPressed();
    }
}
