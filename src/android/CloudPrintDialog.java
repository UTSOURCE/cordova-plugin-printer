/*
    Copyright 2013-2014 appPlant UG

    Licensed to the Apache Software Foundation (ASF) under one
    or more contributor license agreements.  See the NOTICE file
    distributed with this work for additional information
    regarding copyright ownership.  The ASF licenses this file
    to you under the Apache License, Version 2.0 (the
    "License"); you may not use this file except in compliance
    with the License.  You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing,
    software distributed under the License is distributed on an
    "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
    KIND, either express or implied.  See the License for the
    specific language governing permissions and limitations
    under the License.
 */

package de.appplant.cordova.plugin.printer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/**
 * Custom activity to open the web based
 * Google Cloud Print dialog.
 */
public class CloudPrintDialog extends Activity {

    /**
     * URL to the web based Google Cloud Print dialog
     */
    private static final String PRINT_DIALOG_URL =
            "https://www.google.com/cloudprint/dialog.html";

    /**
     * Javascript interface prefix
     */
    private static final String JS_INTERFACE =
            "Printer";

    /**
     * Post message that is sent by Print Dialog web page
     * when the printing dialog needs to be closed.
     */
    private static final String CLOSE_POST_MESSAGE_NAME =
            "cp-dialog-on-close";

    /**
     * Intent that started the action.
     */
    Intent cloudPrintIntent;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        init();
    }

    /**
     * Initializes the activity.
     * Creates the web view and its client + JS interface.
     */
    @SuppressLint("AddJavascriptInterface")
    private void init() {
        WebView webView = new WebView(this);
        WebSettings settings = webView.getSettings();

        cloudPrintIntent = this.getIntent();

        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        webView.setScrollbarFadingEnabled(false);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setJavaScriptEnabled(true);

        addContentView(webView, new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
        );

        webView.setWebViewClient(new PrintDialogWebClient());

        webView.addJavascriptInterface(
                new PrintDialogJavaScriptInterface(), JS_INTERFACE);

        webView.loadUrl(PRINT_DIALOG_URL);
    }

    /**
     * Client to load the google cloud print page
     * once the page is ready.
     */
    private final class PrintDialogWebClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {

            if (!PRINT_DIALOG_URL.equals(url))
                return;

            // Submit print document.
            view.loadUrl("javascript:printDialog.setPrintDocument(" +
                "printDialog.createPrintDocument(" +
                "window." + JS_INTERFACE + ".getType()," +
                "window." + JS_INTERFACE + ".getTitle()," +
                "window." + JS_INTERFACE + ".getContent()))");

            // Add post messages listener.
            view.loadUrl("javascript:window.addEventListener('message'," +
                "function(evt){window." + JS_INTERFACE + ".onPostMessage(evt.data)}, false)");
        }
    }

    /**
     * Interface allows the Java object's methods
     * to be accessible from JavaScript.
     */
    final class PrintDialogJavaScriptInterface {

        @JavascriptInterface
        @SuppressWarnings("UnusedDeclaration")
        public String getType() {
            return "dataUrl";
        }

        @JavascriptInterface
        @SuppressWarnings("UnusedDeclaration")
        public String getTitle() {
            return cloudPrintIntent.getExtras().getString("title");
        }

        /**
         * @return
         *      Converted byte stream as base64 encoded string.
         */
        @JavascriptInterface
        @SuppressWarnings("UnusedDeclaration")
        public String getContent() {
            try {
                ContentResolver contentResolver;
                InputStream in;
                ByteArrayOutputStream out;

                contentResolver = getContentResolver();
                in = contentResolver.openInputStream(cloudPrintIntent.getData());
                out = new ByteArrayOutputStream();

                byte[] buffer = new byte[4096];
                int n = in.read(buffer);

                while (n >= 0) {
                    out.write(buffer, 0, n);
                    n = in.read(buffer);
                }

                in.close();
                out.flush();

                String contentBase64 =
                        Base64.encodeToString(out.toByteArray(), Base64.DEFAULT);

                return "data:" + cloudPrintIntent.getType() +
                       ";base64," + contentBase64;

            } catch (Exception e) {
                e.printStackTrace();
            }

            return "";
        }

        /**
         * JS interface to get informed when the job is finished
         * and the view should be closed.
         *
         * @param message
         *      The fired event name
         */
        @JavascriptInterface
        @SuppressWarnings("UnusedDeclaration")
        public void onPostMessage(String message) {
            if (message.startsWith(CLOSE_POST_MESSAGE_NAME)) {
                finish();
                //finishActivity(0);
            }
        }
    }
}
