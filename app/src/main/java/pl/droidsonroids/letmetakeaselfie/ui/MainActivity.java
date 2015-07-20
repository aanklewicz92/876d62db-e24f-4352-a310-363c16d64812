package pl.droidsonroids.letmetakeaselfie.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.PersistableBundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import pl.droidsonroids.letmetakeaselfie.R;
import pl.droidsonroids.letmetakeaselfie.api.SelfieApiManager;
import pl.droidsonroids.letmetakeaselfie.api.response.GetSelfiesResponse;
import pl.droidsonroids.letmetakeaselfie.api.response.PostSelfieResponse;
import pl.droidsonroids.letmetakeaselfie.model.Selfie;
import pl.droidsonroids.letmetakeaselfie.ui.adapter.SelfieAdapter;
import pl.droidsonroids.letmetakeaselfie.ui.dialog.UserNameDialogFragment;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class MainActivity extends AppCompatActivity implements UserNameDialogFragment.OnUserNameTypedListener {

    private final static int TAKE_PICTURE_REQUEST_CODE = 0;
    private final static String BUNDLE_PHOTO_PATH = "bundle_file_path";
    @Bind(R.id.refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;
    @Bind(R.id.recycler_view)
    RecyclerView recyclerView;
    private boolean resultOk = false;

    private SelfieAdapter selfieAdapter;
    private String mPhotoPath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ButterKnife.bind(this);

        initSelfieAdapter();
        setupSwipeRefreshLayout();
        setupRecyclerView();
        postRefresh();
    }

    private void initSelfieAdapter() {
        selfieAdapter = new SelfieAdapter();
    }

    private void setupSwipeRefreshLayout() {
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshSelfies();
            }
        });
    }

    private void setupRecyclerView() {
        recyclerView.setAdapter(selfieAdapter);
        recyclerView.setLayoutManager(new GridLayoutManager(this, getResources().getInteger(R.integer.grid_span_count)));
        recyclerView.addItemDecoration(new RecyclerView.ItemDecoration() {
            @Override
            public void getItemOffsets(final Rect outRect, final View view, final RecyclerView parent, final RecyclerView.State state) {
                int offset = getResources().getDimensionPixelSize(R.dimen.grid_padding);
                outRect.set(offset, offset, offset, offset);
            }
        });
    }

    private void postRefresh() {
        swipeRefreshLayout.post(new Runnable() {
            @Override
            public void run() {
                swipeRefreshLayout.setRefreshing(true);
                refreshSelfies();
            }
        });
    }

    private void refreshSelfies() {
        SelfieApiManager.getInstance().getSelfies(new Callback<GetSelfiesResponse>() {
            @Override
            public void success(final GetSelfiesResponse getSelfiesResponse, final Response response) {
                swipeRefreshLayout.setRefreshing(false);
                selfieAdapter.setSelfies(getSelfiesResponse.getSelfies());
            }

            @Override
            public void failure(final RetrofitError error) {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, R.string.selfies_load_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
        if (requestCode == TAKE_PICTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            resultOk = true;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState, PersistableBundle outPersistentState) {
        outState.putString(BUNDLE_PHOTO_PATH, mPhotoPath);
        super.onSaveInstanceState(outState, outPersistentState);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        mPhotoPath = savedInstanceState.getString(BUNDLE_PHOTO_PATH);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resultOk)
            new UserNameDialogFragment().show(getSupportFragmentManager(), null);
        resultOk = false;
    }

    @OnClick(R.id.button_add)
    public void addSelfie() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File photo = new File(Environment.getExternalStorageDirectory(), "temp.jpg");
            intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photo));
            mPhotoPath = photo.getPath();
            startActivityForResult(intent, TAKE_PICTURE_REQUEST_CODE);
        } else {
            Toast.makeText(this, R.string.no_camera, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onUserNameTyped(@NonNull final String userName) {
        Selfie selfie = new Selfie();
        selfie.setUserName(userName);
        File file = rotateImage(mPhotoPath);
        if (file != null)
            selfie.setPhotoFile(file);
        else
            Toast.makeText(this, R.string.rotation_error, Toast.LENGTH_SHORT).show();

        SelfieApiManager.getInstance().postSelfie(selfie, new Callback<PostSelfieResponse>() {
            @Override
            public void success(final PostSelfieResponse postSelfieResponse, final Response response) {
                refreshSelfies();
            }

            @Override
            public void failure(final RetrofitError error) {
                Toast.makeText(MainActivity.this, R.string.selfie_upload_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private File rotateImage(String path) {
        Bitmap bitmap = BitmapFactory.decodeFile(path);
        ExifInterface ei;
        try {
            ei = new ExifInterface(path);
        } catch (IOException e) {
            return null;
        }
        int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
        }
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        File file = new File(Environment.getExternalStorageDirectory(), "name" + System.currentTimeMillis() + ".jpg");

        try {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.PNG, 0, fileOutputStream);
            fileOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return file;
    }
}
