# Parking Management

Sistema de gerenciamento de estacionamento . A aplicação recebe eventos de um simulador de garagem via webhook, controla a ocupação de vagas e setores, e calcula a receita por setor/dia com precificação dinâmica.

---

## Visão Geral

O simulador (`garage-sim`) gerencia as cancelas físicas de uma garagem e envia eventos à aplicação em três momentos:

| Evento | Descrição |
|--------|-----------|
| `ENTRY` | Veículo passou pela cancela de entrada |
| `PARKED` | Veículo estacionou em uma vaga específica |
| `EXIT` | Veículo saiu da garagem — dispara o cálculo do valor a cobrar |

Ao iniciar, a aplicação consome `GET /garage` do simulador para carregar a estrutura completa da garagem (setores, vagas e cancelas) no banco de dados.

---

## Arquitetura

O projeto segue **Clean Architecture** com quatro camadas de pacotes bem delimitadas:

```
adapter  →  application  →  domain  ←  infrastructure
```

| Camada | Pacote | Responsabilidade |
|--------|--------|-----------------|
| **Adapter** | `adapter/controller/` | Controllers REST — recebem e devolvem HTTP |
| | `adapter/model/request/` | Objetos de entrada HTTP |
| | `adapter/model/response/` | Objetos de saída HTTP + `ErrorResponse` |
| **Application** | `application/usecase/` | Orquestração dos casos de uso |
| | `application/mapper/` | Conversão entre camadas (request ↔ dto ↔ entity ↔ response) |
| | `application/exception/` | `GlobalExceptionHandler` e exceções tipadas da aplicação |
| **Domain** | `domain/model/` | DTOs e enums de domínio (sem dependências de framework) |
| | `domain/service/` | Regras de negócio puras (`BillingService`, `PricingService`) |
| | `domain/exception/` | Exceções de violação de regras de negócio (`BusinessException`) |
| **Infrastructure** | `infrastructure/persistence/` | Entidades JPA e repositórios Spring Data |
| | `infrastructure/client/` | Cliente HTTP do simulador (`SimulatorClient`) |
| | `infrastructure/config/` | Beans de configuração Spring |

### Regras de negócio

- **Precificação dinâmica** — o preço por hora varia conforme a ocupação do setor:

  | Ocupação | Multiplicador |
  |----------|--------------|
  | < 25% | 0,9× (desconto) |
  | 25% – 49% | 1,0× (base) |
  | 50% – 74% | 1,1× |
  | 75% – 100% | 1,25× |

- **Cobrança por duração** — estadas de até 30 minutos são gratuitas; acima disso, cobra-se por hora cheia (teto).
- **Capacidade** — entradas são bloqueadas (HTTP 409) quando o setor está 100% ocupado.

### Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/webhook` | Recebe eventos `ENTRY`, `PARKED` e `EXIT` do simulador |
| `GET` | `/revenue` | Retorna a receita de um setor em uma data específica |

A documentação interativa (Swagger UI) está disponível em:
```
http://localhost:3003/swagger-ui.html
```

---

## Stack

- **Java 21** + **Spring Boot 3.3.5**
- **Spring Data JPA** / Hibernate — persistência
- **Flyway** — migrações de banco de dados (V1–V5)
- **MySQL 8** — banco de dados relacional
- **SpringDoc OpenAPI 2.6** — documentação da API
- **Testcontainers** — testes de integração com banco real

---

## Executar com Docker

### Iniciar

```bash
sudo docker compose up --build -d
```

Sobe três containers em ordem:
1. `parking-simulator` — simulador da garagem (porta 3000)
2. `parking-db` — MySQL 8 (porta 3306), aguarda healthcheck
3. `parking-app` — a aplicação (porta 3003), aguarda o banco estar saudável

### Finalizar e limpar

```bash
sudo docker compose down -v --rmi all
```

Remove os containers, o volume do banco de dados e as imagens geradas.

---

## Executar localmente (sem Docker)

Pré-requisitos: Java 21, Maven 3.9+, MySQL 8 rodando em `localhost:3306`.

```bash
# Build
mvn clean package -DskipTests

# Run
mvn spring-boot:run
```

### Testes

```bash
# Unitários (padrão)
mvn test

# Unitários + integração (requer Docker para Testcontainers)
mvn test -Pintegration-tests
```

---

## Variáveis de ambiente

| Variável | Padrão | Descrição |
|----------|--------|-----------|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/parking_management` | URL do banco |
| `SPRING_DATASOURCE_USERNAME` | `parking` | Usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | `parking123` | Senha do banco |
| `SIMULATOR_BASE_URL` | `http://localhost:3000` | URL base do simulador |
