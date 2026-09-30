package dev.apidocs.core.context;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.SchemaInfo;
import dev.apidocs.core.testsupport.ModelFixtures;
import org.junit.jupiter.api.Test;

class SliceBuilderTest {

    @Test
    void collectsTheSchemasAndServiceMethodsOfAController() {
        ApiModel model = ModelFixtures.orderApi();

        ControllerSlice slice = new SliceBuilder().slice(model, model.controllers().get(0));

        assertThat(slice.schemas()).extracting(SchemaInfo::name)
                .containsExactly("CreateOrderRequest", "Customer", "OrderItemRequest", "OrderResponse", "OrderStatus");
        assertThat(slice.serviceMethods()).extracting(ref -> ref.service() + "#" + ref.method().name())
                .containsExactly("OrderService#create", "OrderService#find");
        assertThat(slice.exceptionMappings()).hasSize(3);
    }
}
