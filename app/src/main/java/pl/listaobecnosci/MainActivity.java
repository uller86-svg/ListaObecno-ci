package pl.listaobecnosci;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;

import java.io.OutputStream;

public class MainActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);   // zapamiętuje listę pracowników
        s.setAllowFileAccess(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    /** Zapis raportu .xlsx do folderu Pobrane (Download) na telefonie. */
    private class Bridge {
        @JavascriptInterface
        public String saveXlsx(String name, String base64) {
            try {
                byte[] data = Base64.decode(base64, Base64.DEFAULT);
                ContentResolver r = getContentResolver();
                Uri coll = MediaStore.Downloads.EXTERNAL_CONTENT_URI;

                // ten sam dzień = nadpisz wcześniejszy raport (jeśli to nasz plik)
                try (Cursor c = r.query(coll,
                        new String[]{MediaStore.MediaColumns._ID},
                        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND "
                                + MediaStore.MediaColumns.RELATIVE_PATH + "=?",
                        new String[]{name, Environment.DIRECTORY_DOWNLOADS + "/"}, null)) {
                    while (c != null && c.moveToNext()) {
                        try {
                            r.delete(ContentUris.withAppendedId(coll, c.getLong(0)), null, null);
                        } catch (Exception ignored) { }
                    }
                } catch (Exception ignored) { }

                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                v.put(MediaStore.MediaColumns.IS_PENDING, 1);
                Uri u = r.insert(coll, v);
                if (u == null) return "ERR: nie można utworzyć pliku";
                try (OutputStream o = r.openOutputStream(u)) {
                    o.write(data);
                }
                v.clear();
                v.put(MediaStore.MediaColumns.IS_PENDING, 0);
                r.update(u, v, null, null);
                return "OK:" + name;
            } catch (Exception e) {
                return "ERR: " + e.getMessage();
            }
        }
    }
}
