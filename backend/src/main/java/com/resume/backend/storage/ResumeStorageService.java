package com.resume.backend.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// Local filesystem replacement for the Supabase "resumes" Storage bucket.
// Files are laid out as {root}/{ownerRole}/{ownerId}/{uuid}__{originalFilename}
// so ownership can be checked from the id string alone, with no extra table.
@Service
public class ResumeStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");

    private final Path root;

    public ResumeStorageService(@Value("${resumes.storage-dir}") String storageDir) throws IOException {
        this.root = Paths.get(storageDir).toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public record StoredResume(String id, String originalFilename) {
    }

    public record LoadedResume(Resource resource, String originalFilename, String contentType) {
    }

    public StoredResume store(MultipartFile file, String ownerRole, String ownerId) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }
        String originalFilename = Paths.get(Objects.requireNonNullElse(file.getOriginalFilename(), "resume"))
                .getFileName().toString();
        String extension = extensionOf(originalFilename);
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Unsupported file type: only .pdf and .docx are accepted");
        }

        Path ownerDir = root.resolve(ownerRole).resolve(ownerId);
        Files.createDirectories(ownerDir);
        String storedFilename = UUID.randomUUID() + "__" + originalFilename;
        file.transferTo(ownerDir.resolve(storedFilename));

        return new StoredResume(ownerRole + "/" + ownerId + "/" + storedFilename, originalFilename);
    }

    public boolean isOwnedBy(String id, String ownerRole, String ownerId) {
        return id.startsWith(ownerRole + "/" + ownerId + "/");
    }

    public LoadedResume load(String id) throws IOException {
        // Path.resolve() treats an absolute argument as an outright replacement
        // (ignoring `root` entirely), so an id like "/etc/passwd" must be
        // rejected up front rather than relying on the startsWith(root) check
        // below, which a leading-slash id would otherwise bypass completely.
        if (id.isBlank() || Paths.get(id).isAbsolute()) {
            throw new NoSuchFileException(id);
        }
        Path resolved = root.resolve(id).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new NoSuchFileException(id);
        }

        String storedFilename = resolved.getFileName().toString();
        int separator = storedFilename.indexOf("__");
        String originalFilename = separator >= 0 ? storedFilename.substring(separator + 2) : storedFilename;
        String contentType = Files.probeContentType(resolved);

        return new LoadedResume(new FileSystemResource(resolved), originalFilename,
                contentType != null ? contentType : "application/octet-stream");
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }
}
