package com.casms.ship.controller;

import com.casms.common.response.ApiResponse;
import com.casms.ship.dto.response.PublicShipModelResponse;
import com.casms.ship.service.ShipModelAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/ships/{shipId}/public-model")
public class PublicShipModelController {
    private final ShipModelAssetService assets;

    @GetMapping
    @PreAuthorize("permitAll()")
    public ApiResponse<PublicShipModelResponse> model(@PathVariable UUID shipId) {
        return ApiResponse.success(assets.publicModel(shipId));
    }

    @GetMapping("/{modelId}/assets/{*assetPath}")
    @PreAuthorize("permitAll()")
    public ResponseEntity<StreamingResponseBody> asset(@PathVariable UUID shipId, @PathVariable UUID modelId,
                                                       @PathVariable String assetPath) {
        String name = assetPath.startsWith("/") ? assetPath.substring(1) : assetPath;
        var metadata = assets.publicAsset(shipId, modelId, name);
        StreamingResponseBody body = output -> assets.writePublicAsset(shipId, modelId, name, output);
        // Recheck publication on every request, including after archive/deactivation.
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("model/gltf-binary"))
                .contentLength(metadata.size()).cacheControl(CacheControl.noStore()).body(body);
    }
}
