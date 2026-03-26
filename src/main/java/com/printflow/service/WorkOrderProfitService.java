package com.printflow.service;

import com.printflow.entity.Company;
import com.printflow.entity.TimeEntry;
import com.printflow.entity.WorkOrderItem;
import com.printflow.repository.TimeEntryRepository;
import com.printflow.repository.WorkOrderItemRepository;
import com.printflow.repository.WorkOrderRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.List;

@Service
public class WorkOrderProfitService {

    private static final int MONEY_SCALE = 2;

    private final WorkOrderRepository workOrderRepository;
    private final WorkOrderItemRepository workOrderItemRepository;
    private final TimeEntryRepository timeEntryRepository;

    public WorkOrderProfitService(WorkOrderRepository workOrderRepository,
                                  WorkOrderItemRepository workOrderItemRepository,
                                  TimeEntryRepository timeEntryRepository) {
        this.workOrderRepository = workOrderRepository;
        this.workOrderItemRepository = workOrderItemRepository;
        this.timeEntryRepository = timeEntryRepository;
    }

    public ProfitResult calculateRealProfit(Long workOrderId, Company company) {
        workOrderRepository.findByIdAndCompany_Id(workOrderId, company.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Work order not found"));

        List<WorkOrderItem> items = workOrderItemRepository.findAllByWorkOrder_IdAndCompany_Id(workOrderId, company.getId());
        BigDecimal revenue = items.stream()
            .map(WorkOrderItem::getCalculatedPrice)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimatedCost = items.stream()
            .map(WorkOrderItem::getCalculatedCost)
            .filter(v -> v != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TimeEntry> entries = timeEntryRepository.findByWorkOrderIdAndCompanyId(workOrderId, company.getId());
        BigDecimal laborCost = entries.stream()
            .map(this::costForEntry)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal realCost = estimatedCost.add(laborCost);
        BigDecimal profit = revenue.subtract(realCost);
        BigDecimal marginPercent = BigDecimal.ZERO;
        if (revenue.compareTo(BigDecimal.ZERO) > 0) {
            marginPercent = profit.divide(revenue, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"));
        }

        ProfitResult result = new ProfitResult();
        result.setRevenue(roundMoney(revenue));
        result.setEstimatedCost(roundMoney(estimatedCost));
        result.setLaborCost(roundMoney(laborCost));
        result.setRealCost(roundMoney(realCost));
        result.setProfit(roundMoney(profit));
        result.setMarginPercent(roundMoney(marginPercent));
        result.setCurrency(resolveCurrency(company));
        return result;
    }

    private BigDecimal costForEntry(TimeEntry entry) {
        if (entry == null || entry.getUser() == null || entry.getUser().getHourlyRate() == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal hours = BigDecimal.valueOf(entry.getHours() != null ? entry.getHours() : 0);
        BigDecimal minutes = BigDecimal.valueOf(entry.getMinutes() != null ? entry.getMinutes() : 0);
        BigDecimal totalHours = hours.add(minutes.divide(new BigDecimal("60"), 6, RoundingMode.HALF_UP));
        return entry.getUser().getHourlyRate().multiply(totalHours);
    }

    private BigDecimal roundMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        }
        return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static class ProfitResult {
        private BigDecimal revenue;
        private BigDecimal estimatedCost;
        private BigDecimal laborCost;
        private BigDecimal realCost;
        private BigDecimal profit;
        private BigDecimal marginPercent;
        private String currency;

        public BigDecimal getRevenue() {
            return revenue;
        }

        public void setRevenue(BigDecimal revenue) {
            this.revenue = revenue;
        }

        public BigDecimal getEstimatedCost() {
            return estimatedCost;
        }

        public void setEstimatedCost(BigDecimal estimatedCost) {
            this.estimatedCost = estimatedCost;
        }

        public BigDecimal getLaborCost() {
            return laborCost;
        }

        public void setLaborCost(BigDecimal laborCost) {
            this.laborCost = laborCost;
        }

        public BigDecimal getRealCost() {
            return realCost;
        }

        public void setRealCost(BigDecimal realCost) {
            this.realCost = realCost;
        }

        public BigDecimal getProfit() {
            return profit;
        }

        public void setProfit(BigDecimal profit) {
            this.profit = profit;
        }

        public BigDecimal getMarginPercent() {
            return marginPercent;
        }

        public void setMarginPercent(BigDecimal marginPercent) {
            this.marginPercent = marginPercent;
        }

        public String getCurrency() {
            return currency;
        }

        public void setCurrency(String currency) {
            this.currency = currency;
        }
    }

    private String resolveCurrency(Company company) {
        if (company == null || company.getCurrency() == null || company.getCurrency().isBlank()) {
            return "RSD";
        }
        return company.getCurrency().trim().toUpperCase(Locale.ROOT);
    }
}
