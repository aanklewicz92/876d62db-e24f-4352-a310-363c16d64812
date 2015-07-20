package pl.droidsonroids.letmetakeaselfie.ui;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import java.io.File;

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

    @Bind(R.id.refresh_layout) SwipeRefreshLayout swipeRefreshLayout;
    @Bind(R.id.recycler_view) RecyclerView recyclerView;

    private SelfieAdapter selfieAdapter;

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
        if (requestCode == 0 && resultCode == RESULT_OK) {
            new UserNameDialogFragment().show(getSupportFragmentManager(), null);
        }
    }

    @OnClick(R.id.button_add)
    public void addSelfie() {
        // todo: open camera here
    }

    @Override
    public void onUserNameTyped(@NonNull final String userName) {
        Selfie selfie = new Selfie();
        selfie.setUserName(userName);
        selfie.setPhotoFile(new File(getExternalFilesDir(null), null)); // todo: put photo here

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
}
