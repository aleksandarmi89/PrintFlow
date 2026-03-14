package com.printflow.controller;

import com.printflow.dto.PlannerStatsDTO;
import com.printflow.service.ProductionPlannerService;
import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionPlannerControllerTest {

    @Test
    void plannerNormalizesInvalidFiltersToSafeDefaults() {
        ProductionPlannerService service = mock(ProductionPlannerService.class);
        ProductionPlannerController controller = new ProductionPlannerController(service);
        PlannerStatsDTO stats = new PlannerStatsDTO();

        when(service.getStats(any())).thenReturn(stats);
        when(service.getDueSoonOrders(8)).thenReturn(List.of());
        when(service.getDailyLoad(7, 1L)).thenReturn(List.of());
        when(service.getWorkerLoad(8)).thenReturn(List.of());
        when(service.getProfitByPrintType(isNull(), any(), eq(1L))).thenReturn(List.of());
        when(service.getStatusTimeline(anyInt(), any(), eq(1L))).thenReturn(List.of());
        when(service.getDailyHeatmap(any(), eq(1L))).thenReturn(List.of());
        when(service.getProfitTrend(anyInt(), any(), isNull(), eq(1L))).thenReturn(List.of());
        when(service.getMaxGross(anyList())).thenReturn(0.0);
        when(service.getMaxGrossLog(anyList())).thenReturn(0.0);
        when(service.getMaxLoadCount(anyList())).thenReturn(0);
        when(service.getMaxWorkerLoadCount(anyList())).thenReturn(0L);
        when(service.getMaxStatusCount(anyList())).thenReturn(0);
        when(service.getMaxHeatmapCount(anyList())).thenReturn(0);
        when(service.getWorkers()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.planner("bad-value", "2026-03", 1L, 5, "oops", 99, "invalid", model);

        assertEquals("admin/planner/index", view);
        assertEquals("status", model.getAttribute("heatmapMode"));
        assertEquals("log", model.getAttribute("scale"));
        assertNull(model.getAttribute("printTypeFilter"));
        assertEquals(7, model.getAttribute("trendDays"));
        assertEquals(12, model.getAttribute("profitMonths"));

        verify(service).getDailyHeatmap(any(), eq(1L));
        verify(service, never()).getDeadlineHeatmap(any(), eq(1L));
        verify(service).getProfitByPrintType(isNull(), any(), eq(1L));
        verify(service).getProfitTrend(eq(12), any(), isNull(), eq(1L));
    }

    @Test
    void plannerKeepsAllowedFilters() {
        ProductionPlannerService service = mock(ProductionPlannerService.class);
        ProductionPlannerController controller = new ProductionPlannerController(service);
        PlannerStatsDTO stats = new PlannerStatsDTO();

        when(service.getStats(any())).thenReturn(stats);
        when(service.getDueSoonOrders(8)).thenReturn(List.of());
        when(service.getDailyLoad(7, null)).thenReturn(List.of());
        when(service.getWorkerLoad(8)).thenReturn(List.of());
        when(service.getProfitByPrintType(eq("DTF"), any(), isNull())).thenReturn(List.of());
        when(service.getStatusTimeline(anyInt(), any(), isNull())).thenReturn(List.of());
        when(service.getDeadlineHeatmap(any(), isNull())).thenReturn(List.of());
        when(service.getProfitTrend(anyInt(), any(), eq("DTF"), isNull())).thenReturn(List.of());
        when(service.getMaxGross(anyList())).thenReturn(0.0);
        when(service.getMaxGrossLog(anyList())).thenReturn(0.0);
        when(service.getMaxLoadCount(anyList())).thenReturn(0);
        when(service.getMaxWorkerLoadCount(anyList())).thenReturn(0L);
        when(service.getMaxStatusCount(anyList())).thenReturn(0);
        when(service.getMaxHeatmapCount(anyList())).thenReturn(0);
        when(service.getWorkers()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = controller.planner("dtf", "2026-03", null, 30, "deadline", 6, "linear", model);

        assertEquals("admin/planner/index", view);
        assertEquals("deadline", model.getAttribute("heatmapMode"));
        assertEquals("linear", model.getAttribute("scale"));
        assertEquals("DTF", model.getAttribute("printTypeFilter"));

        verify(service).getDeadlineHeatmap(any(), isNull());
        verify(service, never()).getDailyHeatmap(any(), isNull());
        verify(service).getProfitByPrintType(eq("DTF"), any(), isNull());
        verify(service).getProfitTrend(eq(6), any(), eq("DTF"), isNull());
    }
}
