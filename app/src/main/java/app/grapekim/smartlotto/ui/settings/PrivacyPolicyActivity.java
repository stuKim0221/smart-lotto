package app.grapekim.smartlotto.ui.settings;

import android.os.Build;
import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import app.grapekim.smartlotto.R;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.privacy_policy_title);
        setContentView(R.layout.activity_privacy_policy);

        // HTML 태그 적용
        TextView tvPolicy = findViewById(R.id.tvPolicy);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            tvPolicy.setText(Html.fromHtml(
                    getString(R.string.privacy_policy_placeholder),
                    Html.FROM_HTML_MODE_COMPACT
            ));
        } else {
            tvPolicy.setText(Html.fromHtml(getString(R.string.privacy_policy_placeholder)));
        }
    }
}