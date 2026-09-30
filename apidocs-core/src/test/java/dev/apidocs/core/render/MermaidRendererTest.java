package dev.apidocs.core.render;

import static org.assertj.core.api.Assertions.assertThat;

import dev.apidocs.core.model.ApiModel;
import dev.apidocs.core.model.ControllerInfo;
import dev.apidocs.core.testsupport.ModelFixtures;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class MermaidRendererTest {

    private final MermaidRenderer renderer = new MermaidRenderer();

    @Test
    void drawsLayersAndDependencies() {
        assertThat(renderer.layers(ModelFixtures.orderApi())).isEqualTo("""
                flowchart LR
                  subgraph Controllers
                    C_OrderController["OrderController"]
                  end
                  subgraph Services
                    S_OrderService["OrderService"]
                  end
                  subgraph Repositories
                    R_CustomerRepository["CustomerRepository"]
                    R_OrderRepository["OrderRepository"]
                  end
                  subgraph Entities
                    E_Customer["Customer"]
                    E_Order["Order"]
                  end
                  C_OrderController --> S_OrderService
                  S_OrderService --> R_OrderRepository
                  R_CustomerRepository --> E_Customer
                  R_OrderRepository --> E_Order
                """);
    }

    @Test
    void groupsByPackageWhenTheDiagramIsLarge() {
        ApiModel base = ModelFixtures.orderApi();
        List<ControllerInfo> controllers = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            controllers.add(new ControllerInfo("C" + i, "com.shop.web.C" + i, "/", "", List.of("OrderService"), List.of()));
        }
        ApiModel large = new ApiModel(base.project(), controllers, base.schemas(), base.entities(), base.services(),
                base.repositories(), base.exceptionMappings(), base.warnings());

        String diagram = renderer.layers(large);

        assertThat(diagram).contains("C_com_shop_web[\"com.shop.web (45)\"]");
        assertThat(diagram).contains("C_com_shop_web --> S_com_shop");
        assertThat(diagram).doesNotContain("C_C1[");
    }

    @Test
    void drawsTheEntityRelationshipDiagram() {
        assertThat(renderer.entities(ModelFixtures.orderApi().entities())).isEqualTo("""
                erDiagram
                  Customer {
                    long id PK
                    string name
                    string email UK
                  }
                  Order {
                    long id PK
                    enum status
                    decimal total
                  }
                  Customer ||--o{ Order : customer
                """);
    }
}
