package com.weibo.net;

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.webkit.SslErrorHandler;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.weibo.android.R;

public class WeiboDialog extends Dialog {

	static final FrameLayout.LayoutParams FILL = new FrameLayout.LayoutParams(
			ViewGroup.LayoutParams.FILL_PARENT,
			ViewGroup.LayoutParams.FILL_PARENT);
	static final int MARGIN = 4;
	static final int PADDING = 2;

	private final Weibo mWeibo;
	private String mUrl;
	private WeiboDialogListener mListener;
	private ProgressDialog mSpinner;
	private ImageView mBtnClose;
	private WebView mWebView;
	private RelativeLayout webViewContainer;
	private RelativeLayout mContent;

	private final static String TAG = "Weibo-WebView";

	public WeiboDialog(final Weibo weibo, Context context, String url,
			WeiboDialogListener listener) {
		super(context, R.style.weibosdk_ContentOverlay);
		mWeibo = weibo;
		mUrl = url;
		mListener = listener;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSpinner = new ProgressDialog(getContext());
		mSpinner.requestWindowFeature(Window.FEATURE_NO_TITLE);
		mSpinner.setMessage("Loading...");
		mSpinner.setOnKeyListener(new OnKeyListener() {

			@Override
			public boolean onKey(DialogInterface dialog, int keyCode,
					KeyEvent event) {
				onBack();
				return false;
			}

		});
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		mContent = new RelativeLayout(getContext());

		setUpWebView();
		// setUpCloseBtn();

		addContentView(mContent, new LayoutParams(LayoutParams.FILL_PARENT,
				LayoutParams.FILL_PARENT));
	}

	protected void onBack() {
		try {
			mSpinner.dismiss();
			if (null != mWebView) {
				mWebView.stopLoading();
				mWebView.destroy();
			}
		} catch (Exception e) {
		}
		dismiss();
	}

	private void setUpWebView() {
		webViewContainer = new RelativeLayout(getContext());

		mWebView = new WebView(getContext());
		mWebView.setVerticalScrollBarEnabled(false);
		mWebView.setHorizontalScrollBarEnabled(false);
		mWebView.getSettings().setJavaScriptEnabled(true);
		mWebView.setWebViewClient(new WeiboDialog.WeiboWebViewClient());
		mWebView.loadUrl(mUrl);
		mWebView.setLayoutParams(FILL);
		mWebView.setVisibility(View.INVISIBLE);

		webViewContainer.addView(mWebView);

		RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
				LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
		Resources resources = getContext().getResources();
		lp.leftMargin = resources
				.getDimensionPixelSize(R.dimen.weibosdk_dialog_left_margin);
		lp.topMargin = resources
				.getDimensionPixelSize(R.dimen.weibosdk_dialog_top_margin);
		lp.rightMargin = resources
				.getDimensionPixelSize(R.dimen.weibosdk_dialog_right_margin);
		lp.bottomMargin = resources
				.getDimensionPixelSize(R.dimen.weibosdk_dialog_bottom_margin);
		mContent.addView(webViewContainer, lp);
	}

	private void setUpCloseBtn() {
		mBtnClose = new ImageView(getContext());
		mBtnClose.setClickable(true);
		mBtnClose.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mListener.onCancel();
				WeiboDialog.this.dismiss();
			}
		});

		mBtnClose.setImageResource(R.drawable.weibosdk_close_selector);
		mBtnClose.setVisibility(View.INVISIBLE);

		RelativeLayout.LayoutParams closeBtnRL = new RelativeLayout.LayoutParams(
				LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		closeBtnRL.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		closeBtnRL.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		closeBtnRL.topMargin = getContext().getResources()
				.getDimensionPixelSize(
						R.dimen.weibosdk_dialog_btn_close_right_margin);
		closeBtnRL.rightMargin = getContext().getResources()
				.getDimensionPixelSize(
						R.dimen.weibosdk_dialog_btn_close_top_margin);

		webViewContainer.addView(mBtnClose, closeBtnRL);
	}

	private class WeiboWebViewClient extends WebViewClient {

		@Override
		public boolean shouldOverrideUrlLoading(WebView view, String url) {
			Log.d(TAG, "Redirect URL: " + url);
			return super.shouldOverrideUrlLoading(view, url);
		}

		@Override
		public void onReceivedError(WebView view, int errorCode,
				String description, String failingUrl) {
			super.onReceivedError(view, errorCode, description, failingUrl);
			mListener.onError(new DialogError(description, errorCode,
					failingUrl));
			WeiboDialog.this.dismiss();
		}

		@Override
		public void onPageStarted(WebView view, String url, Bitmap favicon) {
			Log.d(TAG, "onPageStarted URL: " + url);
			if (url.startsWith(mWeibo.getRedirectUrl())) {
				handleRedirectUrl(view, url);
				view.stopLoading();
				WeiboDialog.this.dismiss();
				return;
			}
			super.onPageStarted(view, url, favicon);
			mSpinner.show();
		}

		@Override
		public void onPageFinished(WebView view, String url) {
			Log.d(TAG, "onPageFinished URL: " + url);
			super.onPageFinished(view, url);
			if (mSpinner.isShowing()) {
				mSpinner.dismiss();
			}

			mContent.setBackgroundColor(Color.TRANSPARENT);
			webViewContainer
					.setBackgroundResource(R.drawable.weibosdk_dialog_bg);
			// mBtnClose.setVisibility(View.VISIBLE);
			mWebView.setVisibility(View.VISIBLE);
		}

		public void onReceivedSslError(WebView view, SslErrorHandler handler,
				SslError error) {
			handler.proceed();
		}

	}

	private void handleRedirectUrl(WebView view, String url) {
		Bundle values = Utility.parseUrl(url);

		String error = values.getString("error");
		String error_code = values.getString("error_code");

		if (error == null && error_code == null) {
			mListener.onComplete(values);
		} else if (error.equals("access_denied")) {
			// 用户或授权服务器拒绝授予数据访问权限
			mListener.onCancel();
		} else {
			mListener.onWeiboException(new WeiboException(error, Integer
					.parseInt(error_code)));
		}
	}
}
