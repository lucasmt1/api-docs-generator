# Documentação Técnica — Shop API

## Visão geral

A Shop API gerencia o catálogo de produtos, o cadastro de clientes e o ciclo de vida dos pedidos de uma loja virtual.

Aplicações front-end usam a API para criar pedidos com reserva automática de estoque, acompanhar o status e consultar produtos. Administradores ajustam estoque e avançam pedidos pelas etapas de pagamento, envio e entrega.

## Stack

| Item | Valor |
| --- | --- |
| Java | 21 |
| Spring Boot | 4.1.1 |
| Ferramenta de build | maven |
| Dependências principais | `h2`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-boot-starter-validation`, `spring-boot-starter-webmvc` |

## Números

| Métrica | Valor |
| --- | --- |
| Controllers | 3 |
| Endpoints | 13 |
| Schemas (DTOs) | 14 |
| Entidades | 4 |
| Services | 3 |
| Repositories | 3 |

## Configuração

| Item | Valor |
| --- | --- |
| Nome da aplicação | `shop-api` |
| Context path | `/shop` |

## Como executar

```bash
mvn spring-boot:run
```

## Conceitos do domínio

| Conceito | Descrição |
| --- | --- |
| Cliente | Pessoa que compra na loja, identificada por um e-mail único. |
| Produto | Item do catálogo com SKU único, preço e quantidade em estoque. |
| Pedido | Compra de um cliente, com itens, valores calculados e um status de ciclo de vida. |
| Item do pedido | Produto e quantidade dentro de um pedido, com o preço unitário da compra. |

## Regras de negócio

### Pedidos

- O status evolui na ordem CREATED, PAID, SHIPPED, DELIVERED.
- Pedidos enviados ou entregues não podem ser cancelados.
- Somente pedidos cancelados podem ser excluídos.
- Subtotais acima de 500,00 recebem 10% de desconto.

### Estoque

- A criação do pedido baixa o estoque e o cancelamento o devolve.
- O estoque nunca pode ficar negativo.

### Cadastros

- O SKU do produto e o e-mail do cliente são únicos.

## Tratamento de erros

Os erros são tratados de forma centralizada por um @RestControllerAdvice, que devolve um corpo ApiError com status, erro e mensagem. Recursos inexistentes geram 404, violações de regra de negócio geram 409, transições de status inválidas geram 422 e falhas de validação geram 400.

| Exceção | Status |
| --- | --- |
| `BusinessException` | 409 Conflict |
| `InvalidOrderStateException` | 422 Unprocessable Content |
| `MethodArgumentNotValidException` | 400 Bad Request |
| `NotFoundException` | 404 Not Found |

## Glossário

| Termo | Definição |
| --- | --- |
| SKU | Código único que identifica um produto no catálogo. |
| Subtotal | Soma de preço unitário vezes quantidade de todos os itens, antes do desconto. |
| Desconto | Abatimento de 10% aplicado a pedidos com subtotal acima de 500,00. |
