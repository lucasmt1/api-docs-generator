package com.example.shop.product;

import com.example.shop.common.ApiPaths;
import com.example.shop.product.dto.CreateProductRequest;
import com.example.shop.product.dto.ProductResponse;
import com.example.shop.product.dto.StockAdjustmentRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Product catalog and stock management. */
@RestController
@RequestMapping(ApiPaths.PRODUCTS)
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** Lists products page by page. */
    @GetMapping
    public Page<ProductResponse> list(Pageable pageable) {
        return productService.list(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request) {
        return productService.create(request);
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductResponse adjustStock(@PathVariable Long id, @Valid @RequestBody StockAdjustmentRequest request) {
        return productService.adjustStock(id, request);
    }
}
