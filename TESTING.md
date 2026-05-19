# Guia Completo de Testes (Microservices Platform)

Este documento detalha como inicializar todo o ambiente, realizar chamadas HTTP nos endpoints da aplicação, validar a idempotência e acompanhar o comportamento sistêmico nas ferramentas de observabilidade e tracing.

---

## 1. Inicializando o Ambiente

Abra seu terminal na raiz do projeto (`c:/spring/2026/microservices-v2`) e siga os passos:

1. **Suba a infraestrutura base:**
   ```bash
   docker-compose up -d
   ```
   *Isso inicializará o PostgreSQL (com criação dos bancos via init-db.sql), Redis, RabbitMQ, Jaeger, Prometheus e Grafana.*

2. **Inicie os Microsserviços:**
   Abra o projeto em sua IDE (IntelliJ, VS Code, etc) e execute as três classes `main` na seguinte ordem:
   - `api-gateway` (porta 8080)
   - `auth-service` (porta 8081)
   - `order-service` (porta 8082)
   
   *(As tabelas de banco de dados serão criadas automaticamente pelo Flyway no primeiro boot de cada serviço).*

---

## 2. Testes Manuais de Endpoints (cURL / Postman)

O `api-gateway` roda na porta `8080` e é a nossa única porta de entrada.

### A. Registro de Usuário
```bash
curl -X POST http://localhost:8080/auth/register \
-H "Content-Type: application/json" \
-d '{"name":"João Silva", "email":"joao@email.com", "password":"123"}'
```
**Esperado:** Retorno `200 OK` contendo um JSON com o `token`.

### B. Login (Gerando o JWT)
```bash
curl -X POST http://localhost:8080/auth/login \
-H "Content-Type: application/json" \
-d '{"email":"joao@email.com", "password":"123"}'
```
**Esperado:** Retorno `200 OK` contendo o `token`. 
> [!IMPORTANT]
> **Copie este token**, ele será necessário nas próximas requisições! Substitua `<SEU_TOKEN>` nos comandos abaixo por ele.

### C. Buscar Perfil Logado
```bash
curl -X GET http://localhost:8080/users/me \
-H "Authorization: Bearer <SEU_TOKEN>"
```
**Esperado:** O Gateway valida a assinatura, extrai o ID do usuário, adiciona o header `X-User-Id` e encaminha para o `auth-service`. Você receberá seus dados em JSON.

### D. Criar Pedido
Gere um UUID qualquer na internet (ex: `e3b0c442-989b-464c-8646-123456789abc`) para usar como `Idempotency-Key`.
```bash
curl -X POST http://localhost:8080/orders \
-H "Authorization: Bearer <SEU_TOKEN>" \
-H "Idempotency-Key: e3b0c442-989b-464c-8646-123456789abc" \
-H "Content-Type: application/json" \
-d '{"total": 150.50}'
```
**Esperado:** Retorno `200 OK` com os dados do pedido recém-criado, incluindo status `CREATED` e o UUID gerado do pedido.

---

## 3. Como Testar a Idempotência

A idempotência foi implementada utilizando **Locks e Cache no Redis**. A regra de negócio é: uma mesma chave não pode criar dois pedidos distintos para o mesmo usuário.

**Teste:**
1. Execute a requisição `POST /orders` (passo **2.D**) exatamente como fez acima, enviando a **MESMA** `Idempotency-Key` e os mesmos dados.
2. **Resultado Esperado:** O sistema **não vai processar uma nova inserção no banco e não vai emitir um novo evento pro RabbitMQ**. Em vez disso, ele irá interceptar a chamada no `IdempotencyService` (que identificou a chave no Redis como `SUCCESS`) e devolverá **imediatamente** o exato JSON que foi gerado na primeira tentativa.
3. Se você usar uma ferramenta de stress testing (ex: JMeter/K6) e tentar disparar 100 requisições simultâneas com a mesma `Idempotency-Key`, apenas **uma** passará. As outras 99 tentarão adquirir o lock no Redis, verão que ele está como `PROCESSING`, e lançarão uma exceção que reverterá em um `HTTP 409 Conflict` (ou 500 caso o handler global não converta).

---

## 4. Testando o Tracing Distribuído (OpenTelemetry + Jaeger)

Graças à instrumentação do Micrometer, o Trace ID viaja por todo o sistema.

1. Acesse o **Jaeger UI**: `http://localhost:16686`
2. No menu esquerdo, em **Service**, selecione `api-gateway` e clique em **Find Traces**.
3. Escolha o Trace referente à sua criação de pedido (`POST /orders`).
4. **O que observar:** Você verá uma "árvore" (Gantt chart) impressionante mostrando a linha do tempo exata:
   - A requisição batendo no filtro do **Gateway**.
   - O encaminhamento HTTP para o **Order Service**.
   - O tempo que o Order Service levou pra executar a transação no banco (Postgres).
   - O tempo gasto disparando o evento pro RabbitMQ.

---

## 5. Visualizando Eventos no RabbitMQ

Quando você criou o pedido, o `order-service` despachou uma mensagem assíncrona.

1. Acesse o **RabbitMQ Management**: `http://localhost:15672` (Usuário: `guest`, Senha: `guest`).
2. Vá até a aba **Queues** e procure pela fila `order-created-queue`.
3. Lá você notará que houve tráfego de mensagens. Você pode olhar nos logs do terminal do `order-service` para confirmar que o `OrderEventConsumer` recebeu a notificação.

---

## 6. Observabilidade com Grafana e Prometheus

O Prometheus está "raspando" os atuadores do Spring Boot (`/actuator/prometheus`) a cada 10 segundos.

1. Acesse o **Grafana**: `http://localhost:3000` (Usuário: `admin`, Senha: `admin`).
2. Logo no primeiro acesso, o Grafana pedirá para configurar um Data Source.
   - Escolha **Prometheus**.
   - Na URL do servidor Prometheus, insira `http://prometheus:9090` (nome do container).
   - Salve.
3. Para importar um Dashboard completo focado em Micrometer/Spring Boot:
   - Vá no menu lateral -> Dashboards -> Import.
   - Digite o ID **4701** (JVM Micrometer padrão) ou **11378** (Spring Boot Statistics) e clique em Load.
   - Selecione seu Data Source criado.
4. **O que você verá:** Taxa de requisições, tempo de resposta (`latency`), uso de CPU de cada microsserviço, métricas da JVM (Heap Memory, Threads) e estatísticas do Tomcat.
