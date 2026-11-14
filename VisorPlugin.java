package com.visorcbr.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.Window;
import android.view.WindowManager;

import androidx.activity.result.ActivityResult;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Locale;

@CapacitorPlugin(name = "VisorCBR")
public class VisorPlugin extends Plugin {

    private static final String[] COLS = {
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_SIZE
    };

    /* ---------- Carpeta fija (permiso que se conserva entre sesiones) ---------- */

    @PluginMethod
    public void pickFolder(PluginCall call) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(call, i, "pickFolderResult");
    }

    @ActivityCallback
    private void pickFolderResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("cancelado");
            return;
        }
        Uri tree = result.getData().getData();
        try {
            getContext().getContentResolver().takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) { }
        JSObject o = new JSObject();
        o.put("uri", tree.toString());
        o.put("name", docName(DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree)), "Carpeta"));
        call.resolve(o);
    }

    @PluginMethod
    public void scanFolder(final PluginCall call) {
        final String u = call.getString("uri");
        if (u == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try {
                Uri tree = Uri.parse(u);
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                String name = docName(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), "Carpeta");
                call.resolve(scan(tree, rootId, name, 0));
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private JSObject scan(Uri tree, String docId, String name, int depth) throws Exception {
        JSObject node = new JSObject();
        node.put("name", name);
        JSArray dirs = new JSArray();
        JSArray comics = new JSArray();
        Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        ContentResolver cr = getContext().getContentResolver();
        try (Cursor c = cr.query(children, COLS, null, null, null)) {
            if (c != null) {
                while (c.moveToNext()) {
                    String id = c.getString(0);
                    String n = c.getString(1);
                    String mime = c.getString(2);
                    long size = c.isNull(3) ? 0 : c.getLong(3);
                    if (n == null || n.startsWith(".")) continue;
                    if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) {
                        if (depth < 12) dirs.put(scan(tree, id, n, depth + 1));
                    } else if (isComic(n)) {
                        JSObject o = new JSObject();
                        o.put("name", n);
                        o.put("size", size);
                        o.put("uri", DocumentsContract.buildDocumentUriUsingTree(tree, id).toString());
                        comics.put(o);
                    }
                }
            }
        }
        node.put("dirs", dirs);
        node.put("comics", comics);
        return node;
    }

    private boolean isComic(String n) {
        String l = n.toLowerCase(Locale.ROOT);
        return l.endsWith(".cbr") || l.endsWith(".cbz") || l.endsWith(".rar") || l.endsWith(".zip");
    }

    private String docName(Uri uri, String def) {
        try (Cursor c = getContext().getContentResolver().query(uri,
                new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst() && c.getString(0) != null) return c.getString(0);
        } catch (Exception ignored) { }
        return def;
    }

    /* ---------- Leer archivos ---------- */

    /** Copia el cómic a la caché de la app y devuelve su ruta (la página lo lee desde ahí). */
    @PluginMethod
    public void copyToCache(final PluginCall call) {
        final String u = call.getString("uri");
        final String name = call.getString("name", "comic");
        if (u == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try {
                File dir = new File(getContext().getCacheDir(), "comics");
                dir.mkdirs();
                File[] old = dir.listFiles();
                if (old != null) {
                    Arrays.sort(old, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    for (int k = 2; k < old.length; k++) old[k].delete(); // deja solo los 2 más recientes
                }
                Uri uri = Uri.parse(u);
                String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
                File out = new File(dir, Math.abs(u.hashCode()) + "_" + safe);
                long expect = querySize(uri);
                if (!(out.exists() && expect > 0 && out.length() == expect)) {
                    try (InputStream in = getContext().getContentResolver().openInputStream(uri);
                         OutputStream os = new FileOutputStream(out)) {
                        if (in == null) throw new Exception("no se pudo abrir el archivo");
                        byte[] buf = new byte[1 << 16];
                        int n;
                        while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                    }
                }
                out.setLastModified(System.currentTimeMillis());
                JSObject r = new JSObject();
                r.put("path", out.getAbsolutePath());
                r.put("size", out.length());
                call.resolve(r);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    private long querySize(Uri uri) {
        try (Cursor c = getContext().getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (c != null && c.moveToFirst() && !c.isNull(0)) return c.getLong(0);
        } catch (Exception ignored) { }
        return -1;
    }

    /** Dice si es ZIP o RAR mirando solo los primeros bytes (sin copiar el archivo). */
    @PluginMethod
    public void sniff(final PluginCall call) {
        final String u = call.getString("uri");
        if (u == null) { call.reject("falta uri"); return; }
        new Thread(() -> {
            try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(u))) {
                byte[] b = new byte[4];
                int n = in == null ? 0 : in.read(b);
                String kind = "?";
                if (n >= 2 && b[0] == 0x50 && b[1] == 0x4B) kind = "ZIP";
                else if (n >= 4 && b[0] == 0x52 && b[1] == 0x61 && b[2] == 0x72 && b[3] == 0x21) kind = "RAR";
                JSObject r = new JSObject();
                r.put("kind", kind);
                call.resolve(r);
            } catch (Exception e) {
                call.reject(String.valueOf(e.getMessage()));
            }
        }).start();
    }

    /* ---------- "Abrir con…" desde el gestor de archivos ---------- */

    private JSObject fileFromIntent(Intent it) {
        if (it == null || !Intent.ACTION_VIEW.equals(it.getAction()) || it.getData() == null) return null;
        Uri u = it.getData();
        String name = null;
        try (Cursor c = getContext().getContentResolver().query(u, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Exception ignored) { }
        if (name == null) name = u.getLastPathSegment() != null ? u.getLastPathSegment() : "comic.cbr";
        JSObject o = new JSObject();
        o.put("uri", u.toString());
        o.put("name", name);
        return o;
    }

    @PluginMethod
    public void getIntentFile(PluginCall call) {
        Intent it = getActivity().getIntent();
        JSObject f = fileFromIntent(it);
        if (f != null) {
            it.setData(null);
            it.setAction(Intent.ACTION_MAIN);
            call.resolve(f);
        } else {
            call.resolve(new JSObject());
        }
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        JSObject f = fileFromIntent(intent);
        if (f != null) notifyListeners("fileIntent", f, true);
    }

    /* ---------- Pantalla y teclas ---------- */

    @PluginMethod
    public void immersive(final PluginCall call) {
        final boolean on = call.getBoolean("on", false);
        getActivity().runOnUiThread(() -> {
            Window w = getActivity().getWindow();
            WindowInsetsControllerCompat c = WindowCompat.getInsetsController(w, w.getDecorView());
            if (on) {
                c.hide(WindowInsetsCompat.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            } else {
                c.show(WindowInsetsCompat.Type.systemBars());
            }
            call.resolve();
        });
    }

    @PluginMethod
    public void keepAwake(final PluginCall call) {
        final boolean on = call.getBoolean("on", false);
        getActivity().runOnUiThread(() -> {
            Window w = getActivity().getWindow();
            if (on) w.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            else w.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            call.resolve();
        });
    }

    @PluginMethod
    public void captureVolume(PluginCall call) {
        MainActivity.captureVolume = call.getBoolean("on", false);
        call.resolve();
    }
}
