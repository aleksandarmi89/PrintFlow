package com.printflow.dto;

public class PlannerProfitDTO {
    private String printType;
    private double gross;
    private double margin;

    public PlannerProfitDTO() {}

    public PlannerProfitDTO(String printType, double gross, double margin) {
        this.printType = printType;
        this.gross = gross;
        this.margin = margin;
    }

    public String getPrintType() { return printType; }
    public void setPrintType(String printType) { this.printType = printType; }
    public double getGross() { return gross; }
    public void setGross(double gross) { this.gross = gross; }
    public double getMargin() { return margin; }
    public void setMargin(double margin) { this.margin = margin; }
}
