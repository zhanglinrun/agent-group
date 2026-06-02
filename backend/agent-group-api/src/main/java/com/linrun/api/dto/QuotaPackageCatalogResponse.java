package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class QuotaPackageCatalogResponse implements Serializable {

    private List<ProductCardDTO> packages = new ArrayList<>();
}
