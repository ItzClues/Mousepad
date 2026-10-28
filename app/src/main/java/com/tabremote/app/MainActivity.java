package com.tabremote.app;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public class MainActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(48, 48, 48, 48);
        l.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(this);
        t.setTextSize(18);
        l.addView(t);
        Button btn = new Button(this);
        btn.setText("Accessibility settings kholain");
        btn.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        l.addView(btn);
        setContentView(l);
        int ip = ((WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE)).getConnectionInfo().getIpAddress();
        String addr = (ip & 0xff) + "." + ((ip >> 8) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 24) & 0xff);
        if (ip == 0) addr = "(WiFi/hotspot se connect karain)";
        t.setText("1) Neeche button dabayen, \"Tab Remote\" chunen aur ON karen.\n\n2) Tablet ke browser mein yeh address kholen:\n\nhttp://" + addr + ":" + RemoteService.PORT + "\n\nDono device ek hi WiFi par hon.");
    }
}
