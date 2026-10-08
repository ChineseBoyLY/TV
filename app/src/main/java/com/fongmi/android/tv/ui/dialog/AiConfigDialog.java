package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.fongmi.android.tv.setting.AiSetting;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class AiConfigDialog {
    private AiConfigDialog() {}

    public static void show(Context context, Runnable onSaved) {
        LinearLayout box = new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL); int pad = 24; box.setPadding(pad, 0, pad, 0);
        EditText url = field(context, "AI 地址，例如 https://api.openai.com/v1", AiSetting.getBaseUrl(), false);
        EditText key = field(context, "API Key", AiSetting.getApiKey(), true);
        EditText model = field(context, "模型，例如 gpt-4o-mini", AiSetting.getModel(), false);
        box.addView(url); box.addView(key); box.addView(model);
        new MaterialAlertDialogBuilder(context).setTitle("AI 设置").setView(box).setNegativeButton(android.R.string.cancel, null).setPositiveButton(android.R.string.ok, (d, w) -> {
            AiSetting.putBaseUrl(url.getText().toString()); AiSetting.putApiKey(key.getText().toString()); AiSetting.putModel(model.getText().toString());
            if (onSaved != null) onSaved.run();
        }).show();
    }

    private static EditText field(Context context, String hint, String value, boolean secret) {
        EditText field = new EditText(context); field.setHint(hint); field.setText(value); field.setSingleLine(true); field.setInputType(secret ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI); return field;
    }
}
