package com.example.htmlsandbox;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutList, layoutViewer;
    private RecyclerView recyclerView;
    private WebView webView;
    private MaterialToolbar toolbar, toolbarViewer;
    private FileAdapter adapter;
    private final List<FileAdapter.GroupItem> groups = new ArrayList<>();
    private File sandboxRoot;
    private boolean isViewerMode = false;

    private final ActivityResultLauncher<Uri> pickTree = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                importTree(uri);
            }
    );

    private final ActivityResultLauncher<String[]> pickFile = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            uri -> {
                if (uri == null) return;
                getContentResolver().takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION);
                importSingleFile(uri);
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sandboxRoot = new File(getFilesDir(), "sandbox");
        if (!sandboxRoot.exists()) sandboxRoot.mkdirs();

        layoutList = findViewById(R.id.layout_list);
        layoutViewer = findViewById(R.id.layout_viewer);
        recyclerView = findViewById(R.id.recycler_view);
        webView = findViewById(R.id.webview);
        toolbar = findViewById(R.id.toolbar);
        toolbarViewer = findViewById(R.id.toolbar_viewer);

        setSupportActionBar(toolbar);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileAdapter(groups, this::onFileClicked);
        recyclerView.setAdapter(adapter);

        configureWebView();

        toolbarViewer.setNavigationOnClickListener(v -> exitViewer());

        refreshFileList();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_import_dir) {
            pickTree.launch(null);
            return true;
        } else if (id == R.id.action_import_file) {
            pickFile.launch(new String[]{"text/html", "*/*"});
            return true;
        } else if (id == R.id.action_clear_all) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle("清除所有")
                    .setMessage("确定要清除所有导入的文件吗？")
                    .setPositiveButton("确定", (d, w) -> {
                        wipe(sandboxRoot);
                        sandboxRoot.mkdirs();
                        refreshFileList();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (isViewerMode) {
            exitViewer();
            return;
        }
        super.onBackPressed();
    }

    private void onFileClicked(FileItem item) {
        enterViewer(new File(item.path));
    }

    private void enterViewer(File file) {
        isViewerMode = true;
        layoutList.setVisibility(android.view.View.GONE);
        layoutViewer.setVisibility(android.view.View.VISIBLE);
        setSupportActionBar(toolbarViewer);
        loadHtml(file);
    }

    private void exitViewer() {
        isViewerMode = false;
        layoutViewer.setVisibility(android.view.View.GONE);
        layoutList.setVisibility(android.view.View.VISIBLE);
        setSupportActionBar(toolbar);
        webView.stopLoading();
        webView.loadUrl("about:blank");
        webView.clearHistory();
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

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                toolbarViewer.setTitle(view.getTitle() != null ? view.getTitle() : "HTML View");
            }
        });
    }

    private void importTree(Uri uri) {
        try {
            DocumentFile root = DocumentFile.fromTreeUri(this, uri);
            if (root == null || !root.isDirectory()) {
                toast("目录无效");
                return;
            }
            wipe(sandboxRoot);
            sandboxRoot.mkdirs();
            copyTree(root, sandboxRoot);
            refreshFileList();
            toast("已导入目录");
        } catch (Exception e) {
            toast("导入失败: " + e.getMessage());
        }
    }

    private void importSingleFile(Uri uri) {
        try {
            DocumentFile doc = DocumentFile.fromSingleUri(this, uri);
            if (doc == null) {
                toast("文件无效");
                return;
            }
            String name = doc.getName();
            if (name == null || name.isEmpty()) {
                name = "imported_" + System.currentTimeMillis() + ".html";
            }
            name = name.replace('/', '_').replace('\\', '_');
            File target = new File(sandboxRoot, name);
            if (target.getParentFile() != null) target.getParentFile().mkdirs();

            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) { toast("无法读取文件"); return; }
            OutputStream out = new FileOutputStream(target);
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
            out.close();
            in.close();

            refreshFileList();
            toast("已导入: " + name);
        } catch (Exception e) {
            toast("导入失败: " + e.getMessage());
        }
    }

    private void copyTree(DocumentFile source, File targetDir) throws Exception {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.isEmpty()) continue;
            name = name.replace('/', '_').replace('\\', '_');
            File target = new File(targetDir, name);
            if (child.isDirectory()) {
                target.mkdirs();
                copyTree(child, target);
            } else if (child.isFile()) {
                if (target.getParentFile() != null) target.getParentFile().mkdirs();
                InputStream in = getContentResolver().openInputStream(child.getUri());
                if (in == null) continue;
                OutputStream out = new FileOutputStream(target);
                byte[] buf = new byte[8192];
                int len;
                while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
                out.close();
                in.close();
            }
        }
    }

    private void refreshFileList() {
        groups.clear();
        Map<String, List<FileItem>> dirMap = new HashMap<>();
        scanFiles(sandboxRoot, "", dirMap);

        // Root-level files first
        List<FileItem> rootFiles = dirMap.remove("");
        if (rootFiles != null) {
            for (FileItem fi : rootFiles) {
                groups.add(new FileAdapter.GroupItem(fi));
            }
        }

        // Then directory groups, sorted by name
        List<String> dirNames = new ArrayList<>(dirMap.keySet());
        java.util.Collections.sort(dirNames);
        for (String dirName : dirNames) {
            groups.add(new FileAdapter.GroupItem(dirName, dirMap.get(dirName)));
        }

        adapter.notifyDataSetChanged();
    }

    private void scanFiles(File dir, String prefix, Map<String, List<FileItem>> dirMap) {
        File[] children = dir.listFiles();
        if (children == null) return;
        Arrays.sort(children, Comparator.comparing(File::getName));
        for (File child : children) {
            String name = child.getName().toLowerCase(Locale.ROOT);
            boolean isHtml = name.endsWith(".html") || name.endsWith(".htm");
            if (child.isDirectory()) {
                scanFiles(child, prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName(), dirMap);
            } else if (isHtml) {
                String groupKey = prefix;
                String relPath = prefix.isEmpty() ? child.getName() : prefix + "/" + child.getName();
                if (!dirMap.containsKey(groupKey)) {
                    dirMap.put(groupKey, new ArrayList<>());
                }
                dirMap.get(groupKey).add(new FileItem(child.getName(), child.getAbsolutePath(), relPath));
            }
        }
    }

    private void loadHtml(File file) {
        try {
            String html = readText(file);
            String base = "file://" + file.getParentFile().getAbsolutePath() + "/";
            String cache = "<meta http-equiv=\"Cache-Control\" content=\"no-cache, no-store, must-revalidate\"><meta http-equiv=\"Pragma\" content=\"no-cache\"><meta http-equiv=\"Expires\" content=\"0\">";
            html = injectHead(html, cache + "<base href=\"" + base + "\">");
            webView.loadDataWithBaseURL(base + "?t=" + System.currentTimeMillis(), html, "text/html", "UTF-8", null);
        } catch (Exception e) {
            toast("加载失败: " + e.getMessage());
        }
    }

    private String readText(File file) throws Exception {
        byte[] bytes = new byte[(int) file.length()];
        FileInputStream in = new FileInputStream(file);
        int ignored = in.read(bytes);
        in.close();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private WebResourceResponse loadFile(String path) {
        try {
            if (path == null) return null;
            File file = new File(path);
            if (!file.exists() || file.isDirectory()) return null;
            return new WebResourceResponse(mime(path), "UTF-8", new FileInputStream(file));
        } catch (Exception e) {
            return null;
        }
    }

    private String mime(String path) {
        String l = path.toLowerCase(Locale.ROOT);
        if (l.endsWith(".html") || l.endsWith(".htm")) return "text/html";
        if (l.endsWith(".css")) return "text/css";
        if (l.endsWith(".js")) return "application/javascript";
        if (l.endsWith(".png")) return "image/png";
        if (l.endsWith(".jpg") || l.endsWith(".jpeg")) return "image/jpeg";
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

    private void wipe(File file) {
        if (!file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) wipe(child);
        file.delete();
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    static class FileItem {
        String name;
        String path;
        String relativePath;

        FileItem(String name, String path, String relativePath) {
            this.name = name;
            this.path = path;
            this.relativePath = relativePath;
        }
    }
}
