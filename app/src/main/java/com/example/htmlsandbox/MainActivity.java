package com.example.htmlsandbox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private WebView webView;
    private TextView statusView;
    private File sandboxRoot;

    private final ActivityResultLauncher<Uri> pickTree = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                importTree(uri);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sandboxRoot = new File(getFilesDir(), "sandbox");
        if (!sandboxRoot.exists()) sandboxRoot.mkdirs();

        webView = findViewById(R.id.webview);
        statusView = findViewById(R.id.tv_status);
        Button btnImport = findViewById(R.id.btn_import);
        Button btnOpen = findViewById(R.id.btn_open);
        Button btnReload = findViewById(R.id.btn_reload);

        configureWebView();

        btnImport.setOnClickListener(v -> pickTree.launch(null));
        btnOpen.setOnClickListener(v -> openSandbox());
        btnReload.setOnClickListener(v -> reloadSandbox());

        File index = findIndex(sandboxRoot);
        if (index != null) loadHtml(index);
    }

    private void configureWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if ("file".equalsIgnoreCase(uri.getScheme()))
                    return loadFile(uri.getPath());
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });
    }

    private void importTree(Uri uri) {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, uri);
            if (root == null || !root.isDirectory()) { toast("目录无效"); return; }
            wipe(sandboxRoot); sandboxRoot.mkdirs();
            copyTree(root, sandboxRoot);
            reloadSandbox();
            toast("已导入到私有沙箱");
        } catch (Exception e) { toast("导入失败: " + e.getMessage()); }
    }

    private void copyTree(DocumentFile source, File targetDir) throws Exception {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.isEmpty()) continue;
            name = name.replace('/', '_').replace('\\', '_');
            File target = new File(targetDir, name);
            if (child.isDirectory()) { target.mkdirs(); copyTree(child, target); }
            else if (child.isFile()) {
                if (target.getParentFile() != null) target.getParentFile().mkdirs();
                InputStream in = getContentResolver().openInputStream(child.getUri());
                if (in == null) continue;
                OutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[8192]; int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                out.close(); in.close();
            }
        }
    }

    private void openSandbox() {
        File index = findIndex(sandboxRoot);
        if (index == null) { toast("沙箱里没有 index.html"); return; }
        loadHtml(index);
    }

    private void reloadSandbox() {
        webView.reload();
        File index = findIndex(sandboxRoot);
        if (index != null) loadHtml(index);
        else statusView.setText("沙箱已就绪，但没有 index.html");
    }

    private void loadHtml(File file) {
        try {
            String html = readText(file);
            String base = "file://" + file.getParentFile().getAbsolutePath() + "/";
            String cache = "<meta http-equiv=\"Cache-Control\" content=\"no-cache, no-store, must-revalidate\"><meta http-equiv=\"Pragma\" content=\"no-cache\"><meta http-equiv=\"Expires\" content=\"0\">";
            html = injectHead(html, cache + "<base href=\"" + base + "\">");
            webView.loadDataWithBaseURL(base + "?t=" + System.currentTimeMillis(), html, "text/html", "UTF-8", null);
            statusView.setText("已加载: " + file.getAbsolutePath());
        } catch (Exception e) { toast("加载失败: " + e.getMessage()); }
    }

    private String readText(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        int ignored = in.read(bytes); in.close();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private WebResourceResponse loadFile(String path) {
        try {
            if (path == null) return null;
            File file = new File(path);
            if (!file.exists() || file.isDirectory()) return null;
            return new WebResourceResponse(mime(path), "UTF-8", new FileInputStream(file));
        } catch (Exception e) { return null; }
    }

    private String mime(String path) {
        String l = path.toLowerCase(Locale.ROOT);
        if (l.endsWith(".html")||l.endsWith(".htm")) return "text/html";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg")||l.endsWith(".jpeg")) return "image/jpeg";
        if (l.endsWith(".gif")) return "image/gif";
        if (l.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    private String injectHead(String html, String tag) {
        int head = html.toLowerCase(Locale.ROOT).indexOf("<head>");
        if (head >= 0)
            return html.substring(0, head + 6) + tag + html.substring(head + 6);
        return "<head>" + tag + "</head>" + html;
    }

    private File findIndex(File dir) {
        File html = new File(dir, "index.html");
        if (html.exists()) return html;
        File htm = new File(dir, "index.htm");
        if (htm.exists()) return htm;
        File[] children = dir.listFiles();
        if (children == null) return null;
        for (File child : children) {
            if (child.isDirectory()) {
                File found = findIndex(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void wipe(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) wipe(child);
        file.delete();
    }

    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
}
