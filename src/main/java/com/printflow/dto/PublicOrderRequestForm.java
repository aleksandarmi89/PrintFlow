package com.printflow.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

public class PublicOrderRequestForm {

    @NotBlank(message = "{public.order.validation.customer_name.required}")
    @Size(max = 150, message = "{public.order.validation.customer_name.max}")
    private String customerName;

    @NotBlank(message = "{public.order.validation.customer_email.required}")
    @Email(message = "{public.order.validation.customer_email.invalid}")
    @Size(max = 190, message = "{public.order.validation.customer_email.max}")
    private String customerEmail;

    @Size(max = 50, message = "{public.order.validation.customer_phone.max}")
    private String customerPhone;
    @Size(max = 190, message = "{public.order.validation.customer_company.max}")
    private String customerCompanyName;

    @NotBlank(message = "{public.order.validation.product_type.required}")
    @Size(max = 150, message = "{public.order.validation.product_type.max}")
    private String productType;

    @NotNull(message = "{public.order.validation.quantity.required}")
    @Min(value = 1, message = "{public.order.validation.quantity.min}")
    private Integer quantity;

    @Size(max = 190, message = "{public.order.validation.dimensions.max}")
    private String dimensions;
    @Size(max = 150, message = "{public.order.validation.material.max}")
    private String material;
    @Size(max = 150, message = "{public.order.validation.finishing.max}")
    private String finishing;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime deadline;

    @Size(max = 4000, message = "{public.order.validation.notes.max}")
    private String notes;

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerCompanyName() {
        return customerCompanyName;
    }

    public void setCustomerCompanyName(String customerCompanyName) {
        this.customerCompanyName = customerCompanyName;
    }

    public String getProductType() {
        return productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getDimensions() {
        return dimensions;
    }

    public void setDimensions(String dimensions) {
        this.dimensions = dimensions;
    }

    public String getMaterial() {
        return material;
    }

    public void setMaterial(String material) {
        this.material = material;
    }

    public String getFinishing() {
        return finishing;
    }

    public void setFinishing(String finishing) {
        this.finishing = finishing;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
