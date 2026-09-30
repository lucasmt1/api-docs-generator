package dev.apidocs.core.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.Constraint;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.model.EndpointInfo;
import dev.apidocs.core.model.ErrorResponse;
import dev.apidocs.core.model.ExceptionMapping;
import dev.apidocs.core.model.HttpMethod;
import dev.apidocs.core.model.MethodRef;
import dev.apidocs.core.model.ObjectRef;
import dev.apidocs.core.model.ParameterInfo;
import dev.apidocs.core.model.ParameterLocation;
import dev.apidocs.core.model.RequestBodyInfo;
import dev.apidocs.core.model.ResponseInfo;
import dev.apidocs.core.model.ScalarKind;
import dev.apidocs.core.model.ScalarType;
import dev.apidocs.core.model.ServiceInfo;
import dev.apidocs.core.model.ServiceMethod;
import dev.apidocs.core.testsupport.JavaSnippets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ErrorResolverTest {

    @Test
    void combinesValidationEndpointAndServiceExceptions() {
        ExceptionStatusResolver statuses = new ExceptionStatusResolver(
                JavaSnippets.index(
                        "package com.x; public abstract class BusinessException extends RuntimeException {}",
                        "package com.x; public class StockException extends BusinessException {}",
                        "package com.x; public class LocalException extends RuntimeException {}"),
                List.of(new ExceptionMapping("BusinessException", 409),
                        new ExceptionMapping("MethodArgumentNotValidException", 400)));
        ServiceInfo service = new ServiceInfo("OrderService", "com.x.OrderService", List.of(), List.of(
                new ServiceMethod("create", "Order create()", "{}", true, false,
                        List.of("StockException", "IllegalArgumentException"), List.of(), "")));
        EndpointInfo endpoint = new EndpointInfo("C#create", HttpMethod.POST, "/orders", "create",
                List.of(new ParameterInfo("limit", ParameterLocation.QUERY, new ScalarType(ScalarKind.INTEGER), false,
                        "", List.of(Constraint.of("Max", "value", "5")))),
                new RequestBodyInfo(new ObjectRef("CreateOrder"), true), new ResponseInfo(201, null), List.of(),
                List.of("LocalException"), "", false, "", "", List.of(), List.of(),
                List.of(new MethodRef("OrderService", "create")));
        ControllerInfo controller = new ControllerInfo("C", "com.x.C", "/", "", List.of(), List.of(endpoint));

        ControllerInfo resolved = new ErrorResolver(statuses, List.of(service)).resolve(controller);

        assertThat(resolved.endpoints().get(0).errors()).containsExactly(
                new ErrorResponse(400, "HandlerMethodValidationException"),
                new ErrorResponse(400, "MethodArgumentNotValidException"),
                new ErrorResponse(409, "StockException"),
                new ErrorResponse(500, "LocalException"));
    }
}
