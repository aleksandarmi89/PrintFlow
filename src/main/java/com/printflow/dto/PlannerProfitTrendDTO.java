package com.printflow.dto;

public class PlannerProfitTrendDTO {
    private String label;
    private double gross;
    private double margin;
    private double cost;

    public PlannerProfitTrendDTO(String label, double gross, double margin, double cost) {
        this.label = label;
        this.gross = gross;
        this.margin = margin;
        this.cost = cost;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public double getGross() {
        return gross;
    }

    public void setGross(double gross) {
        this.gross = gross;
    }

    public double getMargin() {
        return margin;
    }

    public void setMargin(double margin) {
        this.margin = margin;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }
}
