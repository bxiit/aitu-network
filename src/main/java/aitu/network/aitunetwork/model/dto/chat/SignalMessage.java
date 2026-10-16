package aitu.network.aitunetwork.model.dto.chat;

import lombok.Data;

@Data
public class SignalMessage {
    private String type;
    private String senderId;
    private String companionId;
    private Sdp sdp;
    private Object candidate;
    // "audio" or "video": lets the callee's ringing UI show the call kind and request the camera only for video
    private String media;
}