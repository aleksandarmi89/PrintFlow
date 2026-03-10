package com.printflow.controller;

import com.printflow.service.ProductionPlannerService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@Controller
@RequestMapping("/admin/planner")
public class ProductionPlannerController extends BaseController {

    private final ProductionPlannerService productionPlannerService;

    public ProductionPlannerController(ProductionPlannerService productionPlannerService) {
        this.productionPlannerService = productionPlannerService;
    }

    @GetMapping
    public String planner(@RequestParam(required = false) String printType,
                          @RequestParam(required = false) String month,
                          @RequestParam(required = false) Long workerId,
                          @RequestParam(required = false, defaultValue = "7") int trendDays,
                          @RequestParam(required = false, defaultValue = "status") String heatmapMode,
                          @RequestParam(required = false, defaultValue = "6") int profitMonths,
                          @RequestParam(required = false, defaultValue = "log") String scale,
                          Model model) {
        java.time.YearMonth ym = null;
        if (month != null && !month.isBlank()) {
            try {
                ym = java.time.YearMonth.parse(month);
            } catch (Exception ignored) {
            }
        }
        model.addAttribute("stats", productionPlannerService.getStats(ym));
        model.addAttribute("dueSoonOrders", productionPlannerService.getDueSoonOrders(8));
        var dailyLoad = productionPlannerService.getDailyLoad(7, workerId);
        var workerLoad = productionPlannerService.getWorkerLoad(8);
        var profit = productionPlannerService.getProfitByPrintType(printType, ym, workerId);
        int safeTrendDays = trendDays < 7 ? 7 : Math.min(trendDays, 60);
        var statusTimeline = productionPlannerService.getStatusTimeline(safeTrendDays, ym, workerId);
        java.time.YearMonth heatmapMonth = ym != null ? ym : java.time.YearMonth.now();
        boolean deadlineHeatmap = "deadline".equalsIgnoreCase(heatmapMode);
        var heatmap = deadlineHeatmap
            ? productionPlannerService.getDeadlineHeatmap(ym, workerId)
            : productionPlannerService.getDailyHeatmap(ym, workerId);
        int safeProfitMonths = Math.max(3, Math.min(profitMonths, 12));
        var profitTrend = productionPlannerService.getProfitTrend(safeProfitMonths, ym, printType, workerId);
        model.addAttribute("dailyLoad", dailyLoad);
        model.addAttribute("workerLoad", workerLoad);
        model.addAttribute("profitByType", profit);
        model.addAttribute("profitMaxGross", productionPlannerService.getMaxGross(profit));
        model.addAttribute("profitMaxGrossLog", productionPlannerService.getMaxGrossLog(profit));
        model.addAttribute("statusTimeline", statusTimeline);
        model.addAttribute("heatmapDays", heatmap);
        model.addAttribute("maxDailyLoad", productionPlannerService.getMaxLoadCount(dailyLoad));
        model.addAttribute("maxWorkerLoad", productionPlannerService.getMaxWorkerLoadCount(workerLoad));
        model.addAttribute("maxStatusCount", productionPlannerService.getMaxStatusCount(statusTimeline));
        model.addAttribute("maxHeatmapCount", productionPlannerService.getMaxHeatmapCount(heatmap));
        model.addAttribute("heatmapOffset", heatmapMonth.atDay(1).getDayOfWeek().getValue() - 1);
        model.addAttribute("workers", productionPlannerService.getWorkers());
        model.addAttribute("heatmapMode", heatmapMode);
        model.addAttribute("profitTrend", profitTrend);
        model.addAttribute("printTypeFilter", printType);
        model.addAttribute("monthFilter", month);
        model.addAttribute("workerFilter", workerId);
        model.addAttribute("scale", scale);
        model.addAttribute("trendDays", safeTrendDays);
        model.addAttribute("profitMonths", safeProfitMonths);
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            model.addAttribute("statusTimelineJson", mapper.writeValueAsString(statusTimeline));
            model.addAttribute("profitTrendJson", mapper.writeValueAsString(profitTrend));
        } catch (Exception e) {
            model.addAttribute("statusTimelineJson", "[]");
            model.addAttribute("profitTrendJson", "[]");
        }
        return "admin/planner/index";
    }

    @GetMapping("/margins.csv")
    public ResponseEntity<String> exportMargins(@RequestParam(required = false) String printType,
                                                @RequestParam(required = false) String month,
                                                @RequestParam(required = false, defaultValue = "sr") String format) {
        java.time.YearMonth ym = null;
        if (month != null && !month.isBlank()) {
            try {
                ym = java.time.YearMonth.parse(month);
            } catch (Exception ignored) {
            }
        }
        var rows = productionPlannerService.getProfitByPrintType(printType, ym);
        StringBuilder sb = new StringBuilder();
        boolean sr = "sr".equalsIgnoreCase(format);
        String sep = sr ? ";" : ",";
        java.text.DecimalFormat df;
        if (sr) {
            java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator('.');
            df = new java.text.DecimalFormat("#0.00", symbols);
        } else {
            df = new java.text.DecimalFormat("#0.00");
        }
        if (sr) {
            sb.append("tip").append(sep).append("bruto").append(sep).append("marza").append(sep).append("trosak").append("\n");
        } else {
            sb.append("printType").append(sep).append("gross").append(sep).append("margin").append(sep).append("cost").append("\n");
        }
        for (var row : rows) {
            double cost = row.getGross() - row.getMargin();
            String typeLabel = row.getPrintType();
            if (sr) {
                if ("LASER".equalsIgnoreCase(typeLabel)) {
                    typeLabel = "Laser";
                } else if ("OTHER".equalsIgnoreCase(typeLabel)) {
                    typeLabel = "Ostalo";
                }
            } else {
                if ("LASER".equalsIgnoreCase(typeLabel)) {
                    typeLabel = "Laser";
                } else if ("OTHER".equalsIgnoreCase(typeLabel)) {
                    typeLabel = "Other";
                } else if ("DTF".equalsIgnoreCase(typeLabel)) {
                    typeLabel = "DTF";
                }
            }
            sb.append(typeLabel).append(sep)
              .append(sr ? df.format(row.getGross()) : row.getGross()).append(sep)
              .append(sr ? df.format(row.getMargin()) : row.getMargin()).append(sep)
              .append(sr ? df.format(cost) : cost).append("\n");
        }
        String csv = "\uFEFF" + sb.toString();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"margins.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/profit-trend.csv")
    public ResponseEntity<String> exportProfitTrend(@RequestParam(required = false) String printType,
                                                    @RequestParam(required = false) String month,
                                                    @RequestParam(required = false) Long workerId,
                                                    @RequestParam(required = false, defaultValue = "6") int profitMonths,
                                                    @RequestParam(required = false, defaultValue = "sr") String format) {
        java.time.YearMonth ym = null;
        if (month != null && !month.isBlank()) {
            try {
                ym = java.time.YearMonth.parse(month);
            } catch (Exception ignored) {
            }
        }
        int safeMonths = Math.max(3, Math.min(profitMonths, 12));
        var rows = productionPlannerService.getProfitTrend(safeMonths, ym, printType, workerId);
        boolean sr = "sr".equalsIgnoreCase(format);
        String sep = sr ? ";" : ",";
        java.text.DecimalFormat df;
        if (sr) {
            java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols();
            symbols.setDecimalSeparator(',');
            symbols.setGroupingSeparator('.');
            df = new java.text.DecimalFormat("#0.00", symbols);
        } else {
            df = new java.text.DecimalFormat("#0.00");
        }
        StringBuilder sb = new StringBuilder();
        if (sr) {
            sb.append("mesec").append(sep).append("bruto").append(sep).append("marza").append(sep).append("trosak").append("\n");
        } else {
            sb.append("month").append(sep).append("gross").append(sep).append("margin").append(sep).append("cost").append("\n");
        }
        for (var row : rows) {
            sb.append(row.getLabel()).append(sep)
              .append(sr ? df.format(row.getGross()) : row.getGross()).append(sep)
              .append(sr ? df.format(row.getMargin()) : row.getMargin()).append(sep)
              .append(sr ? df.format(row.getCost()) : row.getCost()).append("\n");
        }
        String csv = "\uFEFF" + sb.toString();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"profit-trend.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }

    @GetMapping("/status-trend.csv")
    public ResponseEntity<String> exportStatusTrend(@RequestParam(required = false) String month,
                                                    @RequestParam(required = false) Long workerId,
                                                    @RequestParam(required = false, defaultValue = "7") int trendDays,
                                                    @RequestParam(required = false, defaultValue = "sr") String format) {
        java.time.YearMonth ym = null;
        if (month != null && !month.isBlank()) {
            try {
                ym = java.time.YearMonth.parse(month);
            } catch (Exception ignored) {
            }
        }
        int safeTrendDays = trendDays < 7 ? 7 : Math.min(trendDays, 60);
        var rows = productionPlannerService.getStatusTimeline(safeTrendDays, ym, workerId);
        boolean sr = "sr".equalsIgnoreCase(format);
        String sep = sr ? ";" : ",";
        StringBuilder sb = new StringBuilder();
        if (sr) {
            sb.append("dan").append(sep).append("new").append(sep).append("design")
              .append(sep).append("print").append(sep).append("ready").append("\n");
        } else {
            sb.append("day").append(sep).append("new").append(sep).append("design")
              .append(sep).append("print").append(sep).append("ready").append("\n");
        }
        for (var row : rows) {
            sb.append(row.getLabel()).append(sep)
              .append(row.getNewCount()).append(sep)
              .append(row.getDesignCount()).append(sep)
              .append(row.getPrintCount()).append(sep)
              .append(row.getReadyCount()).append("\n");
        }
        String csv = "\uFEFF" + sb.toString();
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"status-trend.csv\"")
            .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
            .body(csv);
    }
}
