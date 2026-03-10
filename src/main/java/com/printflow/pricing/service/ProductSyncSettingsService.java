package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.ProductSyncAuthType;
import com.printflow.pricing.dto.ProductSyncResult;
import com.printflow.pricing.dto.ProductSyncSettingsForm;
import com.printflow.pricing.dto.ProductSyncStatusView;
import com.printflow.pricing.entity.ProductSyncSettings;
import com.printflow.pricing.repository.ProductSyncSettingsRepository;
import com.printflow.service.CryptoService;
import com.printflow.service.TenantGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductSyncSettingsService {

    private final ProductSyncSettingsRepository settingsRepository;
    private final TenantGuard tenantGuard;
    private final CryptoService cryptoService;

    public ProductSyncSettingsService(ProductSyncSettingsRepository settingsRepository,
                                      TenantGuard tenantGuard,
                                      CryptoService cryptoService) {
        this.settingsRepository = settingsRepository;
        this.tenantGuard = tenantGuard;
        this.cryptoService = cryptoService;
    }

    @Transactional(readOnly = true)
    public ProductSyncSettings getOrCreateCurrentTenantSettings() {
        Company company = tenantGuard.requireCompany();
        return settingsRepository.findByCompany_Id(company.getId())
            .orElseGet(() -> {
                ProductSyncSettings settings = new ProductSyncSettings();
                settings.setCompany(company);
                settings.setAuthType(ProductSyncAuthType.NONE);
                return settings;
            });
    }

    @Transactional(readOnly = true)
    public boolean isSyncConfiguredForCurrentTenant() {
        ProductSyncSettings settings = getOrCreateCurrentTenantSettings();
        return settings.isEnabled()
            && settings.getEndpointUrl() != null
            && !settings.getEndpointUrl().isBlank();
    }

    @Transactional(readOnly = true)
    public ProductSyncStatusView currentStatusView() {
        ProductSyncSettings settings = getOrCreateCurrentTenantSettings();
        ProductSyncStatusView view = new ProductSyncStatusView();
        view.setConfigured(settings.isEnabled()
            && settings.getEndpointUrl() != null
            && !settings.getEndpointUrl().isBlank());
        view.setLastSyncAt(settings.getLastSyncAt());
        view.setLastSyncStatus(settings.getLastSyncStatus());
        view.setLastSyncMessage(settings.getLastSyncMessage());
        view.setLastSyncImported(settings.getLastSyncImported());
        view.setLastSyncUpdated(settings.getLastSyncUpdated());
        view.setLastSyncFailed(settings.getLastSyncFailed());
        return view;
    }

    @Transactional(readOnly = true)
    public ProductSyncSettingsForm toForm(ProductSyncSettings settings) {
        ProductSyncSettingsForm form = new ProductSyncSettingsForm();
        form.setEnabled(settings.isEnabled());
        form.setEndpointUrl(settings.getEndpointUrl());
        form.setAuthType(settings.getAuthType());
        form.setAuthHeaderName(settings.getAuthHeaderName());
        form.setPayloadRoot(settings.getPayloadRoot());
        form.setConnectTimeoutMs(settings.getConnectTimeoutMs());
        form.setReadTimeoutMs(settings.getReadTimeoutMs());
        return form;
    }

    public ProductSyncSettings saveCurrentTenant(ProductSyncSettingsForm form) {
        Company company = tenantGuard.requireCompany();
        ProductSyncSettings settings = settingsRepository.findByCompany_Id(company.getId())
            .orElseGet(() -> {
                ProductSyncSettings created = new ProductSyncSettings();
                created.setCompany(company);
                return created;
            });

        String endpoint = trim(form.getEndpointUrl());
        if (form.isEnabled() && (endpoint == null || endpoint.isBlank())) {
            throw new IllegalArgumentException("Endpoint URL is required when sync is enabled.");
        }
        ProductSyncAuthType authType = form.getAuthType() == null ? ProductSyncAuthType.NONE : form.getAuthType();
        String authHeaderName = trim(form.getAuthHeaderName());
        if (authType == ProductSyncAuthType.API_KEY_HEADER && (authHeaderName == null || authHeaderName.isBlank())) {
            throw new IllegalArgumentException("Auth header name is required for API_KEY_HEADER.");
        }

        settings.setEnabled(form.isEnabled());
        settings.setEndpointUrl(endpoint);
        settings.setAuthType(authType);
        settings.setAuthHeaderName(authHeaderName);
        settings.setPayloadRoot(trim(form.getPayloadRoot()));
        settings.setConnectTimeoutMs(form.getConnectTimeoutMs());
        settings.setReadTimeoutMs(form.getReadTimeoutMs());

        String rawToken = trim(form.getAuthToken());
        if (rawToken != null && !rawToken.isBlank()) {
            settings.setAuthTokenEnc(cryptoService.encrypt(rawToken));
        }

        return settingsRepository.save(settings);
    }

    public void markSyncSuccess(ProductSyncResult result) {
        Company company = tenantGuard.requireCompany();
        ProductSyncSettings settings = settingsRepository.findByCompany_Id(company.getId())
            .orElseGet(() -> {
                ProductSyncSettings created = new ProductSyncSettings();
                created.setCompany(company);
                return created;
            });
        settings.setLastSyncAt(java.time.LocalDateTime.now());
        settings.setLastSyncStatus("SUCCESS");
        settings.setLastSyncImported(result != null ? result.getImportedCount() : 0);
        settings.setLastSyncUpdated(result != null ? result.getUpdatedCount() : 0);
        settings.setLastSyncFailed(result != null ? result.getFailedCount() : 0);
        settings.setLastSyncMessage(result != null && !result.getErrors().isEmpty()
            ? String.join(" | ", result.getErrors().stream().limit(3).toList())
            : "OK");
        settingsRepository.save(settings);
    }

    public void markSyncFailure(String message) {
        Company company = tenantGuard.requireCompany();
        ProductSyncSettings settings = settingsRepository.findByCompany_Id(company.getId())
            .orElseGet(() -> {
                ProductSyncSettings created = new ProductSyncSettings();
                created.setCompany(company);
                return created;
            });
        settings.setLastSyncAt(java.time.LocalDateTime.now());
        settings.setLastSyncStatus("FAILED");
        settings.setLastSyncImported(0);
        settings.setLastSyncUpdated(0);
        settings.setLastSyncFailed(1);
        settings.setLastSyncMessage(trim(message));
        settingsRepository.save(settings);
    }

    @Transactional(readOnly = true)
    public String resolveToken(ProductSyncSettings settings) {
        if (settings == null || settings.getAuthTokenEnc() == null || settings.getAuthTokenEnc().isBlank()) {
            return null;
        }
        return cryptoService.decrypt(settings.getAuthTokenEnc());
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
