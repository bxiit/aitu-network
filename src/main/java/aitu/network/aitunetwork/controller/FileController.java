package aitu.network.aitunetwork.controller;

import aitu.network.aitunetwork.service.FileService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/file")
public class FileController {
    // Uploaded files are served from the app's own origin, so a browser must never render one as a document
    // that can run script (HTML, SVG, XML...): it could read the user's private key from IndexedDB.
    // Only these types are shown inline; everything else is sent as an opaque download.
    private static final Set<String> INLINE_TYPES = Set.of(
            "image/png", "image/jpeg", "image/gif", "image/webp", "image/avif", "image/bmp",
            "audio/mpeg", "audio/mp4", "audio/ogg", "audio/webm", "audio/wav",
            "video/mp4", "video/webm", "video/ogg", "video/quicktime"
    );
    // A file opened directly gets an opaque origin with scripts disabled, whatever its type
    private static final String SANDBOX_POLICY = "sandbox";

    private final FileService fileService;

    @GetMapping("/{id}")
    CompletableFuture<ResponseEntity<Resource>> downloadFile(@PathVariable String id) {
        return CompletableFuture.supplyAsync(() -> {
            GridFsResource resource = fileService.getFile(id);
            MediaType detected = MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
            boolean inline = INLINE_TYPES.contains(detected.getType() + "/" + detected.getSubtype());
            MediaType contentType = inline ? detected : MediaType.APPLICATION_OCTET_STREAM;
            ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                    .build();
            try {
                CacheControl cacheControl = CacheControl.maxAge(Duration.ofDays(1))
                        .cachePublic()
                        .immutable();
                return ResponseEntity.ok()
                        .cacheControl(cacheControl)
                        .eTag("/%s/".formatted(id))
                        .contentType(contentType)
                        .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                        .header("Content-Security-Policy", SANDBOX_POLICY)
                        .contentLength(resource.contentLength())
                        .body(new InputStreamResource(resource.getInputStream()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
