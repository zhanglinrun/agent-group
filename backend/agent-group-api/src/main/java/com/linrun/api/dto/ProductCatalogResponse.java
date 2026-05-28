package com.linrun.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class ProductCatalogResponse implements Serializable {

    private List<MallProductDTO> products = new ArrayList<>();
}
