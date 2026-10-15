package aitu.network.aitunetwork.service;

import aitu.network.aitunetwork.common.exception.NotFoundException;
import aitu.network.aitunetwork.service.util.GridFsService;
import com.mongodb.client.gridfs.model.GridFSFile;
import org.bson.BsonObjectId;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.gridfs.GridFsResource;

import java.util.Date;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileServiceTest {
    private final GridFsService gridFsService = mock(GridFsService.class);
    private final FileService fileService = new FileService(gridFsService);

    private GridFsResource storedFile(String id, Document metadata) {
        GridFsResource resource = new GridFsResource(
                new GridFSFile(new BsonObjectId(), "f", 3, 255, new Date(), metadata));
        when(gridFsService.downloadFile(id)).thenReturn(resource);
        return resource;
    }

    @Test
    @DisplayName("The public file endpoint never serves a chat attachment")
    void publicDownloadHidesChatFiles() {
        storedFile("chat-file", new Document("chatId", "chat-1"));

        assertThrows(NotFoundException.class, () -> fileService.getFile("chat-file"));
    }

    @Test
    @DisplayName("Ordinary uploads (posts, avatars) are still public")
    void publicDownloadServesOrdinaryFiles() {
        GridFsResource resource = storedFile("avatar", null);

        assertThat(fileService.getFile("avatar"), sameInstance(resource));
    }

    @Test
    @DisplayName("A chat file can only be fetched through its own chat")
    void chatDownloadChecksChat() {
        GridFsResource resource = storedFile("chat-file", new Document("chatId", "chat-1"));

        assertThat(fileService.getChatFile("chat-1", "chat-file"), sameInstance(resource));
        assertThrows(NotFoundException.class, () -> fileService.getChatFile("chat-2", "chat-file"));

        storedFile("avatar", null);
        assertThrows(NotFoundException.class, () -> fileService.getChatFile("chat-1", "avatar"));
    }
}
