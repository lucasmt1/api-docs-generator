package com.example.shop.order;

import com.example.shop.common.PageResponse;
import com.example.shop.common.exception.InsufficientStockException;
import com.example.shop.common.exception.InvalidOrderStateException;
import com.example.shop.common.exception.NotFoundException;
import com.example.shop.customer.Customer;
import com.example.shop.customer.CustomerService;
import com.example.shop.order.dto.CreateOrderRequest;
import com.example.shop.order.dto.OrderItemRequest;
import com.example.shop.order.dto.OrderResponse;
import com.example.shop.product.Product;
import com.example.shop.product.ProductRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderService {

    static final BigDecimal DISCOUNT_THRESHOLD = new BigDecimal("500.00");
    static final BigDecimal DISCOUNT_RATE = new BigDecimal("0.10");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final CustomerService customerService;

    public OrderService(OrderRepository orderRepository, ProductRepository productRepository,
            CustomerService customerService) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.customerService = customerService;
    }

    /**
     * Creates an order for an existing customer, reserving stock for every item.
     * Orders above 500.00 receive a 10% discount.
     */
    public OrderResponse create(CreateOrderRequest request) {
        Customer customer = customerService.getEntity(request.customerId());
        Order order = new Order(customer);
        for (OrderItemRequest itemRequest : request.items()) {
            Product product = productRepository.findById(itemRequest.productId())
                    .orElseThrow(() -> new NotFoundException("Product", itemRequest.productId()));
            if (product.getStock() < itemRequest.quantity()) {
                throw new InsufficientStockException(product.getSku(), itemRequest.quantity(), product.getStock());
            }
            product.setStock(product.getStock() - itemRequest.quantity());
            order.addItem(new OrderItem(product, itemRequest.quantity(), product.getPrice()));
        }
        BigDecimal subtotal = order.getItems().stream()
                .map(OrderItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = subtotal.compareTo(DISCOUNT_THRESHOLD) > 0
                ? subtotal.multiply(DISCOUNT_RATE).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        order.setSubtotal(subtotal);
        order.setDiscount(discount);
        order.setTotal(subtotal.subtract(discount));
        return OrderResponse.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return OrderResponse.from(getOrder(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<OrderResponse> list(OrderStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = status == null
                ? orderRepository.findAll(pageable)
                : orderRepository.findByStatus(status, pageable);
        return PageResponse.from(orders.map(OrderResponse::from));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listByCustomer(Long customerId) {
        return orderRepository.findByCustomerId(customerId).stream().map(OrderResponse::from).toList();
    }

    /** Cancels an order and returns its items to stock. Shipped or delivered orders cannot be cancelled. */
    public OrderResponse cancel(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() == OrderStatus.SHIPPED || order.getStatus() == OrderStatus.DELIVERED) {
            throw new InvalidOrderStateException(id, order.getStatus(), "be cancelled");
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {
            order.getItems().forEach(item -> item.getProduct().setStock(item.getProduct().getStock() + item.getQuantity()));
            order.setStatus(OrderStatus.CANCELLED);
        }
        return OrderResponse.from(order);
    }

    /** Moves an order forward: CREATED to PAID to SHIPPED to DELIVERED. */
    public OrderResponse updateStatus(Long id, OrderStatus next) {
        Order order = getOrder(id);
        boolean allowed = switch (order.getStatus()) {
            case CREATED -> next == OrderStatus.PAID;
            case PAID -> next == OrderStatus.SHIPPED;
            case SHIPPED -> next == OrderStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!allowed) {
            throw new InvalidOrderStateException(id, order.getStatus(), "move to " + next);
        }
        order.setStatus(next);
        return OrderResponse.from(order);
    }

    /** Deletes an order; only cancelled orders can be deleted. */
    public void delete(Long id) {
        Order order = getOrder(id);
        if (order.getStatus() != OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException(id, order.getStatus(), "be deleted");
        }
        orderRepository.delete(order);
    }

    private Order getOrder(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    }
}
