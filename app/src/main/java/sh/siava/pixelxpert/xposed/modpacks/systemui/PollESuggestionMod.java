package sh.siava.pixelxpert.xposed.modpacks.systemui;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import io.github.libxposed.api.XposedModuleInterface;
import sh.siava.pixelxpert.xposed.Constants;
import sh.siava.pixelxpert.xposed.XposedModPack;
import sh.siava.pixelxpert.xposed.annotations.SystemUIModPack;
import sh.siava.pixelxpert.xposed.utils.reflection.ReflectedClass;

import static sh.siava.pixelxpert.xposed.utils.SystemUtils.idOf;

@SystemUIModPack
public class PollESuggestionMod extends XposedModPack {

    private static final long SUGGESTION_DISPLAY_MS = 10_000L;
    private static final String TAG_POLL_E = "poll_e_suggestion_view";

    private ViewGroup mStatusbarStartSide = null;
    private TextView mSuggestionView = null;
    private String mPendingSuggestion = null;

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHideRunnable = this::hideSuggestion;

    private final BroadcastReceiver mSuggestionReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String text = intent.getStringExtra(Constants.EXTRA_POLL_E_TEXT);
            if (text != null && !text.isBlank()) {
                showSuggestion(text);
            }
        }
    };

    public PollESuggestionMod(Context context) {
        super(context);
    }

    @SuppressLint("DiscouragedApi")
    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageReadyParam PRParam) throws Throwable {
        IntentFilter filter = new IntentFilter(Constants.ACTION_POLL_E_SUGGESTION);
        mContext.registerReceiver(
                mSuggestionReceiver, filter,
                Constants.PERMISSION_POLL_E_IPC, null,
                Context.RECEIVER_EXPORTED);

        ReflectedClass PhoneStatusBarViewClass =
                ReflectedClass.of("com.android.systemui.statusbar.phone.PhoneStatusBarView");

        PhoneStatusBarViewClass
                .after("updateStatusBarHeight")
                .run(param -> {
                    View root = (View) param.thisObject;
                    ViewGroup startSide = root.findViewWithTag(TAG_POLL_E) != null
                            ? mStatusbarStartSide
                            : (ViewGroup) root.findViewById(idOf("status_bar_start_side_except_heads_up"));
                    if (startSide == null) return;
                    if (startSide == mStatusbarStartSide) return;
                    mStatusbarStartSide = startSide;
                    attachSuggestionView();
                });
    }

    private void attachSuggestionView() {
        if (mSuggestionView != null && mSuggestionView.getParent() != null) {
            ((ViewGroup) mSuggestionView.getParent()).removeView(mSuggestionView);
        }

        TextView tv = new TextView(mContext);
        tv.setTag(TAG_POLL_E);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        tv.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
        tv.setTextColor(Color.WHITE);
        tv.setAlpha(0.85f);
        tv.setMaxLines(1);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        tv.setMaxEms(18);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setVisibility(View.GONE);
        tv.setOnClickListener(v -> acceptSuggestion());
        tv.setOnLongClickListener(v -> { hideSuggestion(); return true; });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMarginStart(8);
        tv.setLayoutParams(lp);

        mStatusbarStartSide.addView(tv, 1);
        mSuggestionView = tv;

        if (mPendingSuggestion != null) {
            showSuggestion(mPendingSuggestion);
        }
    }

    private void showSuggestion(String text) {
        mPendingSuggestion = text;
        if (mSuggestionView == null) return;
        mHandler.removeCallbacks(mHideRunnable);
        mSuggestionView.post(() -> {
            mSuggestionView.setText(text);
            mSuggestionView.setVisibility(View.VISIBLE);
        });
        mHandler.postDelayed(mHideRunnable, SUGGESTION_DISPLAY_MS);
    }

    private void hideSuggestion() {
        mPendingSuggestion = null;
        mHandler.removeCallbacks(mHideRunnable);
        if (mSuggestionView == null) return;
        mSuggestionView.post(() -> {
            mSuggestionView.setVisibility(View.GONE);
            mSuggestionView.setText("");
        });
    }

    private void acceptSuggestion() {
        // Commit the suggestion: send back to Poll-E service so it can insert the text.
        // TODO: broadcast ACTION_POLL_E_ACCEPT with the text once Poll-E service handles it.
        hideSuggestion();
    }

    @Override
    public void onPreferenceUpdated(String... Key) {}
}
