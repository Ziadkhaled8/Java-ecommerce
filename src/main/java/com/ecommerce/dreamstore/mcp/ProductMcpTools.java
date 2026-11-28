package com.ecommerce.dreamstore.mcp;

import com.ecommerce.dreamstore.dto.request.CreateProductRequest;
import com.ecommerce.dreamstore.dto.response.ProductResponse;
import com.ecommerce.dreamstore.service.interfaces.ProductService;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP Tools for managing products in the Dream Store catalogue.
 */
@Component
public class ProductMcpTools {

    private final ProductService productService;

    public ProductMcpTools(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Input schema for creating a product via MCP.
     */
    public record CreateProductInput(
            @ToolParam(description = "The name or title of the product", required = true)
            @McpToolParam(description = "The name or title of the product", required = true)
            String name,

            @ToolParam(description = "The brand or manufacturer name", required = true)
            @McpToolParam(description = "The brand or manufacturer name", required = true)
            String brand,

            @ToolParam(description = "Detailed description of the product", required = false)
            @McpToolParam(description = "Detailed description of the product", required = false)
            String description,

            @ToolParam(description = "Price of the product (e.g. 29.99)", required = true)
            @McpToolParam(description = "Price of the product (e.g. 29.99)", required = true)
            double price,

            @ToolParam(description = "Available quantity in stock", required = true)
            @McpToolParam(description = "Available quantity in stock", required = true)
            int stockQuantity,

            @ToolParam(description = "ID of the category this product belongs to", required = true)
            @McpToolParam(description = "ID of the category this product belongs to", required = true)
            Long categoryId
    ) {}

    /**
     * MCP Tool for creating a new product.
     *
     * @param input the product metadata
     * @return the created product response
     */
    @Tool(name = "create_product", description = "Creates a new product in the dream store catalogue.")
    @McpTool(name = "create_product", description = "Creates a new product in the dream store catalogue.")
    public ProductResponse createProduct(CreateProductInput input) {
        CreateProductRequest request = new CreateProductRequest(
                input.name(),
                input.brand(),
                input.description(),
                input.price(),
                input.stockQuantity(),
                input.categoryId(),
                null
        );
        return productService.createProduct(request);
    }
}
