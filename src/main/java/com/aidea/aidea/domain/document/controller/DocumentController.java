package com.aidea.aidea.domain.document.controller;

import java.util.Base64;
import java.util.List;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import com.aidea.aidea.domain.document.dto.YjsUpdateMessage;
import com.aidea.aidea.domain.document.service.DocumentService;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;


@Controller
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate; // 특정 결로로 메시지 직접 전송

    @GetMapping("/")
    public String runTest() {
        return "/test/document/live-test";
    }
    

    @MessageMapping("/doc/{docId}/update")
    public void handleUpdate(
            @DestinationVariable String docId,
            @Payload YjsUpdateMessage message) {

        // Base64 디코딩 후 DB 저장
        byte[] update = Base64.getDecoder().decode(message.getUpdate());
        documentService.applyUpdate(docId, update);

        // 브로드캐스트 (이미 Base64 String)
        messagingTemplate.convertAndSend("/topic/doc/" + docId, message);
    }

    @MessageMapping("/doc/{docId}/sync")
    @SendToUser("/queue/doc/sync")
    public YjsUpdateMessage syncDocument(@DestinationVariable String docId) {
        List<String> updates = documentService.getSnapshotAsBase64List(docId);
        return new YjsUpdateMessage(docId, null, updates);
    }

}
