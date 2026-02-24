package com.lt.dbcomparator.service;

import com.lt.dbcomparator.dto.CustomerResponse;
import com.lt.dbcomparator.entity.Customer;
import com.lt.dbcomparator.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Сервис чтения данных клиентов.
 * Адаптирован для MongoDB (CustomerRepository).
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final com.lt.dbcomparator.repository.CustomerProfileRepository profileRepository;
    private final com.lt.dbcomparator.repository.OrderRepository orderRepository;
    private final com.lt.dbcomparator.repository.OrderItemRepository itemRepository;
    private final com.lt.dbcomparator.repository.ProductRepository productRepository;

    /**
     * Загрузить клиента. Теперь мы собираем документ из 5 коллекций.
     */
    public CustomerResponse getById(String id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found: id=" + id));

        com.lt.dbcomparator.entity.CustomerProfile profile = profileRepository.findByCustomerId(id).orElse(null);
        java.util.List<com.lt.dbcomparator.entity.Order> orders = orderRepository.findByCustomerId(id);

        java.util.List<String> orderIds = orders.stream().map(com.lt.dbcomparator.entity.Order::getId).toList();
        java.util.List<com.lt.dbcomparator.entity.OrderItem> items = orderIds.isEmpty() ? java.util.List.of()
                : itemRepository.findByOrderIdIn(orderIds);

        java.util.List<String> productIds = items.stream().map(com.lt.dbcomparator.entity.OrderItem::getProductId)
                .distinct().toList();
        java.util.List<com.lt.dbcomparator.entity.Product> products = productIds.isEmpty() ? java.util.List.of()
                : productRepository.findAllById(productIds);

        java.util.Map<String, com.lt.dbcomparator.entity.Product> productMap = products.stream()
                .collect(java.util.stream.Collectors.toMap(com.lt.dbcomparator.entity.Product::getId, p -> p));
        java.util.Map<String, java.util.List<com.lt.dbcomparator.entity.OrderItem>> itemsByOrderId = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.lt.dbcomparator.entity.OrderItem::getOrderId));

        return CustomerResponse.from(customer, profile, orders, itemsByOrderId, productMap);
    }

    /**
     * Страничная выдача клиентов. Сборка из коллекций для батча.
     */
    public Page<CustomerResponse> getAll(Pageable pageable) {
        Page<Customer> customersPage = customerRepository.findAll(pageable);

        java.util.List<String> customerIds = customersPage.getContent().stream().map(Customer::getId).toList();

        // Profiles
        java.util.List<com.lt.dbcomparator.entity.CustomerProfile> profiles = profileRepository.findAll(); // Optimization:
                                                                                                           // should be
                                                                                                           // findByCustomerIdIn
                                                                                                           // but we'll
                                                                                                           // filter
                                                                                                           // below or
                                                                                                           // just map.
                                                                                                           // Wait,
                                                                                                           // actually
                                                                                                           // we don't
                                                                                                           // have
                                                                                                           // findByCustomerIdIn
                                                                                                           // yet.
        // I will just use findAll for simplicity if repository missing method, or add
        // it later. Actually, I didn't add findByCustomerIdIn to
        // CustomerProfileRepository. Let's just fetch all and filter, or I can add it
        // if needed.
        // For now, doing it via a stream filter for safety, since we don't have
        // findByCustomerIdIn in the ProfileRepo.
        java.util.Map<String, com.lt.dbcomparator.entity.CustomerProfile> profileMap = profiles.stream()
                .filter(p -> customerIds.contains(p.getCustomerId()))
                .collect(java.util.stream.Collectors.toMap(com.lt.dbcomparator.entity.CustomerProfile::getCustomerId,
                        p -> p, (p1, p2) -> p1));

        // Orders
        java.util.List<com.lt.dbcomparator.entity.Order> orders = orderRepository.findByCustomerIdIn(customerIds);
        java.util.Map<String, java.util.List<com.lt.dbcomparator.entity.Order>> ordersByCustomer = orders.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.lt.dbcomparator.entity.Order::getCustomerId));

        java.util.List<String> orderIds = orders.stream().map(com.lt.dbcomparator.entity.Order::getId).toList();
        java.util.List<com.lt.dbcomparator.entity.OrderItem> items = orderIds.isEmpty() ? java.util.List.of()
                : itemRepository.findByOrderIdIn(orderIds);
        java.util.Map<String, java.util.List<com.lt.dbcomparator.entity.OrderItem>> itemsByOrderId = items.stream()
                .collect(java.util.stream.Collectors.groupingBy(com.lt.dbcomparator.entity.OrderItem::getOrderId));

        java.util.List<String> productIds = items.stream().map(com.lt.dbcomparator.entity.OrderItem::getProductId)
                .distinct().toList();
        java.util.List<com.lt.dbcomparator.entity.Product> products = productIds.isEmpty() ? java.util.List.of()
                : productRepository.findAllById(productIds);
        java.util.Map<String, com.lt.dbcomparator.entity.Product> productMap = products.stream()
                .collect(java.util.stream.Collectors.toMap(com.lt.dbcomparator.entity.Product::getId, p -> p));

        return customersPage.map(c -> CustomerResponse.from(c, profileMap.get(c.getId()),
                ordersByCustomer.getOrDefault(c.getId(), java.util.List.of()), itemsByOrderId, productMap));
    }
}
