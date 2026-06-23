package com.detetive.app;

import android.os.Bundle;
import android.view.WindowManager;

import com.detetive.app.plugins.NoirPlugin;
import com.detetive.app.plugins.SchedulingPlugin;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registerPlugin(NoirPlugin.class);
        registerPlugin(SchedulingPlugin.class);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
    }
}
