package org.workswap.api.controller.advice;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.workswap.datasource.central.model.enums.PriceType;

@ControllerAdvice
public class GlobalModelAttributes {

    @ModelAttribute("priceTypes")
    public PriceType[] populatePriceTypes() {
        return PriceType.values();
    }
}