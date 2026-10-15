package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.ConflictException;
import aitu.network.aitunetwork.common.exception.NotFoundException;
import aitu.network.aitunetwork.service.util.GridFsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {
    private final GridFsService gridFsService;
    @Value("${secure-talk.file-download-path}")
    private String path;

    public String uploadFile(MultipartFile file) {
        try {
            return gridFsService.uploadFile(file);
        } catch (IOException e) {
            throw new ConflictException(e.getLocalizedMessage());
        }
    }

    public String uploadChatFile(MultipartFile file, String chatId, String uploaderId) {
        try {
            return gridFsService.uploadChatFile(file, chatId, uploaderId);
        } catch (IOException e) {
            throw new ConflictException(e.getLocalizedMessage());
        }
    }

    public GridFsResource getChatFile(String chatId, String fileId) {
        GridFsResource resource = gridFsService.downloadFile(fileId);
        Assert.notNull(resource.getGridFSFile(), "Resource is null");
        if (!chatId.equals(GridFsService.chatIdOf(resource.getGridFSFile()))) {
            throw new NotFoundException("File with id %s not found".formatted(fileId));
        }
        return resource;
    }

    public void deleteFile(String id) {
        gridFsService.deleteFile(id);
    }

    public String getLinkForResource(String hexId) {
        return path + hexId;
    }

    public GridFsResource getFile(String id) {
        GridFsResource resource = gridFsService.downloadFile(id);
        Assert.notNull(resource.getGridFSFile(), "Resource is null");
        // chat attachments are only served to the chat's participants, see getChatFile
        if (GridFsService.chatIdOf(resource.getGridFSFile()) != null) {
            throw new NotFoundException("File with id %s not found".formatted(id));
        }
        return resource;
    }

    public void deleteFilesByLink(List<String> mediaFileIds) {
        //["https://aitunet.kz/api/v1/file/68172d4e1391893c7f5279ea"]
        mediaFileIds.stream()
                .map(link -> link.substring(link.lastIndexOf('/') + 1)).
                forEach(this::deleteFile);
    }
}
