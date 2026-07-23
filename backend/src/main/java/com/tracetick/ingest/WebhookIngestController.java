package com.tracetick.ingest;

import com.tracetick.api.dto.TicketDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingest")
public class WebhookIngestController {

    private final WebhookIngestService ingestService;

    public WebhookIngestController(WebhookIngestService ingestService) {
        this.ingestService = ingestService;
    }

    @PostMapping(value = "/{urlToken}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TicketDto> ingest(
            @PathVariable String urlToken,
            @RequestHeader(value = "X-TraceTick-Signature", required = false) String signatureHeader,
            @RequestBody byte[] rawBody) {
        var result = ingestService.ingest(urlToken, rawBody, signatureHeader);
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(TicketDto.from(result.ticket()));
    }
}
