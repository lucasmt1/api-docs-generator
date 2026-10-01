# Referência da API

13 endpoints em 3 controllers.

## Conteúdo

- [CustomerController](#customercontroller) — 2 endpoints
- [OrderController](#ordercontroller) — 7 endpoints
- [ProductController](#productcontroller) — 4 endpoints

## CustomerController

Cadastro e consulta de clientes da loja.

### `POST /shop/api/customers` — Cadastrar cliente

Cadastra um novo cliente.

Valida nome e e-mail e grava o cliente. Responde 201 com o cliente criado e o cabeçalho Location.

**Regras de negócio**

- O e-mail não pode estar em uso por outro cliente.

**Corpo da requisição** — `CreateCustomerRequest` (validado com @Valid)

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `name` | `string` | sim | `@NotBlank` `@Size(max=120)` |  |
| `email` | `string` | sim | `@NotBlank` `@Email` |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 201 Created | `CustomerResponse` | Sucesso |
| 400 Bad Request | `MethodArgumentNotValidException` | Nome ou e-mail ausentes ou com formato inválido. |
| 409 Conflict | `DuplicateResourceException` | Já existe um cliente com o e-mail informado. |

**Exemplos**

Requisição:

```json
{
  "name": "Ana Souza",
  "email": "ana@example.com"
}
```

Resposta:

```json
{
  "id": 1,
  "name": "Ana Souza",
  "email": "ana@example.com"
}
```

### `GET /shop/api/customers/{id}` — Consultar cliente

Retorna os dados de um cliente.

Busca o cliente pelo identificador e devolve o registro completo da entidade.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `Customer` | Sucesso |
| 404 Not Found | `NotFoundException` | Nenhum cliente possui o id informado. |

**Exemplos**

Resposta:

```json
{
  "id": 1,
  "name": "Ana Souza",
  "email": "ana@example.com"
}
```

## OrderController

Criação de pedidos e controle do ciclo de vida: pagamento, envio, entrega e cancelamento.

### `GET /shop/api/orders` — Listar pedidos

Lista pedidos paginados, do mais recente para o mais antigo.

Permite filtrar por status. A página começa em 0 e o tamanho vai de 1 a 100.

**Regras de negócio**

- A ordenação é sempre pela data de criação, da mais recente para a mais antiga.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `status` | query | `OrderStatus` | não |  |  |
| `page` | query | `integer (int32)` | não | 0 | `@Min(value=0)` |
| `size` | query | `integer (int32)` | não | 20 | `@Min(value=1)` `@Max(value=100)` |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `PageResponse_OrderResponse` | Sucesso |
| 400 Bad Request | `HandlerMethodValidationException` | page negativo ou size fora do intervalo de 1 a 100. |

**Exemplos**

Resposta:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

### `POST /shop/api/orders` — Criar pedido

Cria um pedido para um cliente existente.

Reserva o estoque de cada item, calcula subtotal, desconto e total e grava o pedido com status CREATED.

**Regras de negócio**

- O cliente e todos os produtos precisam existir.
- Cada item precisa de estoque suficiente; o estoque é baixado na criação do pedido.
- Pedidos com subtotal acima de 500,00 recebem 10% de desconto.
- Cada item aceita de 1 a 10 unidades e o pedido aceita até 50 itens.

**Corpo da requisição** — `CreateOrderRequest` (validado com @Valid)

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `customerId` | `integer (int64)` | sim | `@NotNull` |  |
| `items` | `array<OrderItemRequest>` | sim | `@NotEmpty` `@Size(max=50)` |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 201 Created | `OrderResponse` | Sucesso |
| 400 Bad Request | `MethodArgumentNotValidException` | Corpo inválido: cliente ausente, lista de itens vazia ou quantidade fora de 1 a 10. |
| 404 Not Found | `NotFoundException` | O cliente ou algum produto não existe. |
| 409 Conflict | `InsufficientStockException` | Algum produto não tem estoque suficiente. |

**Exemplos**

Requisição:

```json
{
  "customerId": 1,
  "items": [
    {
      "productId": 10,
      "quantity": 2
    }
  ]
}
```

Resposta:

```json
{
  "id": 100,
  "customerId": 1,
  "status": "CREATED",
  "items": [
    {
      "productId": 10,
      "sku": "CAM-001",
      "quantity": 2,
      "unitPrice": 300.5,
      "lineTotal": 601.0
    }
  ],
  "subtotal": 601.0,
  "discount": 60.1,
  "total": 540.9,
  "createdAt": "2026-09-29T12:00:00Z"
}
```

### `GET /shop/api/orders/customer/{customerId}` — Listar pedidos do cliente

> ⚠️ Obsoleto

Lista todos os pedidos de um cliente.

Endpoint obsoleto: devolve todos os pedidos do cliente sem paginação; prefira a listagem paginada.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `customerId` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `array<OrderResponse>` | Sucesso |

### `GET /shop/api/orders/{id}` — Consultar pedido

Retorna um pedido com seus itens e valores.

Busca o pedido pelo identificador.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `OrderResponse` | Sucesso |
| 404 Not Found | `NotFoundException` | O pedido não existe. |

### `DELETE /shop/api/orders/{id}` — Excluir pedido

> 🔒 Requer: `hasRole('ADMIN')`

Exclui um pedido cancelado.

Remove definitivamente um pedido. Apenas administradores podem excluir.

**Regras de negócio**

- Somente pedidos com status CANCELLED podem ser excluídos.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 204 No Content | — | Sucesso |
| 404 Not Found | `NotFoundException` | O pedido não existe. |
| 422 Unprocessable Content | `InvalidOrderStateException` | O pedido não está cancelado. |

### `POST /shop/api/orders/{id}/cancel` — Cancelar pedido

Cancela um pedido e devolve os itens ao estoque.

Cancelar um pedido já cancelado não tem efeito e devolve o pedido como está.

**Regras de negócio**

- Pedidos SHIPPED ou DELIVERED não podem ser cancelados.
- O estoque de cada item é devolvido no cancelamento.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `OrderResponse` | Sucesso |
| 404 Not Found | `NotFoundException` | O pedido não existe. |
| 422 Unprocessable Content | `InvalidOrderStateException` | O pedido já foi enviado ou entregue. |

### `PATCH /shop/api/orders/{id}/status` — Avançar status do pedido

> 🔒 Requer: `hasRole('ADMIN')`

Move o pedido para o próximo status.

Apenas administradores alteram o status, sempre na ordem CREATED, PAID, SHIPPED, DELIVERED.

**Regras de negócio**

- Transições válidas: CREATED para PAID, PAID para SHIPPED e SHIPPED para DELIVERED.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Corpo da requisição** — `UpdateOrderStatusRequest` (validado com @Valid)

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `status` | `OrderStatus` | sim | `@NotNull` |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `OrderResponse` | Sucesso |
| 400 Bad Request | `MethodArgumentNotValidException` | Status ausente no corpo. |
| 404 Not Found | `NotFoundException` | O pedido não existe. |
| 422 Unprocessable Content | `InvalidOrderStateException` | A transição pedida não é permitida. |

**Exemplos**

Requisição:

```json
{
  "status": "PAID"
}
```

## ProductController

Catálogo de produtos e ajustes de estoque.

### `GET /shop/api/products` — Listar produtos

Lista os produtos do catálogo com paginação.

Aceita os parâmetros padrão de paginação do Spring Data (page, size e sort).

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `page` | query | `integer (int32)` | não | 0 |  |
| `size` | query | `integer (int32)` | não | 20 |  |
| `sort` | query | `array<string>` | não |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `PagedModel_ProductResponse` | Sucesso |

**Exemplos**

Resposta:

```json
{
  "content": [
    {
      "id": 10,
      "sku": "CAM-001",
      "name": "Camiseta",
      "price": 300.5,
      "stock": 25
    }
  ],
  "page": {
    "size": 20,
    "number": 0,
    "totalElements": 1,
    "totalPages": 1
  }
}
```

### `POST /shop/api/products` — Cadastrar produto

> 🔒 Requer: `hasRole('ADMIN')`

Cadastra um produto no catálogo.

Apenas administradores cadastram produtos.

**Regras de negócio**

- O SKU é único no catálogo e aceita apenas letras maiúsculas, números e hífen.
- O preço mínimo é 0,01 e o estoque inicial não pode ser negativo.

**Corpo da requisição** — `CreateProductRequest` (validado com @Valid)

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `sku` | `string` | sim | `@NotBlank` `@Size(max=32)` `@Pattern(regexp=^[A-Z0-9-]+$)` |  |
| `name` | `string` | sim | `@NotBlank` `@Size(max=120)` |  |
| `price` | `number (decimal)` | sim | `@NotNull` `@DecimalMin(value=0.01)` |  |
| `stock` | `integer (int32)` | sim | `@PositiveOrZero` |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 201 Created | `ProductResponse` | Sucesso |
| 400 Bad Request | `MethodArgumentNotValidException` | SKU, nome ou preço inválidos. |
| 409 Conflict | `DuplicateResourceException` | Já existe um produto com o SKU informado. |

**Exemplos**

Requisição:

```json
{
  "sku": "CAM-001",
  "name": "Camiseta",
  "price": 300.5,
  "stock": 25
}
```

Resposta:

```json
{
  "id": 10,
  "sku": "CAM-001",
  "name": "Camiseta",
  "price": 300.5,
  "stock": 25
}
```

### `GET /shop/api/products/{id}` — Consultar produto

Retorna um produto pelo identificador.

Inclui preço e estoque atuais.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `ProductResponse` | Sucesso |
| 404 Not Found | `NotFoundException` | O produto não existe. |

### `PATCH /shop/api/products/{id}/stock` — Ajustar estoque

> 🔒 Requer: `hasRole('ADMIN')`

Soma ou subtrai unidades do estoque de um produto.

Valores negativos removem unidades. Apenas administradores ajustam estoque.

**Regras de negócio**

- O estoque nunca pode ficar negativo.

**Parâmetros**

| Nome | Local | Tipo | Obrigatório | Padrão | Restrições |
| --- | --- | --- | --- | --- | --- |
| `id` | path | `integer (int64)` | sim |  |  |

**Corpo da requisição** — `StockAdjustmentRequest` (validado com @Valid)

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `delta` | `integer (int32)` | sim | `@NotNull` |  |
| `reason` | `string` | não | `@Size(max=200)` |  |

**Respostas**

| Status | Resposta | Quando |
| --- | --- | --- |
| 200 OK | `ProductResponse` | Sucesso |
| 400 Bad Request | `MethodArgumentNotValidException` | delta ausente ou motivo com mais de 200 caracteres. |
| 404 Not Found | `NotFoundException` | O produto não existe. |
| 409 Conflict | `InsufficientStockException` | O ajuste deixaria o estoque negativo. |

**Exemplos**

Requisição:

```json
{
  "delta": -5,
  "reason": "Avaria no transporte"
}
```

## Schemas

### CreateCustomerRequest

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `name` | `string` | sim | `@NotBlank` `@Size(max=120)` |  |
| `email` | `string` | sim | `@NotBlank` `@Email` |  |

### CreateOrderRequest

Items to buy for an existing customer.

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `customerId` | `integer (int64)` | sim | `@NotNull` |  |
| `items` | `array<OrderItemRequest>` | sim | `@NotEmpty` `@Size(max=50)` |  |

### CreateProductRequest

Data required to register a new product in the catalog.

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `sku` | `string` | sim | `@NotBlank` `@Size(max=32)` `@Pattern(regexp=^[A-Z0-9-]+$)` |  |
| `name` | `string` | sim | `@NotBlank` `@Size(max=120)` |  |
| `price` | `number (decimal)` | sim | `@NotNull` `@DecimalMin(value=0.01)` |  |
| `stock` | `integer (int32)` | sim | `@PositiveOrZero` |  |

### Customer

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `id` | `integer (int64)` | não |  |  |
| `createdAt` | `string (date-time)` | não |  |  |
| `updatedAt` | `string (date-time)` | não |  |  |
| `name` | `string` | não |  |  |
| `email` | `string` | não |  |  |

### CustomerResponse

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `id` | `integer (int64)` | não |  |  |
| `name` | `string` | não |  |  |
| `email` | `string` | não |  |  |

### OrderItemRequest

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `productId` | `integer (int64)` | sim | `@NotNull` |  |
| `quantity` | `integer (int32)` | sim | `@Min(value=1)` `@Max(value=10)` |  |

### OrderItemResponse

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `productId` | `integer (int64)` | não |  |  |
| `sku` | `string` | não |  |  |
| `quantity` | `integer (int32)` | sim |  |  |
| `unitPrice` | `number (decimal)` | não |  |  |
| `lineTotal` | `number (decimal)` | não |  |  |

### OrderResponse

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `id` | `integer (int64)` | não |  |  |
| `customerId` | `integer (int64)` | não |  |  |
| `status` | `OrderStatus` | não |  |  |
| `items` | `array<OrderItemResponse>` | não |  |  |
| `subtotal` | `number (decimal)` | não |  |  |
| `discount` | `number (decimal)` | não |  |  |
| `total` | `number (decimal)` | não |  |  |
| `createdAt` | `string (date-time)` | não |  |  |

### OrderStatus

Lifecycle of an order.

**Valores**: `CREATED`, `PAID`, `SHIPPED`, `DELIVERED`, `CANCELLED`

### PageMetadata

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `size` | `integer (int64)` | sim |  |  |
| `number` | `integer (int64)` | sim |  |  |
| `totalElements` | `integer (int64)` | sim |  |  |
| `totalPages` | `integer (int64)` | sim |  |  |

### PageResponse_OrderResponse

Page of results returned by list endpoints.

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `content` | `array<OrderResponse>` | não |  |  |
| `page` | `integer (int32)` | sim |  |  |
| `size` | `integer (int32)` | sim |  |  |
| `totalElements` | `integer (int64)` | sim |  |  |
| `totalPages` | `integer (int32)` | sim |  |  |

### PagedModel_ProductResponse

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `content` | `array<ProductResponse>` | sim |  |  |
| `page` | `PageMetadata` | sim |  |  |

### ProductResponse

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `id` | `integer (int64)` | não |  |  |
| `sku` | `string` | não |  |  |
| `name` | `string` | não |  |  |
| `price` | `number (decimal)` | não |  |  |
| `stock` | `integer (int32)` | sim |  |  |

### StockAdjustmentRequest

Relative stock change; negative values remove units.

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `delta` | `integer (int32)` | sim | `@NotNull` |  |
| `reason` | `string` | não | `@Size(max=200)` |  |

### UpdateOrderStatusRequest

| Campo | Tipo | Obrigatório | Restrições | Descrição |
| --- | --- | --- | --- | --- |
| `status` | `OrderStatus` | sim | `@NotNull` |  |
