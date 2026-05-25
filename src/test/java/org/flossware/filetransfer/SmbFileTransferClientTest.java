package org.flossware.filetransfer;

import jcifs.smb.SmbFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.MockedConstruction;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for SmbFileTransferClient to achieve 100% coverage.
 * Note: Most methods require jcifs connection which needs a live SMB server or complex mocking.
 * These tests focus on builder validation, configuration, and URL resolution logic.
 */
class SmbFileTransferClientTest {

    private SmbFileTransferClient client;

    @AfterEach
    void tearDown() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // Ignore close errors in tearDown
            }
        }
    }

    @Test
    @DisplayName("Should support builder chaining")
    void testBuilderChaining() {
        SmbFileTransferClient.Builder builder = SmbFileTransferClient.builder();
        assertSame(builder, builder.server("smb://server.com/share/"));
        assertSame(builder, builder.domain("DOMAIN"));
        assertSame(builder, builder.username("user"));
        assertSame(builder, builder.password("pass"));
        assertSame(builder, builder.credentials("user", "pass"));
        assertSame(builder, builder.credentials("DOMAIN", "user", "pass"));
    }

    @Test
    @DisplayName("Should throw NullPointerException when server is null in builder")
    void testBuilderNullServer() {
        assertThrows(NullPointerException.class,
            () -> SmbFileTransferClient.builder()
                .username("user")
                .password("pass")
                .build());
    }

    @Test
    @DisplayName("Should build client with domain authentication")
    void testBuildWithDomain() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .domain("DOMAIN")
            .username("user")
            .password("pass")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("DOMAIN\\"));
        assertTrue(description.contains("user@"));
    }

    @Test
    @DisplayName("Should build client with workgroup authentication")
    void testBuildWithWorkgroup() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://192.168.1.100/Public/")
            .username("guest")
            .password("")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("guest@"));
    }

    @Test
    @DisplayName("Should build client without authentication")
    void testBuildWithoutAuth() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("smb://server.com/share/"));
    }

    @Test
    @DisplayName("Should add trailing slash to server URL if missing")
    void testServerTrailingSlash() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("smb://server.com/share/"));
    }

    @Test
    @DisplayName("Should not add extra trailing slash if already present")
    void testServerWithTrailingSlash() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        String description = client.getDescription();
        assertFalse(description.contains("smb://server.com/share//"));
    }

    @Test
    @DisplayName("Should resolve URL without credentials")
    void testResolveUrlWithoutCredentials() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        Method resolveUrl = SmbFileTransferClient.class.getDeclaredMethod("resolveUrl", String.class);
        resolveUrl.setAccessible(true);

        String result = (String) resolveUrl.invoke(client, "test.txt");
        assertEquals("smb://server.com/share/test.txt", result);
    }

    @Test
    @DisplayName("Should resolve URL with path")
    void testResolveUrlWithPath() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        Method resolveUrl = SmbFileTransferClient.class.getDeclaredMethod("resolveUrl", String.class);
        resolveUrl.setAccessible(true);

        String result = (String) resolveUrl.invoke(client, "documents/report.pdf");
        assertEquals("smb://server.com/share/documents/report.pdf", result);
    }

    @Test
    @DisplayName("Should return description with domain and username")
    void testGetDescriptionWithDomainAndUsername() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .domain("DOMAIN")
            .username("user")
            .password("pass")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("DOMAIN\\user@"));
        assertTrue(description.contains("smb://server.com/share/"));
    }

    @Test
    @DisplayName("Should return description with username only")
    void testGetDescriptionWithUsernameOnly() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .username("user")
            .password("pass")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("user@"));
        assertFalse(description.contains("\\"));
    }

    @Test
    @DisplayName("Should return description without credentials")
    void testGetDescriptionNoCredentials() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("SMB["));
        assertTrue(description.contains("smb://server.com/share/"));
        assertFalse(description.contains("@"));
        assertFalse(description.contains("\\"));
    }

    @Test
    @DisplayName("Should close without error")
    void testClose() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .build();

        assertDoesNotThrow(() -> client.close());
    }

    @Test
    @DisplayName("Should throw NullPointerException when constructor receives null server")
    void testConstructorNullServer() throws Exception {
        java.lang.reflect.Constructor<SmbFileTransferClient> constructor =
            SmbFileTransferClient.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);

        java.lang.reflect.InvocationTargetException exception = assertThrows(
            java.lang.reflect.InvocationTargetException.class,
            () -> constructor.newInstance(null, null, null, null));

        assertTrue(exception.getCause() instanceof NullPointerException);
        assertTrue(exception.getCause().getMessage().contains("server cannot be null"));
    }

    @Test
    @DisplayName("Should handle null domain in constructor")
    void testConstructorNullDomain() throws Exception {
        client = createTestClient("smb://server.com/share/", null, null, null);
        assertNotNull(client);
        String description = client.getDescription();
        assertFalse(description.contains("\\"));
    }

    @Test
    @DisplayName("Should handle null username and password in constructor")
    void testConstructorNullCredentials() throws Exception {
        client = createTestClient("smb://server.com/share/", null, null, null);
        assertNotNull(client);
        String description = client.getDescription();
        assertFalse(description.contains("@"));
    }

    @Test
    @DisplayName("Should use credentials builder method with username and password")
    void testCredentialsBuilderTwoArgs() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .credentials("user", "pass")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("user@"));
    }

    @Test
    @DisplayName("Should use credentials builder method with domain, username, and password")
    void testCredentialsBuilderThreeArgs() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .credentials("DOMAIN", "user", "pass")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("DOMAIN\\user@"));
    }

    @Test
    @DisplayName("Should verify DEFAULT_BUFFER_SIZE constant")
    void testConstantBufferSize() throws Exception {
        java.lang.reflect.Field bufferSize = SmbFileTransferClient.class.getDeclaredField("DEFAULT_BUFFER_SIZE");
        bufferSize.setAccessible(true);
        assertEquals(8192, bufferSize.get(null));
    }

    @Test
    @DisplayName("Should handle server with IP address")
    void testServerWithIpAddress() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://192.168.1.100/Public/")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("192.168.1.100"));
    }

    @Test
    @DisplayName("Should handle server with custom port")
    void testServerWithCustomPort() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com:445/share/")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains(":445"));
    }

    @Test
    @DisplayName("Should handle empty password")
    void testEmptyPassword() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .username("guest")
            .password("")
            .build();

        assertNotNull(client);
        String description = client.getDescription();
        assertTrue(description.contains("guest@"));
    }

    @Test
    @DisplayName("Should handle username with domain specified separately")
    void testUsernameWithSeparateDomain() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .domain("WORKGROUP")
            .username("user")
            .password("pass")
            .build();

        String description = client.getDescription();
        assertTrue(description.contains("WORKGROUP\\"));
        assertTrue(description.contains("user@"));
    }

    // Tests for actual SMB operations using mocked SmbFile

    @Test
    @DisplayName("Should read file successfully")
    void testReadFileSuccess() throws Exception {
        byte[] fileContent = "test file content".getBytes();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent);

        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.getInputStream()).thenReturn(inputStream);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            byte[] result = client.readFile("test.txt");
            assertArrayEquals(fileContent, result);
        }
    }

    @Test
    @DisplayName("Should throw IOException when readFile fails")
    void testReadFileFailure() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.getInputStream()).thenThrow(new IOException("File not found"));
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            IOException thrown = assertThrows(IOException.class, () -> client.readFile("missing.txt"));
            assertTrue(thrown.getMessage().contains("File not found"));
        }
    }

    @Test
    @DisplayName("Should open file successfully")
    void testOpenFileSuccess() throws Exception {
        ByteArrayInputStream inputStream = new ByteArrayInputStream("test".getBytes());

        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.getInputStream()).thenReturn(inputStream);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            InputStream result = client.openFile("test.txt");
            assertSame(inputStream, result);
        }
    }

    @Test
    @DisplayName("Should throw IOException when openFile fails")
    void testOpenFileFailure() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.getInputStream()).thenThrow(new IOException("Cannot open file"));
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            IOException thrown = assertThrows(IOException.class, () -> client.openFile("test.txt"));
            assertTrue(thrown.getMessage().contains("Cannot open file"));
        }
    }

    @Test
    @DisplayName("Should return true when file exists")
    void testExistsTrue() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(true);
            when(mock.isDirectory()).thenReturn(false);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            assertTrue(client.exists("test.txt"));
        }
    }

    @Test
    @DisplayName("Should return false when file does not exist")
    void testExistsFalse() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(false);
            when(mock.isDirectory()).thenReturn(false);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            assertFalse(client.exists("missing.txt"));
        }
    }

    @Test
    @DisplayName("Should return false when path is directory")
    void testExistsDirectory() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(true);
            when(mock.isDirectory()).thenReturn(true);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            assertFalse(client.exists("directory"));
        }
    }

    @Test
    @DisplayName("Should list files successfully")
    void testListSuccess() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.isDirectory()).thenReturn(true);

            SmbFile file1 = mock(SmbFile.class);
            SmbFile file2 = mock(SmbFile.class);
            when(file1.getName()).thenReturn("file1.txt");
            when(file2.getName()).thenReturn("file2.txt");

            SmbFile[] files = new SmbFile[]{file1, file2};
            when(mock.listFiles()).thenReturn(files);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            var result = client.list("documents/");
            assertEquals(2, result.size());
            assertTrue(result.contains("documents/file1.txt"));
            assertTrue(result.contains("documents/file2.txt"));
        }
    }

    @Test
    @DisplayName("Should handle directory names with trailing slashes")
    void testListDirectoryNamesWithSlash() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.isDirectory()).thenReturn(true);

            SmbFile dir = mock(SmbFile.class);
            SmbFile file = mock(SmbFile.class);
            when(dir.getName()).thenReturn("subdir/");
            when(file.getName()).thenReturn("file.txt");

            SmbFile[] files = new SmbFile[]{dir, file};
            when(mock.listFiles()).thenReturn(files);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            var result = client.list("docs");
            assertEquals(2, result.size());
            assertTrue(result.contains("docs/subdir"));
            assertTrue(result.contains("docs/file.txt"));
        }
    }

    @Test
    @DisplayName("Should handle null file list")
    void testListNullFiles() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.isDirectory()).thenReturn(true);
            when(mock.listFiles()).thenReturn(null);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            var result = client.list("empty/");
            assertTrue(result.isEmpty());
        }
    }

    @Test
    @DisplayName("Should throw IOException when list path is not directory")
    void testListNotDirectory() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.isDirectory()).thenReturn(false);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            IOException thrown = assertThrows(IOException.class, () -> client.list("file.txt"));
            assertTrue(thrown.getMessage().contains("Path is not a directory"));
        }
    }

    @Test
    @DisplayName("Should get file size successfully")
    void testGetFileSizeSuccess() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(true);
            when(mock.isDirectory()).thenReturn(false);
            when(mock.length()).thenReturn(12345L);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            assertEquals(12345L, client.getFileSize("test.txt"));
        }
    }

    @Test
    @DisplayName("Should throw IOException when file not found for size")
    void testGetFileSizeNotFound() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(false);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            IOException thrown = assertThrows(IOException.class, () -> client.getFileSize("missing.txt"));
            assertTrue(thrown.getMessage().contains("File not found"));
        }
    }

    @Test
    @DisplayName("Should throw IOException when getting size of directory")
    void testGetFileSizeDirectory() throws Exception {
        try (MockedConstruction<SmbFile> smbFileMock = mockConstruction(SmbFile.class, (mock, context) -> {
            when(mock.exists()).thenReturn(true);
            when(mock.isDirectory()).thenReturn(true);
        })) {
            client = SmbFileTransferClient.builder()
                .server("smb://server.com/share/")
                .username("user")
                .password("pass")
                .build();

            IOException thrown = assertThrows(IOException.class, () -> client.getFileSize("directory"));
            assertTrue(thrown.getMessage().contains("Path is a directory"));
        }
    }

    @Test
    @DisplayName("Should close successfully with credentials")
    void testCloseWithCredentials() throws Exception {
        client = SmbFileTransferClient.builder()
            .server("smb://server.com/share/")
            .username("user")
            .password("pass")
            .build();

        assertDoesNotThrow(() -> client.close());
    }

    private SmbFileTransferClient createTestClient(String server, String domain,
                                                     String username, String password) throws Exception {
        java.lang.reflect.Constructor<SmbFileTransferClient> constructor =
            SmbFileTransferClient.class.getDeclaredConstructor(
                String.class, String.class, String.class, String.class);
        constructor.setAccessible(true);

        return constructor.newInstance(server, domain, username, password);
    }
}
