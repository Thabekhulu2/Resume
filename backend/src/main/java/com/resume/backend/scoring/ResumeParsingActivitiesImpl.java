package com.resume.backend.scoring;

import com.resume.backend.storage.ResumeStorageService;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.stream.Collectors;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

@Component
public class ResumeParsingActivitiesImpl implements ResumeParsingActivities {

    private final ResumeStorageService storageService;

    public ResumeParsingActivitiesImpl(ResumeStorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public String extractResumeText(String resumeStorageId) {
        try {
            var loaded = storageService.load(resumeStorageId);
            String lowerName = loaded.originalFilename().toLowerCase(Locale.ROOT);

            if (lowerName.endsWith(".pdf")) {
                byte[] bytes = loaded.resource().getContentAsByteArray();
                try (PDDocument document = Loader.loadPDF(bytes)) {
                    return new PDFTextStripper().getText(document);
                }
            } else if (lowerName.endsWith(".docx")) {
                try (InputStream in = loaded.resource().getInputStream();
                        XWPFDocument document = new XWPFDocument(in)) {
                    return document.getParagraphs().stream()
                            .map(paragraph -> paragraph.getText())
                            .collect(Collectors.joining("\n"));
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported resume file format for '" + resumeStorageId + "'; expected .pdf or .docx");
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read resume " + resumeStorageId, e);
        }
    }
}
