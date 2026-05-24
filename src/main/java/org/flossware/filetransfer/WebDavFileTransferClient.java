package org.flossware.filetransfer;

import com.github.sardine.DavResource;
import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * FileTransferClient implementation for WebDAV (Web Distributed Authoring and Versioning).
 *
 * <p>Uses the Sardine library for HTTP-based distributed file access.
 * Supports both authenticated and anonymous access.</p>
 *
 * <h2>Example Usage</h2>
 * <pre>{@code
 * // Authenticated access
 * FileTransferClient webdav = WebDavFileTransferClient.builder()
 *     .baseUrl("https://webdav.example.com/files/")
 *     .username("user")
 *     .password("pass")
 *     .build();
 *
 * // Anonymous access
 * FileTransferClient webdav = WebDavFileTransferClient.builder()
 *     .baseUrl("https://public.example.com/files/")
 *     .build();
 *
 * try {
 *     byte[] data = webdav.readFile("document.pdf");
 *     List<String> files = webdav.list("uploads/");
 * } finally {
 *     webdav.close();
 * }
 * }</pre>
 */
public class WebDavFileTransferClient implements FileTransferClient {
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    private final String baseUrl;
    private final String username;
    private final String password;
    private final Sardine sardine;

    private WebDavFileTransferClient(String baseUrl, String username, String password) {
        Objects.requireNonNull(baseUrl, "baseUrl cannot be null");
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.username = username;
        this.password = password;

        if (username != null && password != null) {
            this.sardine = SardineFactory.begin(username, password);
        } else {
            this.sardine = SardineFactory.begin();
        }
    }

    private String resolveUrl(String path) {
        return baseUrl + path;
    }

    @Override
    public byte[] readFile(String path) throws IOException {
        String url = resolveUrl(path);

        try (InputStream in = sardine.get(url);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[DEFAULT_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            return out.toByteArray();
        }
    }

    @Override
    public InputStream openFile(String path) throws IOException {
        String url = resolveUrl(path);
        return sardine.get(url);
    }

    @Override
    public boolean exists(String path) throws IOException {
        String url = resolveUrl(path);
        return sardine.exists(url);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String url = resolveUrl(prefix);
        List<DavResource> resources = sardine.list(url);
        List<String> results = new ArrayList<>();

        for (DavResource resource : resources) {
            String resourcePath = resource.getPath();
            // Skip the directory itself
            if (!resourcePath.equals(prefix) && !resourcePath.equals(prefix + "/")) {
                // Extract relative path
                if (resourcePath.startsWith("/")) {
                    resourcePath = resourcePath.substring(1);
                }
                results.add(resourcePath);
            }
        }

        return results;
    }

    @Override
    public long getFileSize(String path) throws IOException {
        String url = resolveUrl(path);
        List<DavResource> resources = sardine.list(url);

        if (resources.isEmpty()) {
            throw new IOException("File not found: " + path);
        }

        DavResource resource = resources.get(0);
        if (resource.isDirectory()) {
            throw new IOException("Path is a directory: " + path);
        }

        Long size = resource.getContentLength();
        return size != null ? size : 0L;
    }

    @Override
    public String getDescription() {
        return "WebDAV[" + baseUrl + ", authenticated=" + (username != null) + "]";
    }

    @Override
    public void close() throws IOException {
        sardine.shutdown();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String baseUrl;
        private String username;
        private String password;

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public WebDavFileTransferClient build() {
            Objects.requireNonNull(baseUrl, "baseUrl must be set");
            return new WebDavFileTransferClient(baseUrl, username, password);
        }
    }
}
