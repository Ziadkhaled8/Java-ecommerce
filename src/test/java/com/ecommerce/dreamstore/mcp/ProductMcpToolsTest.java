package com.ecommerce.dreamstore.mcp;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import com.ecommerce.dreamstore.service.interfaces.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductMcpToolsTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ProductMcpTools productMcpTools;

    @Test
    @DisplayName("createProduct MCP tool successfully maps input and calls ProductService")
    void createProduct_success() {
        ProductMcpTools.CreateProductInput input = new ProductMcpTools.CreateProductInput(
                "Wireless Headphones",
                "Sony",
                "Noise-cancelling wireless over-ear headphones",
                299.99,
                50,
                1L
        );

        ProductResponse expectedResponse = new ProductResponse(
                100L,
                "Wireless Headphones",
                "Sony",
                "Noise-cancelling wireless over-ear headphones",
                BigDecimal.valueOf(299.99),
                50,
                "Electronics",
                List.of()
        );

        when(productService.createProduct(any(CreateProductRequest.class))).thenReturn(expectedResponse);

        ProductResponse actualResponse = productMcpTools.createProduct(input);

        assertThat(actualResponse).isNotNull();
        assertThat(actualResponse.id()).isEqualTo(100L);
        assertThat(actualResponse.name()).isEqualTo("Wireless Headphones");
        assertThat(actualResponse.brand()).isEqualTo("Sony");
        assertThat(actualResponse.price()).isEqualTo(BigDecimal.valueOf(299.99));
        assertThat(actualResponse.stockQuantity()).isEqualTo(50);
        assertThat(actualResponse.categoryName()).isEqualTo("Electronics");

        ArgumentCaptor<CreateProductRequest> captor = ArgumentCaptor.forClass(CreateProductRequest.class);
        verify(productService).createProduct(captor.capture());

        CreateProductRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.name()).isEqualTo("Wireless Headphones");
        assertThat(capturedRequest.brand()).isEqualTo("Sony");
        assertThat(capturedRequest.description()).isEqualTo("Noise-cancelling wireless over-ear headphones");
        assertThat(capturedRequest.price()).isEqualTo(299.99);
        assertThat(capturedRequest.stockQuantity()).isEqualTo(50);
        assertThat(capturedRequest.categoryId()).isEqualTo(1L);
        assertThat(capturedRequest.images()).isNull();
    }
}
