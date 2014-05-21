package com.jack.cph.ui.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.ShareActionProvider;
import com.jack.cph.R;
import com.jack.cph.model.ColumnList;
import com.jack.cph.model.Result;
import com.jack.cph.task.DialogAsyncTask.ExceptionListener;
import com.jack.cph.task.LoadResultsTask;
import com.jack.cph.ui.dialog.ProgressDialogFragment;
import com.jack.cph.ui.dialog.ProgressDialogFragment.SimpleListener;

public class ResultActivity extends BaseActivity implements ExceptionListener {

	public static final String RESULTS_FILE_NAME = "ContentProviderHelper.html";

	public static final String INTENT_EXTRA_COLUMNS = "columns";
	public static final String INTENT_EXTRA_URI = "uri";
	public static final String INTENT_EXTRA_WHERE = "where";
	public static final String INTENT_EXTRA_LIMIT = "limit";
	public static final String INTENT_EXTRA_SORT_BY = "sortBy";

	private static final String BUNDLE_RESULT = "result";
	private static final String MIME_TYPE = "text/html";
	private static final String TAG_LOAD_RESULTS_DIALOG = "loadResultsDialog";

	private TextView mTxtRows;
	private WebView mWebView;
	private Result mResult;

	private SimpleListener<Void, Result> mDialogListener = new SimpleListener<Void, Result>() {

		@Override
		public void onPostExecute(Result result) {
			setResult(result, true);
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.result_table);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);

		@SuppressWarnings("unchecked")
		ProgressDialogFragment<Uri, Void, Result> dialog = (ProgressDialogFragment<Uri, Void, Result>) getSupportFragmentManager().findFragmentByTag(TAG_LOAD_RESULTS_DIALOG);
		if (dialog != null) {
			// Update the dialog listener so it points to this Activity instead of the old one.
			// This will also allow the GC to reclaim the old Activity, which the ProgressDialogFragment
			// keeps a reference to.
			dialog.setDialogListener(mDialogListener);
		}

		mTxtRows = (TextView) findViewById(R.id.rows);
		mWebView = (WebView) findViewById(R.id.webView);
		mWebView.setVerticalScrollbarOverlay(false);

		TextView txtContentProvider = (TextView) findViewById(R.id.content_provider);
		TextView txtFilter = (TextView) findViewById(R.id.filter);

		Intent intent = getIntent();
		String uri = intent.getStringExtra(INTENT_EXTRA_URI);
		txtContentProvider.setText(uri);

		String where = intent.getStringExtra(INTENT_EXTRA_WHERE);
		String sortBy = intent.getStringExtra(INTENT_EXTRA_SORT_BY);
		String limit = intent.getStringExtra(INTENT_EXTRA_LIMIT);
		if (sortBy != null) {
			sortBy += !TextUtils.isEmpty(limit) ? " LIMIT " + limit : "";
		}

		String filter = buildFilterLabel(where, sortBy);
		String filterLabel = TextUtils.isEmpty(filter) ? getString(R.string.none) : filter.toString();
		txtFilter.setText(filterLabel);

		if (savedInstanceState == null) { // First load, kick off loading task
			ColumnList columns = intent.getParcelableExtra(INTENT_EXTRA_COLUMNS);
			LoadResultsTask.SQLParams sqlParams = new LoadResultsTask.SQLParams(where, sortBy);
			loadResults(uri, sqlParams, columns);
		} else { // Restore previous instance state
			Result result = savedInstanceState.getParcelable(BUNDLE_RESULT);
			if (result != null) {
				setResult(result, false);
			}
		}
	}

	private void loadResults(String uri, LoadResultsTask.SQLParams sqlParams, ColumnList columns) {
		LoadResultsTask loadResultsTask = new LoadResultsTask(this, columns, sqlParams);
		loadResultsTask.setExceptionListener(this);
		loadResultsTask.execute(Uri.parse(uri));

		ProgressDialogFragment<Uri, Void, Result> dialog = ProgressDialogFragment.newInstance(R.string.loading_data);
		dialog.setup(loadResultsTask, mDialogListener);
		dialog.show(getSupportFragmentManager(), TAG_LOAD_RESULTS_DIALOG);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.result_activity, menu);

		MenuItem item = menu.findItem(R.id.share);
		ShareActionProvider shareActionProvider = (ShareActionProvider) item.getActionProvider();

		Intent shareIntent = createShareIntent();
		shareActionProvider.setShareIntent(shareIntent);

		return super.onCreateOptionsMenu(menu);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putParcelable(BUNDLE_RESULT, mResult);
	}

	private Intent createShareIntent() {
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType(MIME_TYPE);

		Uri uri = Uri.parse("content://" + getPackageName() + "/" + RESULTS_FILE_NAME);
		shareIntent.putExtra(Intent.EXTRA_STREAM, uri);

		return shareIntent;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				onBackPressed();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private String buildFilterLabel(String where, String sortBy) {
		StringBuilder filter = new StringBuilder();

		filter.append(!TextUtils.isEmpty(where) ? "WHERE " + where : "");
		filter.append(!TextUtils.isEmpty(filter.toString()) ? " " : "");
		filter.append(!TextUtils.isEmpty(sortBy) ? "SORT BY " + sortBy : "");

		return filter.toString();
	}

	private void setResult(Result result, final boolean showSuccessToast) {
		mResult = result;
		mWebView.setWebViewClient(new WebViewClient() {

			@Override
			public void onPageFinished(WebView view, String url) {
				mTxtRows.setText(String.valueOf(mResult.getRowCount()));
				if (showSuccessToast) {
					Toast.makeText(mContext, R.string.data_successfully_loaded, Toast.LENGTH_SHORT).show();
				}
			}

			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				Toast.makeText(mContext, description + "(" + errorCode + ")", Toast.LENGTH_SHORT).show();
			}
		});

		String url = "file://" + mResult.getFile().getAbsolutePath();
		mWebView.loadUrl(url);
	}

	@Override
	public void onException(Exception e) {
		mTxtRows.setText("-");
	}

}
