package com.printflow.service;

import com.printflow.entity.Client;
import com.printflow.entity.ClientPricingProfile;
import com.printflow.entity.Company;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.repository.ClientPricingProfileRepository;
import com.printflow.repository.ClientRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.service.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import com.printflow.dto.ClientPricingProfileView;

@Service
@Transactional
public class ClientPricingProfileService {

    private final ClientPricingProfileRepository repository;
    private final ClientRepository clientRepository;
    private final ProductVariantRepository variantRepository;

    public ClientPricingProfileService(ClientPricingProfileRepository repository,
                                       ClientRepository clientRepository,
                                       ProductVariantRepository variantRepository) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.variantRepository = variantRepository;
    }

    public ClientPricingProfile getProfile(Long clientId, Long variantId, Long companyId) {
        return repository.findByClient_IdAndVariant_IdAndCompany_Id(clientId, variantId, companyId).orElse(null);
    }

    public void recordPrice(Client client, ProductVariant variant, BigDecimal price) {
        if (client == null || variant == null || price == null) {
            return;
        }
        Company company = client.getCompany();
        if (company == null) {
            return;
        }
        ClientPricingProfile profile = repository
            .findByClient_IdAndVariant_IdAndCompany_Id(client.getId(), variant.getId(), company.getId())
            .orElseGet(ClientPricingProfile::new);
        profile.setClient(client);
        profile.setVariant(variant);
        profile.setCompany(company);

        BigDecimal lastPrice = profile.getLastPrice();
        profile.setLastPrice(price);
        profile.setLastOrderedAt(LocalDateTime.now());

        if (profile.getAveragePrice() == null) {
            profile.setAveragePrice(price);
        } else if (lastPrice != null) {
            BigDecimal avg = profile.getAveragePrice().add(price).divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP);
            profile.setAveragePrice(avg);
        }

        repository.save(profile);
    }

    @Transactional(readOnly = true)
    public List<ClientPricingProfileView> getProfilesForClient(Long clientId, Long companyId) {
        return repository.findAllByClient_IdAndCompany_Id(clientId, companyId)
            .stream()
            .map(profile -> {
                ClientPricingProfileView view = new ClientPricingProfileView();
                view.setId(profile.getId());
                view.setVariantId(profile.getVariant() != null ? profile.getVariant().getId() : null);
                view.setVariantName(profile.getVariant() != null ? profile.getVariant().getName() : null);
                view.setDiscountPercent(profile.getDiscountPercent());
                view.setLastPrice(profile.getLastPrice());
                view.setAveragePrice(profile.getAveragePrice());
                return view;
            })
            .collect(Collectors.toList());
    }

    public ClientPricingProfile upsertDiscount(Long clientId, Long variantId, Long companyId, BigDecimal discountPercent) {
        if (clientId == null || variantId == null || companyId == null) {
            throw new IllegalArgumentException("Client, variant and company are required");
        }
        if (discountPercent != null && discountPercent.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Discount must be >= 0");
        }
        if (discountPercent != null && discountPercent.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Discount must be <= 100");
        }
        Client client = clientRepository.findByIdAndCompany_Id(clientId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Client not found"));
        ProductVariant variant = variantRepository.findByIdAndCompany_Id(variantId, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Variant not found"));

        ClientPricingProfile profile = repository
            .findByClient_IdAndVariant_IdAndCompany_Id(clientId, variantId, companyId)
            .orElseGet(ClientPricingProfile::new);
        profile.setClient(client);
        profile.setVariant(variant);
        profile.setCompany(client.getCompany());
        profile.setDiscountPercent(discountPercent);
        return repository.save(profile);
    }
}
