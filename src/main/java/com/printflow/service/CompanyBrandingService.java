package com.printflow.service;

import com.printflow.dto.CompanyBrandingDTO;
import com.printflow.entity.Company;
import com.printflow.repository.CompanyRepository;
import com.printflow.storage.FileStorage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Optional;

@Service
public class CompanyBrandingService {

    private final CompanyRepository companyRepository;
    private final FileStorage fileStorage;

    public CompanyBrandingService(CompanyRepository companyRepository, FileStorage fileStorage) {
        this.companyRepository = companyRepository;
        this.fileStorage = fileStorage;
    }

    public Optional<CompanyBrandingDTO> getBrandingByCompanyId(Long companyId) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId).map(this::toBranding);
    }

    public Optional<CompanyBrandingDTO> getBrandingByCompanyId(Long companyId, String token, String type) {
        if (companyId == null) {
            return Optional.empty();
        }
        return companyRepository.findById(companyId).map(company -> toBranding(company, token, type));
    }

    public CompanyBrandingDTO toBranding(Company company) {
        return toBranding(company, null, null);
    }

    public CompanyBrandingDTO toBranding(Company company, String token, String type) {
        String logoUrl = null;
        if (company.getLogoPath() != null && !company.getLogoPath().isBlank()) {
            if (token != null && !token.isBlank() && type != null && !type.isBlank()) {
                logoUrl = "/public/company-logo/" + company.getId()
                    + "?token=" + token + "&type=" + type;
            } else {
                logoUrl = "/public/company-logo/" + company.getId();
            }
        }
        return new CompanyBrandingDTO(
            company.getId(),
            company.getName(),
            logoUrl,
            company.getEmail(),
            company.getPhone(),
            company.getAddress(),
            company.getWebsite(),
            company.getLegalName(),
            company.getTaxId(),
            company.getRegistrationNumber(),
            company.getBillingEmail(),
            company.getBankName(),
            company.getBankAccount(),
            company.getPrimaryColor()
        );
    }

    public byte[] loadLogo(Long companyId) throws IOException {
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        if (company.getLogoPath() == null || company.getLogoPath().isBlank()) {
            throw new IOException("Logo not set");
        }
        return fileStorage.load(company.getLogoPath());
    }
}
