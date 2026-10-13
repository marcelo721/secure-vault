package com.securevault.vault.controller;

import com.securevault.security.AuthenticatedUser;
import com.securevault.vault.dto.VaultItemDetailResponse;
import com.securevault.vault.dto.VaultItemRequest;
import com.securevault.vault.dto.VaultItemSummaryResponse;
import com.securevault.vault.service.VaultItemService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Somente HTTP: a identidade vem do {@link AuthenticatedUser}; a regra de propriedade fica no serviço/repositório. */
@RestController
@RequestMapping("/api/v1/vault-items")
public class VaultItemController {

    private final VaultItemService service;

    public VaultItemController(VaultItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<VaultItemSummaryResponse> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return service.list(user.id());
    }

    @PostMapping
    public ResponseEntity<VaultItemSummaryResponse> create(@AuthenticationPrincipal AuthenticatedUser user,
                                                           @Valid @RequestBody VaultItemRequest request) {
        VaultItemSummaryResponse created = service.create(user.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/vault-items/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    public VaultItemDetailResponse get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        return service.get(user.id(), id);
    }

    @PutMapping("/{id}")
    public VaultItemSummaryResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                                           @PathVariable UUID id,
                                           @Valid @RequestBody VaultItemRequest request) {
        return service.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        service.delete(user.id(), id);
        return ResponseEntity.noContent().build();
    }
}
