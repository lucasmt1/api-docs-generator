package com.example.shop.product;

import com.example.shop.common.exception.DuplicateResourceException;
import com.example.shop.common.exception.InsufficientStockException;
import com.example.shop.common.exception.NotFoundException;
import com.example.shop.product.dto.CreateProductRequest;
import com.example.shop.product.dto.ProductResponse;
import com.example.shop.product.dto.StockAdjustmentRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** Registers a product; SKUs are unique across the catalog. */
    public ProductResponse create(CreateProductRequest request) {
        if (productRepository.existsBySku(request.sku())) {
            throw new DuplicateResourceException("Product", "sku", request.sku());
        }
        Product product = new Product(request.sku(), request.name(), request.price(), request.stock());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResponse findById(Long id) {
        return ProductResponse.from(getProduct(id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> list(Pageable pageable) {
        return productRepository.findAll(pageable).map(ProductResponse::from);
    }

    /** Applies a relative stock change; stock can never become negative. */
    public ProductResponse adjustStock(Long id, StockAdjustmentRequest request) {
        Product product = getProduct(id);
        int newStock = product.getStock() + request.delta();
        if (newStock < 0) {
            throw new InsufficientStockException(product.getSku(), -request.delta(), product.getStock());
        }
        product.setStock(newStock);
        return ProductResponse.from(product);
    }

    private Product getProduct(Long id) {
        return productRepository.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }
}
