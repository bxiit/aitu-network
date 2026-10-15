package aitu.network.aitunetwork.controller;

import aitu.network.aitunetwork.service.FileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.ByteArrayInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

class FileControllerTest {
    private final FileService fileService = mock(FileService.class);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new FileController(fileService)).build();

    private MockHttpServletResponse download(String filename) throws Exception {
        byte[] content = "<script>alert(document.domain)</script>".getBytes();
        GridFsResource resource = mock(GridFsResource.class);
        when(resource.getFilename()).thenReturn(filename);
        when(resource.contentLength()).thenReturn((long) content.length);
        when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(content));
        when(fileService.getFile("f1")).thenReturn(resource);

        MvcResult started = mockMvc.perform(get("/api/v1/file/f1"))
                .andExpect(request().asyncStarted())
                .andReturn();
        return mockMvc.perform(asyncDispatch(started)).andReturn().getResponse();
    }

    @ParameterizedTest
    @CsvSource({"evil.html", "evil.htm", "evil.svg", "evil.xml", "evil.js", "noextension"})
    @DisplayName("Files a browser could execute are sent as an opaque download")
    void scriptableTypesAreForcedToDownload(String filename) throws Exception {
        MockHttpServletResponse response = download(filename);

        assertThat(response.getStatus(), equalTo(200));
        assertThat(response.getContentType(), equalTo("application/octet-stream"));
        assertThat(response.getHeader("Content-Disposition"), equalTo("attachment"));
        assertThat(response.getHeader("Content-Security-Policy"), equalTo("sandbox"));
    }

    @ParameterizedTest
    @CsvSource({
            "photo.png, image/png",
            "photo.jpg, image/jpeg",
            "voice.mp3, audio/mpeg",
            "clip.webm, video/webm",
            "clip.mp4, video/mp4"
    })
    @DisplayName("Images, audio and video keep their type and display inline")
    void mediaTypesAreShownInline(String filename, String expectedType) throws Exception {
        MockHttpServletResponse response = download(filename);

        assertThat(response.getStatus(), equalTo(200));
        assertThat(response.getContentType(), equalTo(expectedType));
        assertThat(response.getHeader("Content-Disposition"), equalTo("inline"));
        assertThat(response.getHeader("Content-Security-Policy"), equalTo("sandbox"));
    }
}
