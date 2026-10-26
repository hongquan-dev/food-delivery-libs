package com.fooddelivery.apiflow.route.xml;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import java.util.List;

/**
 * Raw Jackson XML binding of the routes file. Holds the text exactly as written; all validation and conversion
 * into {@code RouteDefinition} happens in {@code ApiRegistry}.
 *
 * <pre>
 * &lt;routes&gt;
 *     &lt;route&gt;
 *         &lt;method&gt;POST&lt;/method&gt;
 *         &lt;path&gt;/orders/{id}/items&lt;/path&gt;
 *         &lt;schema&gt;schemas/add-order-item.json&lt;/schema&gt;
 *         &lt;validator&gt;com.fooddelivery.order.validation.OrderValidator.validateAddItem&lt;/validator&gt;
 *         &lt;controller&gt;OrderController&lt;/controller&gt;
 *     &lt;/route&gt;
 * &lt;/routes&gt;
 * </pre>
 */
@JacksonXmlRootElement(localName = "routes")
public class ApiConfigWrapper {

    // <route> elements sit directly under <routes>, without an extra wrapper element
    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "route")
    private List<RouteXmlDto> routes;

    public List<RouteXmlDto> getRoutes() { return routes; }
    public void setRoutes(List<RouteXmlDto> routes) { this.routes = routes; }

    // One <route> element. Any field may be null or blank here; ApiRegistry decides what is required
    public static class RouteXmlDto {
        private String method;
        private String path;
        private String validator;
        private String schema;
        private String controller;

        public String getValidator() { return validator; }
        public void setValidator(String validator) { this.validator = validator; }
        public String getSchema() { return schema; }
        public void setSchema(String schema) { this.schema = schema; }
        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getController() { return controller; }
        public void setController(String controller) { this.controller = controller; }
    }
}
