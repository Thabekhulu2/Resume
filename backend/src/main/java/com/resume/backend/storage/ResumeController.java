package com.resume.backend.storage;

import com.resume.backend.auth.JwtService;
import com.resume.backend.auth.Role;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.Locale;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeStorageService storageService;

    public ResumeController(ResumeStorageService storageService) {
        this.storageService = storageService;
    }

    public record UploadResponse(String id, String filename) {
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadResponse upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        try {
            var stored = storageService.store(file, principal.role().name().toLowerCase(Locale.ROOT), principal.id().toString());
            return new UploadResponse(stored.id(), stored.originalFilename());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to store file", e);
        }
    }

    // Recruiters may fetch any resume (mirrors the old "resumes recruiters
    // select" Storage policy); candidates may only fetch their own.
    //
    // Uses the {*id} capture-everything-after syntax rather than a manual
    // "/**" + HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE extraction:
    // Spring Boot 3's default PathPatternParser-based matching leaves that
    // attribute's value percent-encoded (e.g. a space survives as "%20"
    // instead of being decoded), which silently broke lookups for any
    // filename with a space or other encoded character. @PathVariable capture
    // goes through normal URI decoding.
    @GetMapping("/{*id}")
    public ResponseEntity<Resource> download(
            @PathVariable String id,
            @AuthenticationPrincipal JwtService.TokenPrincipal principal) {
        String resumeId = id.startsWith("/") ? id.substring(1) : id;

        if (principal.role() == Role.CANDIDATE && !storageService.isOwnedBy(resumeId, "candidate", principal.id().toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You may only access your own resume");
        }

        try {
            var loaded = storageService.load(resumeId);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(loaded.contentType()))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + loaded.originalFilename() + "\"")
                    .body(loaded.resource());
        } catch (NoSuchFileException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No such resume");
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
        }
    }
}
