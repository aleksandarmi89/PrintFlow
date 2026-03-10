package com.printflow.pricing;

import com.printflow.entity.Company;
import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.ProductCategory;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.dto.PricingCalculateRequest;
import com.printflow.pricing.dto.PricingCalculateResponse;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.Product;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.pricing.service.PricingEngineService;
import com.printflow.repository.CompanyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class PricingEngineServiceTest {

    @Autowired
    private PricingEngineService pricingEngineService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private PricingComponentRepository componentRepository;

    @Test
    void bannerSqmPricing() {
        Company company = createCompany("BannerCo");
        Product product = createProduct(company, "Banner", ProductCategory.BANNER, UnitType.SQM);
        ProductVariant variant = createVariant(company, product, "Banner 510", new BigDecimal("20.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("2.00"));
        createComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));
        request.setWidthMm(1000);
        request.setHeightMm(1000);

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getTotalCost()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(response.getMarginPercent()).isEqualByComparingTo(new BigDecimal("16.67"));
        assertThat(response.getMarkupPercent()).isEqualByComparingTo(new BigDecimal("20.00"));
    }

    @Test
    void tshirtPiecePricing() {
        Company company = createCompany("ShirtCo");
        Product product = createProduct(company, "T-Shirt", ProductCategory.APPAREL, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Cotton Tee", new BigDecimal("20.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("3.00"));
        createComponent(company, variant, PricingComponentType.PRINT, PricingModel.PER_COLOR, new BigDecimal("1.00"));
        createComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("colors", 2);
        request.setAttributes(attributes);

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getTotalCost()).isEqualByComparingTo(new BigDecimal("55.00"));
        assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("66.00"));
    }

    @Test
    void minPriceEnforced() {
        Company company = createCompany("MinPriceCo");
        Product product = createProduct(company, "Mug", ProductCategory.MUG, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "White Mug", new BigDecimal("20.00"), new BigDecimal("100.00"));

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("1.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getAppliedRules()).anyMatch(rule -> rule.startsWith("MIN_PRICE_APPLIED"));
    }

    @Test
    void rushFeeApplied() {
        Company company = createCompany("RushCo");
        Product product = createProduct(company, "Sticker", ProductCategory.STICKER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Matte Sticker", new BigDecimal("20.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("2.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("rush", true);
        request.setAttributes(attributes);

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("26.40"));
        assertThat(response.getAppliedRules()).contains("RUSH_FEE(+10%)");
    }

    @Test
    void marginVsMarkupSanity() {
        Company company = createCompany("MarginCo");
        Product product = createProduct(company, "Poster", ProductCategory.PAPER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Poster A2", new BigDecimal("50.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("10.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getTotalCost()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.getTotalPrice()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(response.getMarginPercent()).isEqualByComparingTo(new BigDecimal("33.33"));
        assertThat(response.getMarkupPercent()).isEqualByComparingTo(new BigDecimal("50.00"));
    }

    @Test
    void wasteAppliedAddsCostAndRule() {
        Company company = createCompany("WasteCo");
        Product product = createProduct(company, "Banner", ProductCategory.BANNER, UnitType.SQM);
        ProductVariant variant = createVariant(company, product, "Banner 510", new BigDecimal("20.00"), null);
        variant.setWastePercent(new BigDecimal("10.00"));
        variantRepository.save(variant);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("2.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));
        request.setWidthMm(1000);
        request.setHeightMm(1000);

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getAppliedRules()).anyMatch(rule -> rule.startsWith("WASTE_APPLIED"));
        assertThat(response.getTotalCost()).isEqualByComparingTo(new BigDecimal("22.00"));
    }

    @Test
    void missingDimensionsAddsWarningForAreaPricing() {
        Company company = createCompany("WarnCo");
        Product product = createProduct(company, "Banner", ProductCategory.BANNER, UnitType.SQM);
        ProductVariant variant = createVariant(company, product, "Banner 510", new BigDecimal("20.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_SQM, new BigDecimal("2.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        assertThat(response.getWarnings()).anyMatch(msg -> msg.toLowerCase().contains("dimensions"));
    }

    @Test
    void breakdownSumMatchesTotalCost() {
        Company company = createCompany("SumCo");
        Product product = createProduct(company, "Sticker", ProductCategory.STICKER, UnitType.PIECE);
        ProductVariant variant = createVariant(company, product, "Matte Sticker", new BigDecimal("20.00"), null);

        createComponent(company, variant, PricingComponentType.MATERIAL, PricingModel.PER_UNIT, new BigDecimal("2.00"));
        createComponent(company, variant, PricingComponentType.SETUP, PricingModel.FIXED, new BigDecimal("5.00"));

        PricingCalculateRequest request = new PricingCalculateRequest();
        request.setVariantId(variant.getId());
        request.setQuantity(new BigDecimal("10"));

        PricingCalculateResponse response = pricingEngineService.calculate(company, request);

        BigDecimal sum = response.getBreakdown().stream()
            .map(row -> row.getLineCost() != null ? row.getLineCost() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo(response.getTotalCost());
    }

    private Company createCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return companyRepository.save(company);
    }

    private Product createProduct(Company company, String name, ProductCategory category, UnitType unitType) {
        Product product = new Product();
        product.setCompany(company);
        product.setName(name);
        product.setCategory(category);
        product.setUnitType(unitType);
        return productRepository.save(product);
    }

    private ProductVariant createVariant(Company company, Product product, String name, BigDecimal markup, BigDecimal minPrice) {
        ProductVariant variant = new ProductVariant();
        variant.setCompany(company);
        variant.setProduct(product);
        variant.setName(name);
        variant.setDefaultMarkupPercent(markup);
        variant.setMinPrice(minPrice);
        return variantRepository.save(variant);
    }

    private PricingComponent createComponent(Company company,
                                             ProductVariant variant,
                                             PricingComponentType type,
                                             PricingModel model,
                                             BigDecimal amount) {
        PricingComponent component = new PricingComponent();
        component.setCompany(company);
        component.setVariant(variant);
        component.setType(type);
        component.setModel(model);
        component.setAmount(amount);
        return componentRepository.save(component);
    }
}
