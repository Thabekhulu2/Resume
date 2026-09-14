package com.resume.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.NoSuchFileException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ResumeStorageServiceTest {

    @TempDir
    java.nio.file.Path tempDir;

    private ResumeStorageService newService() throws IOException {
        return new ResumeStorageService(tempDir.toString());
    }

    @Test
    void storeAndLoadRoundTripPreservesContentAndOriginalFilename() throws IOException {
        ResumeStorageService service = newService();
        var file = new MockMultipartFile("file", "My Resume.pdf", "application/pdf", "pdf-bytes".getBytes());

        var stored = service.store(file, "candidate", "cand-1");
        assertThat(stored.originalFilename()).isEqualTo("My Resume.pdf");

        var loaded = service.load(stored.id());
        assertThat(loaded.originalFilename()).isEqualTo("My Resume.pdf");
        assertThat(loaded.resource().getContentAsByteArray()).isEqualTo("pdf-bytes".getBytes());
    }

    @Test
    void storeRejectsEmptyFile() throws IOException {
        ResumeStorageService service = newService();
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[0]);

        assertThatThrownBy(() -> service.store(file, "candidate", "cand-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storeRejectsDisallowedExtension() throws IOException {
        ResumeStorageService service = newService();
        var file = new MockMultipartFile("file", "resume.exe", "application/octet-stream", "bytes".getBytes());

        assertThatThrownBy(() -> service.store(file, "candidate", "cand-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isOwnedByMatchesOnlyExactRoleAndId() throws IOException {
        ResumeStorageService service = newService();
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", "bytes".getBytes());
        var stored = service.store(file, "candidate", "cand-1");

        assertThat(service.isOwnedBy(stored.id(), "candidate", "cand-1")).isTrue();
        assertThat(service.isOwnedBy(stored.id(), "candidate", "cand-2")).isFalse();
        assertThat(service.isOwnedBy(stored.id(), "recruiter", "cand-1")).isFalse();
    }

    @Test
    void loadRejectsPathTraversalAttempt() throws IOException {
        ResumeStorageService service = newService();

        assertThatThrownBy(() -> service.load("../../etc/passwd"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void loadRejectsAbsolutePathId() throws IOException {
        ResumeStorageService service = newService();

        assertThatThrownBy(() -> service.load("/etc/passwd"))
                .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    void loadThrowsForMissingFile() throws IOException {
        ResumeStorageService service = newService();

        assertThatThrownBy(() -> service.load("candidate/nobody/does-not-exist.pdf"))
                .isInstanceOf(NoSuchFileException.class);
    }
}
