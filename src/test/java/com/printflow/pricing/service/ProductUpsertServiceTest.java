package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.User;
import com.printflow.entity.enums.ProductSource;
import com.printflow.pricing.dto.ProductForm;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.service.TenantGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductUpsertServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TenantGuard tenantGuard;
    @Mock
    private ProductPricingBridgeService pricingBridgeService;

    private ProductUpsertService service;

    @BeforeEach
    void setUp() {
        service = new ProductUpsertService(productRepository, tenantGuard, pricingBridgeService);
    }

    @Test
    void createManualSetsTenantSourceAndAuditUser() {
        Company company = new Company();
        company.setId(10L);
        User user = new User();
        user.setId(44L);
        when(tenantGuard.requireCompany()).thenReturn(company);
        when(tenantGuard.getCurrentUser()).thenReturn(user);
        when(productRepository.existsByCompany_IdAndSkuIgnoreCase(anyLong(), anyString())).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductForm form = new ProductForm();
        form.setName("   Banner Premium   ");
        form.setSku(" SKU-1 ");
        form.setBasePrice(new BigDecimal("120.50"));
        form.setCurrency("rsd");
        form.setActive(true);

        Product saved = service.createManual(form);

        assertThat(saved.getCompany().getId()).isEqualTo(10L);
        assertThat(saved.getName()).isEqualTo("Banner Premium");
        assertThat(saved.getSku()).isEqualTo("SKU-1");
        assertThat(saved.getBasePrice()).isEqualByComparingTo("120.50");
        assertThat(saved.getCurrency()).isEqualTo("RSD");
        assertThat(saved.getSource()).isEqualTo(ProductSource.MANUAL);
        assertThat(saved.getCreatedByUserId()).isEqualTo(44L);
        assertThat(saved.getUpdatedByUserId()).isEqualTo(44L);
        verify(pricingBridgeService).ensureDefaultPricingStructure(saved);
    }

    @Test
    void createManualRejectsDuplicateSkuWithinTenant() {
        Company company = new Company();
        company.setId(10L);
        when(tenantGuard.requireCompany()).thenReturn(company);
        when(productRepository.existsByCompany_IdAndSkuIgnoreCase(10L, "SKU-EXISTS")).thenReturn(true);

        ProductForm form = new ProductForm();
        form.setName("Poster");
        form.setSku("SKU-EXISTS");
        form.setBasePrice(new BigDecimal("12.00"));

        assertThatThrownBy(() -> service.createManual(form))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SKU already exists");
    }

    @Test
    void updateManualChecksTenantAndSkuConflict() {
        Company company = new Company();
        company.setId(10L);
        when(tenantGuard.requireCompany()).thenReturn(company);
        Product existing = new Product();
        existing.setId(7L);
        existing.setCompany(company);
        existing.setName("Old");
        when(productRepository.findByIdAndCompany_Id(7L, 10L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCompany_IdAndSkuIgnoreCaseAndIdNot(10L, "SKU-NEW", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductForm form = new ProductForm();
        form.setName("New Product");
        form.setSku(" SKU-NEW ");
        form.setBasePrice(new BigDecimal("99.99"));

        Product updated = service.updateManual(7L, form);
        assertThat(updated.getName()).isEqualTo("New Product");
        assertThat(updated.getSku()).isEqualTo("SKU-NEW");

        verify(productRepository).findByIdAndCompany_Id(7L, 10L);
        verify(productRepository).existsByCompany_IdAndSkuIgnoreCaseAndIdNot(10L, "SKU-NEW", 7L);
        verify(pricingBridgeService).reconcileAutoBasePrice(updated);
    }
}
