# JFileTransfer

Universal file transfer abstraction library for Java. Provides a simple, unified API for reading files from SFTP, WebDAV, SMB/CIFS, FTP, and FTPS servers.

## Features

- ✅ **Unified API** - Single interface for all file transfer protocols
- ✅ **4 Protocol Families** - SFTP, WebDAV, SMB/CIFS, FTP/FTPS
- ✅ **Builder Pattern** - Fluent, type-safe configuration
- ✅ **Optional Dependencies** - Include only the protocols you need
- ✅ **Thread-Safe** - Concurrent read operations supported
- ✅ **AutoCloseable** - Proper resource management
- ✅ **Minimal Dependencies** - Java 11+, protocol libraries are optional

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>org.flossware</groupId>
    <artifactId>jfiletransfer</artifactId>
    <version>1.0</version>
</dependency>

<!-- Add protocol SDK (only include what you need) -->
<dependency>
    <groupId>com.github.mwiede</groupId>
    <artifactId>jsch</artifactId>
    <version>0.2.21</version>
</dependency>
```

### Basic Usage

```java
import org.flossware.filetransfer.FileTransferClient;
import org.flossware.filetransfer.SftpFileTransferClient;

// Create SFTP client
FileTransferClient client = SftpFileTransferClient.builder()
    .host("sftp.example.com")
    .port(22)
    .username("user")
    .password("secret")
    .basePath("/remote/files")
    .build();

// Read a file
byte[] data = client.readFile("document.pdf");

// Check if file exists
if (client.exists("report.xlsx")) {
    System.out.println("File exists!");
}

// List files
List<String> files = client.list("uploads/");

// Clean up
client.close();
```

## Supported Protocols

### SFTP (SSH File Transfer Protocol)

```java
FileTransferClient sftp = SftpFileTransferClient.builder()
    .host("sftp.example.com")
    .port(22)
    .username("deploy")
    .password("secret")
    .basePath("/app/files")
    .build();
```

**Password authentication:**
```java
.username("user")
.password("secret")
```

**Private key authentication:**
```java
.username("deploy")
.privateKey("/home/user/.ssh/id_rsa")
```

**Dependency:**
```xml
<dependency>
    <groupId>com.github.mwiede</groupId>
    <artifactId>jsch</artifactId>
    <version>0.2.21</version>
</dependency>
```

### WebDAV (Web Distributed Authoring and Versioning)

```java
// Authenticated access
FileTransferClient webdav = WebDavFileTransferClient.builder()
    .baseUrl("https://webdav.example.com/files/")
    .username("user")
    .password("pass")
    .build();

// Anonymous access
FileTransferClient webdav = WebDavFileTransferClient.builder()
    .baseUrl("https://public.example.com/files/")
    .build();
```

**Dependency:**
```xml
<dependency>
    <groupId>com.github.lookfirst</groupId>
    <artifactId>sardine</artifactId>
    <version>5.13</version>
</dependency>
```

### SMB/CIFS (Windows File Shares / Samba)

```java
// Domain authentication
FileTransferClient smb = SmbFileTransferClient.builder()
    .server("smb://server.example.com/share/")
    .domain("CORP")
    .username("user")
    .password("pass")
    .build();

// Workgroup authentication
FileTransferClient smb = SmbFileTransferClient.builder()
    .server("smb://192.168.1.100/Public/")
    .username("guest")
    .password("")
    .build();
```

**Dependency:**
```xml
<dependency>
    <groupId>eu.agno3.jcifs</groupId>
    <artifactId>jcifs-ng</artifactId>
    <version>2.1.10</version>
</dependency>
```

### FTP / FTPS (File Transfer Protocol)

```java
// FTP
FileTransferClient ftp = FtpFileTransferClient.builder()
    .server("ftp://ftp.example.com/files/")
    .username("user")
    .password("pass")
    .build();

// FTPS (FTP over SSL/TLS)
FileTransferClient ftps = FtpFileTransferClient.builder()
    .server("ftps://secure.example.com/files/")
    .username("user")
    .password("pass")
    .build();

// Anonymous FTP
FileTransferClient ftp = FtpFileTransferClient.builder()
    .server("ftp://public.example.com/downloads/")
    .build();
```

**Note:** Uses Java's built-in FTP URL handler. Directory listing not supported.

## API Reference

```java
public interface FileTransferClient extends AutoCloseable {
    byte[] readFile(String path) throws IOException;
    InputStream openFile(String path) throws IOException;
    boolean exists(String path) throws IOException;
    List<String> list(String prefix) throws IOException;  // Not supported for FTP
    long getFileSize(String path) throws IOException;
    String getDescription();
    void close() throws IOException;
}
```

## Common Use Cases

### Backup & Sync

```java
FileTransferClient source = SftpFileTransferClient.builder()
    .host("server1.example.com")
    .username("backup")
    .privateKey("/home/user/.ssh/backup_key")
    .basePath("/data")
    .build();

FileTransferClient destination = SmbFileTransferClient.builder()
    .server("smb://backup-server/backups/")
    .domain("CORP")
    .username("backup-service")
    .password("secret")
    .build();

// Copy file
byte[] data = source.readFile("important.dat");
// ... write to destination
```

### File Migration

```java
FileTransferClient oldServer = FtpFileTransferClient.builder()
    .server("ftp://old-server.com/files/")
    .username("migrate")
    .password("pass")
    .build();

FileTransferClient newServer = WebDavFileTransferClient.builder()
    .baseUrl("https://new-server.com/webdav/")
    .username("admin")
    .password("secret")
    .build();

List<String> files = oldServer.list("archive/");
for (String file : files) {
    byte[] data = oldServer.readFile(file);
    // ... write to new server
}
```

### Deployment

```java
FileTransferClient deployment = SftpFileTransferClient.builder()
    .host("prod-server.example.com")
    .username("deploy")
    .privateKey("/secrets/deploy_key")
    .basePath("/var/www/app")
    .build();

// Deploy artifacts
byte[] artifact = Files.readAllBytes(Paths.get("target/app.jar"));
// ... write via deployment client
```

## Protocol Comparison

| Protocol | Speed | Auth | Directory Listing | Best For |
|----------|-------|------|-------------------|----------|
| **SFTP** | ⚡⚡⚡⚡ | SSH Key/Password | ✅ Yes | Secure file transfer, Linux/Unix servers |
| **WebDAV** | ⚡⚡⚡ | HTTP Basic/Digest | ✅ Yes | HTTP-based, cloud storage (Nextcloud, OwnCloud) |
| **SMB/CIFS** | ⚡⚡⚡⚡ | NTLM/Domain | ✅ Yes | Windows file shares, Samba servers |
| **FTP** | ⚡⚡⚡ | Username/Password | ❌ No | Simple file transfer, public downloads |
| **FTPS** | ⚡⚡⚡ | Username/Password | ❌ No | Secure FTP, legacy systems |

## Best Practices

1. **Always use try-with-resources or close():**
   ```java
   try (FileTransferClient client = builder.build()) {
       byte[] data = client.readFile("file.txt");
   }
   ```

2. **Use SFTP or FTPS for secure transfers:**
   ```java
   // Good - encrypted
   .server("ftps://secure.example.com/")
   
   // Bad - unencrypted
   .server("ftp://insecure.example.com/")
   ```

3. **Prefer SSH keys over passwords for SFTP:**
   ```java
   // Better
   .privateKey("/home/user/.ssh/id_rsa")
   
   // Less secure
   .password("hardcoded-password")
   ```

4. **Handle exceptions appropriately:**
   ```java
   try {
       byte[] data = client.readFile("file.txt");
   } catch (IOException e) {
       logger.error("Failed to read file", e);
   }
   ```

## Versioning and Releases

This project uses **X.Y semantic versioning** (e.g., 1.0, 1.1, 2.0). Versions are automatically incremented on commits to the main branch and published to packagecloud.io.

### Maven Repository

```xml
<repositories>
    <repository>
        <id>packagecloud-flossware</id>
        <url>https://packagecloud.io/flossware/java/maven2</url>
    </repository>
</repositories>
```

## Building from Source

```bash
git clone https://github.com/FlossWare/jfiletransfer.git
cd jfiletransfer
mvn clean install
```

## License

Apache License 2.0

## Related Projects

- [jcloudstorage](https://github.com/FlossWare/jcloudstorage) - Cloud storage abstraction (S3, Azure, GCS, Google Drive, Dropbox, OneDrive)
- [jmessaging](https://github.com/FlossWare/jmessaging) - Messaging abstraction (Kafka, RabbitMQ, Redis)
- [jcontainer](https://github.com/FlossWare/jcontainer) - Container abstraction (Kubernetes, Docker, Hazelcast)
- [jvcs](https://github.com/FlossWare/jvcs) - Version control abstraction (Git)
- [jclassloader](https://github.com/FlossWare/jclassloader) - Dynamic class loading from 34+ transport protocols
