package com.printflow.pricing.service;

import com.printflow.entity.Company;
import com.printflow.entity.enums.PricingComponentType;
import com.printflow.entity.enums.PricingModel;
import com.printflow.entity.enums.UnitType;
import com.printflow.pricing.dto.BreakdownRow;
import com.printflow.pricing.dto.PricingCalculateRequest;
import com.printflow.pricing.dto.PricingCalculateResponse;
import com.printflow.pricing.dto.PricingVariantRequirementsResponse;
import com.printflow.pricing.entity.PricingComponent;
import com.printflow.pricing.entity.ProductVariant;
import com.printflow.pricing.repository.PricingComponentRepository;
import com.printflow.pricing.repository.ProductVariantRepository;
import com.printflow.service.ResourceNotFoundException;
import com.printflow.service.ClientPricingProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PricingEngineService {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final BigDecimal ONE_TEN = new BigDecimal("1.10");
    private static final BigDecimal TARGET_MARGIN = new BigDecimal("0.30");
    private static final int MONEY_SCALE = 2;
    private static final String DEFAULT_CURRENCY = "RSD";

    private final ProductVariantRepository variantRepository;
    private final PricingComponentRepository componentRepository;
    private final ClientPricingProfileService pricingProfileService;

    public PricingEngineService(ProductVariantRepository variantRepository,
                                PricingComponentRepository componentRepository,
                                ClientPricingProfileService pricingProfileService) {
        this.variantRepository = variantRepository;
        this.componentRepository = componentRepository;
        this.pricingProfileService = pricingProfileService;
    }

    @Transactional(readOnly = true)
    public PricingCalculateResponse calculate(Company company, PricingCalculateRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("Request is required");
        }
        if (req.getVariantId() == null) {
            throw new IllegalArgumentException("variantId is required");
        }
        if (req.getQuantity() == null || req.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }

        ProductVariant variant = variantRepository
            .findWithProductByIdAndCompany_Id(req.getVariantId(), company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));

        List<PricingComponent> components = componentRepository
            .findAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());

        PricingCalculateResponse response = new PricingCalculateResponse();
        response.setCurrency(resolveCurrency(company));
        if (components.isEmpty()) {
            response.getWarnings().add("No pricing components configured");
        }

        Map<String, Object> attributes = req.getAttributes();
        int colorsRaw = getInt(attributes, "colors", 1);
        int colors = Math.max(1, colorsRaw);
        if (colorsRaw < 1) {
            response.getWarnings().add("Colors adjusted to 1.");
        }
        int sidesRaw = getInt(attributes, "sides", 1);
        int sides = Math.max(1, sidesRaw);
        if (sidesRaw < 1) {
            response.getWarnings().add("Sides adjusted to 1.");
        }
        int minutes = Math.max(0, getInt(attributes, "minutes", 0));
        boolean rush = getBoolean(attributes, "rush", false);
        BigDecimal customMarkup = getBigDecimal(attributes, "customMarkup");
        Long clientId = req.getClientId();

        BigDecimal qty = req.getQuantity();
        UnitType unitType = variant.getProduct().getUnitType();

        BigDecimal areaM2 = BigDecimal.ZERO;
        boolean requiresArea = unitType == UnitType.SQM
            || components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_SQM);
        if (requiresArea) {
            if (req.getWidthMm() == null || req.getHeightMm() == null
                || req.getWidthMm() <= 0 || req.getHeightMm() <= 0) {
                response.getWarnings().add("Dimensions required for area-based pricing.");
            } else {
                BigDecimal widthM = new BigDecimal(req.getWidthMm()).divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
                BigDecimal heightM = new BigDecimal(req.getHeightMm()).divide(new BigDecimal("1000"), 6, RoundingMode.HALF_UP);
                areaM2 = widthM.multiply(heightM);
            }
        }

        BigDecimal meters = BigDecimal.ONE;
        boolean requiresMeters = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_METER);
        if (unitType == UnitType.METER || requiresMeters) {
            BigDecimal maybeMeters = getBigDecimal(attributes, "meters");
            if (maybeMeters != null && maybeMeters.compareTo(BigDecimal.ZERO) > 0) {
                meters = maybeMeters;
            } else if (requiresMeters) {
                response.getWarnings().add("Meters value missing for per-meter pricing.");
            }
        }
        boolean requiresMinutes = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_MINUTE);
        if (requiresMinutes && minutes <= 0) {
            response.getWarnings().add("Minutes value missing for per-minute pricing.");
        }

        List<BreakdownRow> breakdown = new ArrayList<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal breakdownSum = BigDecimal.ZERO;

        for (PricingComponent component : components) {
            BigDecimal multiplierValue = computeMultiplier(component.getModel(), qty, areaM2, meters, colors, sides, minutes);
            BigDecimal lineCost = component.getAmount() == null
                ? BigDecimal.ZERO
                : component.getAmount().multiply(multiplierValue);
            BreakdownRow row = new BreakdownRow();
            row.setComponentType(component.getType());
            row.setPricingModel(component.getModel());
            row.setBaseAmount(component.getAmount());
            row.setMultiplierDescription(multiplierDescription(component.getModel(), qty, areaM2, meters, colors, sides, minutes));
            row.setMultiplierValue(multiplierValue);
            row.setComputedAmount(roundMoney(multiplierValue));
            row.setUnit(unitForModel(component.getModel()));
            row.setLineCost(roundMoney(lineCost));
            breakdown.add(row);
            totalCost = totalCost.add(lineCost);
            breakdownSum = breakdownSum.add(lineCost);
        }

        BigDecimal wastePercent = safePercent(variant.getWastePercent());
        if (wastePercent.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal wasteAmount = totalCost.multiply(wastePercent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
            BreakdownRow wasteRow = new BreakdownRow();
            wasteRow.setComponentType(PricingComponentType.OTHER);
            wasteRow.setPricingModel(PricingModel.FIXED);
            wasteRow.setBaseAmount(roundMoney(wasteAmount));
            wasteRow.setMultiplierDescription("waste " + wastePercent + "%");
            wasteRow.setMultiplierValue(BigDecimal.ONE);
            wasteRow.setComputedAmount(BigDecimal.ONE);
            wasteRow.setUnit("job");
            wasteRow.setLineCost(roundMoney(wasteAmount));
            wasteRow.setNotes("Waste");
            breakdown.add(wasteRow);
            totalCost = totalCost.add(wasteAmount);
            breakdownSum = breakdownSum.add(wasteAmount);
            response.getAppliedRules().add("WASTE_APPLIED(" + wastePercent + "%)");
        }

        BigDecimal markupPercent = customMarkup != null ? customMarkup : safePercent(variant.getDefaultMarkupPercent());
        BigDecimal totalPrice = totalCost.multiply(BigDecimal.ONE.add(markupPercent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP)));

        if (rush) {
            totalPrice = totalPrice.multiply(ONE_TEN);
            response.getAppliedRules().add("RUSH_FEE(+10%)");
        }

        BigDecimal discountPercent = null;
        if (clientId != null) {
            var profile = pricingProfileService.getProfile(clientId, variant.getId(), company.getId());
            if (profile != null && profile.getDiscountPercent() != null
                && profile.getDiscountPercent().compareTo(BigDecimal.ZERO) > 0) {
                discountPercent = profile.getDiscountPercent();
                BigDecimal discountMultiplier = BigDecimal.ONE.subtract(discountPercent.divide(ONE_HUNDRED, 6, RoundingMode.HALF_UP));
                totalPrice = totalPrice.multiply(discountMultiplier);
                response.getAppliedRules().add("CLIENT_DISCOUNT(" + discountPercent + "%)");
            }
        }

        if (variant.getMinPrice() != null && totalPrice.compareTo(variant.getMinPrice()) < 0) {
            totalPrice = variant.getMinPrice();
            response.getAppliedRules().add("MIN_PRICE_APPLIED(" + variant.getMinPrice() + ")");
        }

        BigDecimal pricePerUnit;
        if (unitType == UnitType.SQM) {
            BigDecimal denom = qty.multiply(areaM2);
            pricePerUnit = denom.compareTo(BigDecimal.ZERO) == 0 ? BigDecimal.ZERO : totalPrice.divide(denom, 6, RoundingMode.HALF_UP);
        } else {
            pricePerUnit = totalPrice.divide(qty, 6, RoundingMode.HALF_UP);
        }

        BigDecimal marginPercent = BigDecimal.ZERO;
        if (totalPrice.compareTo(BigDecimal.ZERO) > 0) {
            marginPercent = totalPrice.subtract(totalCost)
                .divide(totalPrice, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
        }
        BigDecimal markupComputed = BigDecimal.ZERO;
        if (totalCost.compareTo(BigDecimal.ZERO) > 0) {
            markupComputed = totalPrice.subtract(totalCost)
                .divide(totalCost, 6, RoundingMode.HALF_UP)
                .multiply(ONE_HUNDRED);
        }

        BigDecimal minimumPrice = variant.getMinPrice();
        BigDecimal targetMarginPrice = totalCost.compareTo(BigDecimal.ZERO) == 0
            ? BigDecimal.ZERO
            : totalCost.divide(BigDecimal.ONE.subtract(TARGET_MARGIN), 6, RoundingMode.HALF_UP);

        if (totalPrice.compareTo(BigDecimal.ZERO) > 0
            && marginPercent.compareTo(TARGET_MARGIN.multiply(ONE_HUNDRED)) < 0) {
            response.getWarnings().add("Margin below target.");
        }
        BigDecimal diff = breakdownSum.subtract(totalCost).abs();
        if (diff.compareTo(new BigDecimal("0.01")) > 0) {
            response.getWarnings().add("COST_BREAKDOWN_MISMATCH");
        }

        response.setBreakdown(breakdown);
        response.setTotalCost(roundMoney(totalCost));
        response.setTotalPrice(roundMoney(totalPrice));
        response.setProfitAmount(roundMoney(totalPrice.subtract(totalCost)));
        response.setPricePerUnit(roundMoney(pricePerUnit));
        response.setMarginPercent(roundMoney(marginPercent));
        response.setMarkupPercent(roundMoney(markupComputed));
        response.setRecommendedPrice(roundMoney(totalPrice));
        response.setMinimumPrice(minimumPrice != null ? roundMoney(minimumPrice) : null);
        response.setTargetMarginPrice(roundMoney(targetMarginPrice));

        if (clientId != null) {
            var profile = pricingProfileService.getProfile(clientId, variant.getId(), company.getId());
            if (profile != null && profile.getLastPrice() != null) {
                response.getWarnings().add("Last time you charged this client: " + profile.getLastPrice());
                if (totalPrice.compareTo(profile.getLastPrice().multiply(new BigDecimal("0.8"))) < 0) {
                    response.getWarnings().add("Price is more than 20% lower than last time.");
                }
            }
        }

        return response;
    }

    @Transactional(readOnly = true)
    public PricingVariantRequirementsResponse getVariantRequirements(Company company, Long variantId) {
        if (variantId == null) {
            throw new IllegalArgumentException("variantId is required");
        }
        ProductVariant variant = variantRepository
            .findWithProductByIdAndCompany_Id(variantId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Product variant not found"));
        List<PricingComponent> components = componentRepository
            .findAllByVariant_IdAndCompany_Id(variant.getId(), company.getId());

        boolean perSqm = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_SQM);
        boolean perColor = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_COLOR);
        boolean perSide = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_SIDE);
        boolean perMeter = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_METER);
        boolean perMinute = components.stream().anyMatch(c -> c.getModel() == PricingModel.PER_MINUTE);

        PricingVariantRequirementsResponse response = new PricingVariantRequirementsResponse();
        response.setVariantId(variant.getId());
        response.setVariantName(variant.getName());
        response.setUnitType(variant.getProduct() != null && variant.getProduct().getUnitType() != null
            ? variant.getProduct().getUnitType().name()
            : null);
        response.setRequiresDimensions(perSqm || (variant.getProduct() != null && variant.getProduct().getUnitType() == UnitType.SQM));
        response.setRequiresColors(perColor);
        response.setRequiresSides(perSide);
        response.setRequiresMeters(perMeter || (variant.getProduct() != null && variant.getProduct().getUnitType() == UnitType.METER));
        response.setRequiresMinutes(perMinute);
        return response;
    }

    private BigDecimal computeMultiplier(PricingModel model,
                                         BigDecimal qty,
                                         BigDecimal areaM2,
                                         BigDecimal meters,
                                         int colors,
                                         int sides,
                                         int minutes) {
        if (model == null) {
            return BigDecimal.ZERO;
        }
        return switch (model) {
            case FIXED -> BigDecimal.ONE;
            case PER_UNIT -> qty;
            case PER_SQM -> qty.multiply(areaM2);
            case PER_METER -> qty.multiply(meters);
            case PER_COLOR -> qty.multiply(new BigDecimal(colors));
            case PER_SIDE -> qty.multiply(new BigDecimal(sides));
            case PER_MINUTE -> new BigDecimal(minutes);
        };
    }

    private String multiplierDescription(PricingModel model,
                                         BigDecimal qty,
                                         BigDecimal areaM2,
                                         BigDecimal meters,
                                         int colors,
                                         int sides,
                                         int minutes) {
        if (model == null) {
            return "";
        }
        return switch (model) {
            case FIXED -> "fixed";
            case PER_UNIT -> "qty=" + qty;
            case PER_SQM -> "qty=" + qty + ", areaM2=" + areaM2;
            case PER_METER -> "qty=" + qty + ", meters=" + meters;
            case PER_COLOR -> "qty=" + qty + ", colors=" + colors;
            case PER_SIDE -> "qty=" + qty + ", sides=" + sides;
            case PER_MINUTE -> "minutes=" + minutes;
        };
    }

    private String unitForModel(PricingModel model) {
        if (model == null) {
            return "";
        }
        return switch (model) {
            case FIXED -> "job";
            case PER_UNIT -> "pcs";
            case PER_SQM -> "m2";
            case PER_METER -> "m";
            case PER_COLOR -> "color";
            case PER_SIDE -> "side";
            case PER_MINUTE -> "min";
        };
    }

    private BigDecimal roundMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private int getInt(Map<String, Object> attributes, String key, int defaultValue) {
        if (attributes == null || !attributes.containsKey(key) || attributes.get(key) == null) {
            return defaultValue;
        }
        Object value = attributes.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private boolean getBoolean(Map<String, Object> attributes, String key, boolean defaultValue) {
        if (attributes == null || !attributes.containsKey(key) || attributes.get(key) == null) {
            return defaultValue;
        }
        Object value = attributes.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private BigDecimal getBigDecimal(Map<String, Object> attributes, String key) {
        if (attributes == null || !attributes.containsKey(key) || attributes.get(key) == null) {
            return null;
        }
        Object value = attributes.get(key);
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal safePercent(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private String resolveCurrency(Company company) {
        if (company == null) {
            return DEFAULT_CURRENCY;
        }
        String currency = company.getCurrency();
        if (currency == null || currency.isBlank()) {
            return DEFAULT_CURRENCY;
        }
        return currency.trim().toUpperCase();
    }
}
