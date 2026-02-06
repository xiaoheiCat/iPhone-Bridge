package stu.xiaohei.iphonebridge;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 通知过滤设置界面
 */
public class FilterSettingsActivity extends AppCompatActivity {
    private static final String TAG = "FilterSettings";

    private NotificationFilter mFilter;

    private Switch mFilterEnabledSwitch;
    private RadioButton mWhitelistRadio;
    private RadioButton mBlacklistRadio;
    private Switch mRegexEnabledSwitch;
    private EditText mRegexPatternEdit;
    private Button mRegexTestButton;
    private ListView mWhitelistView;
    private ListView mBlacklistView;
    private Button mAddWhitelistButton;
    private Button mAddBlacklistButton;
    private Button mSaveButton;

    private KeywordAdapter mWhitelistAdapter;
    private KeywordAdapter mBlacklistAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_filter_settings);

        mFilter = new NotificationFilter(this);

        initViews();
        loadCurrentSettings();
        setupListeners();
    }

    private void initViews() {
        mFilterEnabledSwitch = findViewById(R.id.filterEnabledSwitch);
        mWhitelistRadio = findViewById(R.id.whitelistRadio);
        mBlacklistRadio = findViewById(R.id.blacklistRadio);
        mRegexEnabledSwitch = findViewById(R.id.regexEnabledSwitch);
        mRegexPatternEdit = findViewById(R.id.regexPatternEdit);
        mRegexTestButton = findViewById(R.id.regexTestButton);
        mWhitelistView = findViewById(R.id.whitelistView);
        mBlacklistView = findViewById(R.id.blacklistView);
        mAddWhitelistButton = findViewById(R.id.addWhitelistButton);
        mAddBlacklistButton = findViewById(R.id.addBlacklistButton);
        mSaveButton = findViewById(R.id.saveButton);

        // 设置适配器
        mWhitelistAdapter = new KeywordAdapter(new ArrayList<>(), true);
        mWhitelistView.setAdapter(mWhitelistAdapter);

        mBlacklistAdapter = new KeywordAdapter(new ArrayList<>(), false);
        mBlacklistView.setAdapter(mBlacklistAdapter);
    }

    private void loadCurrentSettings() {
        mFilterEnabledSwitch.setChecked(mFilter.isFilterEnabled());

        if (mFilter.getFilterMode() == NotificationFilter.MODE_WHITELIST) {
            mWhitelistRadio.setChecked(true);
        } else {
            mBlacklistRadio.setChecked(true);
        }

        mRegexEnabledSwitch.setChecked(mFilter.isRegexEnabled());
        mRegexPatternEdit.setText(mFilter.getRegexPattern());

        // 加载关键字列表
        mWhitelistAdapter.clear();
        mWhitelistAdapter.addAll(mFilter.getWhitelist());
        mWhitelistAdapter.notifyDataSetChanged();

        mBlacklistAdapter.clear();
        mBlacklistAdapter.addAll(mFilter.getBlacklist());
        mBlacklistAdapter.notifyDataSetChanged();

        updateUIState();
    }

    private void setupListeners() {
        mFilterEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateUIState();
        });

        mRegexEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateUIState();
        });

        mWhitelistRadio.setOnClickListener(v -> {
            mBlacklistRadio.setChecked(false);
        });

        mBlacklistRadio.setOnClickListener(v -> {
            mWhitelistRadio.setChecked(false);
        });

        mRegexTestButton.setOnClickListener(v -> testRegex());

        mAddWhitelistButton.setOnClickListener(v -> showAddKeywordDialog(true));
        mAddBlacklistButton.setOnClickListener(v -> showAddKeywordDialog(false));

        mSaveButton.setOnClickListener(v -> saveSettings());
    }

    private void updateUIState() {
        boolean enabled = mFilterEnabledSwitch.isChecked();
        mWhitelistRadio.setEnabled(enabled);
        mBlacklistRadio.setEnabled(enabled);
        mRegexEnabledSwitch.setEnabled(enabled);
        mRegexPatternEdit.setEnabled(enabled && mRegexEnabledSwitch.isChecked());
        mRegexTestButton.setEnabled(enabled && mRegexEnabledSwitch.isChecked());
        mWhitelistView.setEnabled(enabled);
        mBlacklistView.setEnabled(enabled);
        mAddWhitelistButton.setEnabled(enabled);
        mAddBlacklistButton.setEnabled(enabled);
    }

    private void showAddKeywordDialog(boolean isWhitelist) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(isWhitelist ? "添加白名单关键字" : "添加黑名单关键字");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入关键字");
        builder.setView(input);

        builder.setPositiveButton("添加", (dialog, which) -> {
            String keyword = input.getText().toString().trim();
            if (!keyword.isEmpty()) {
                if (isWhitelist) {
                    mWhitelistAdapter.add(keyword);
                } else {
                    mBlacklistAdapter.add(keyword);
                }
                mWhitelistAdapter.notifyDataSetChanged();
                mBlacklistAdapter.notifyDataSetChanged();
            } else {
                Toast.makeText(this, "关键字不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void testRegex() {
        String pattern = mRegexPatternEdit.getText().toString();
        if (pattern.isEmpty()) {
            Toast.makeText(this, "请先输入正则表达式", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!NotificationFilter.isValidRegex(pattern)) {
            Toast.makeText(this, "正则表达式格式错误", Toast.LENGTH_LONG).show();
            return;
        }

        // 显示测试对话框
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("测试正则表达式");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("请输入测试文本");
        builder.setView(input);

        builder.setPositiveButton("测试", (dialog, which) -> {
            String testText = input.getText().toString();
            boolean matches = java.util.regex.Pattern.compile(pattern).matcher(testText).find();
            String result = matches ? "匹配成功 ✓" : "不匹配 ✗";
            Toast.makeText(this, result, Toast.LENGTH_SHORT).show();
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void saveSettings() {
        // 验证正则表达式
        if (mRegexEnabledSwitch.isChecked()) {
            String pattern = mRegexPatternEdit.getText().toString();
            if (!pattern.isEmpty() && !NotificationFilter.isValidRegex(pattern)) {
                Toast.makeText(this, "正则表达式格式错误，请修正后再保存", Toast.LENGTH_LONG).show();
                return;
            }
        }

        // 保存设置
        mFilter.setFilterEnabled(mFilterEnabledSwitch.isChecked());
        mFilter.setFilterMode(mWhitelistRadio.isChecked() ?
            NotificationFilter.MODE_WHITELIST : NotificationFilter.MODE_BLACKLIST);

        mFilter.setRegexEnabled(mRegexEnabledSwitch.isChecked());
        mFilter.setRegexPattern(mRegexPatternEdit.getText().toString());

        // 保存关键字列表
        Set<String> whitelist = new java.util.HashSet<>();
        for (int i = 0; i < mWhitelistAdapter.getCount(); i++) {
            whitelist.add(mWhitelistAdapter.getItem(i));
        }
        mFilter.setWhitelist(whitelist);

        Set<String> blacklist = new java.util.HashSet<>();
        for (int i = 0; i < mBlacklistAdapter.getCount(); i++) {
            blacklist.add(mBlacklistAdapter.getItem(i));
        }
        mFilter.setBlacklist(blacklist);

        mFilter.saveSettings();

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 关键字列表适配器
     */
    private class KeywordAdapter extends ArrayAdapter<String> {
        private boolean isWhitelist;

        public KeywordAdapter(List<String> keywords, boolean isWhitelist) {
            super(FilterSettingsActivity.this, 0, keywords);
            this.isWhitelist = isWhitelist;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(
                    R.layout.item_keyword, parent, false);
            }

            String keyword = getItem(position);
            TextView keywordText = convertView.findViewById(R.id.keywordText);
            Button deleteButton = convertView.findViewById(R.id.deleteButton);

            keywordText.setText(keyword);
            deleteButton.setOnClickListener(v -> {
                remove(keyword);
                notifyDataSetChanged();
            });

            return convertView;
        }
    }
}
