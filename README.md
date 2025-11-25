# Spring Data AOT Coffee Shop Demo

A focused demonstration of **Spring Data Ahead-of-Time (AOT)** - showing why compile-time query processing is a 
great addition for Spring applications.

## Why This Matters 🎯

**Traditional Spring Data** parses query methods at runtime using reflection. Every time your app starts,
Spring analyzes method names like `findByNameContainingIgnoreCase`, figures out the SQL, and creates repository
implementations. This works, but it's slow and error-prone.

**Spring Data AOT** moves all this work to compile time. Here's why that's powerful:

### 1. Catch Errors Before They Reach Production

**Without AOT:**
```java
List<Coffee> findByNamme(String name);  // Typo! Fails at runtime ❌
```
Your app starts fine. You deploy to production. Then, when a customer searches for coffee, it crashes with a cryptic error.

**With AOT + Validation:**
```java
List<Coffee> findByNamme(String name);  // Test suite catches the typo ✅
```

**Important:** AOT itself doesn't fail the build on typos, it silently skips methods it can't parse and falls back to 
runtime reflection. However, this project includes a **test-based validation** (`AotRepositoryValidationTest`) that:
- Compares declared repository methods against AOT-generated metadata
- Validates method signatures (not just names) to handle overloaded methods
- Fails the build if any custom methods are skipped
- Catches typos, invalid property references, and malformed queries

Run `./mvnw clean package` and the test suite will catch repository method errors before deployment.

### 2. Faster Startup & Lower Memory Usage

**The Problem:** Runtime query parsing adds seconds to startup time and uses memory for reflection.

**The Solution:** AOT generates all repository code at build time:
- **50-70% faster startup** - Critical for serverless and microservices
- **Reduced memory footprint** - No reflection or runtime query parsing
- **Native image ready** - Sub-second startup with GraalVM

### 3. Better Developer Experience

**What AOT gives you:**
- Pre-generated repository implementations you can inspect
- Clear trace logging showing which methods are AOT-processed
- Optimized SQL queries generated at build time
- Foundation for GraalVM native image compilation

**What the validation test adds:**
- Automated checking that all your custom repository methods are processable
- Clear error messages showing which methods have issues
- Signature-based validation to catch parameter type mismatches
- Protection against deploying methods that will fall back to reflection

## Getting Started (Zero Setup Required!)

This demo uses **H2 in-memory database** - no Docker, no PostgreSQL, no setup needed.

### Run the Application

```bash
./mvnw spring-boot:run
```

That's it! The app starts on http://localhost:8080 with sample data pre-loaded.

### See AOT in Action

**Trigger AOT processing** to see compile-time query generation:

```bash
./mvnw clean package
```

Look for this output:
```
[INFO] --- spring-boot-maven-plugin:4.0.0-RC1:process-aot (process-aot) @ spring-data-aot ---
[INFO] Processing Spring AOT sources...
```

**Check the generated code:**
```bash
ls target/spring-aot/main/sources/dev/danvega/coffee/coffee/
ls target/spring-aot/main/sources/dev/danvega/coffee/order/
```

You'll see generated files like:
- `CoffeeRepositoryImpl__AotRepository.java` - Pre-compiled repository implementation
- `OrderRepositoryImpl__AotRepository.java`
- `OrderItemRepositoryImpl__AotRepository.java`

These contain all the query logic, generated at compile time!

**View the metadata:**
```bash
cat target/classes/dev/danvega/coffee/coffee/CoffeeRepository.json
```

This shows exactly which methods were AOT-processed and which were skipped.

## What This Demo Shows

This project demonstrates the key AOT capabilities with a simple coffee shop API.

### Repository Methods

**CoffeeRepository** - 3 methods showcasing different AOT features:

```java
// 1. Complex derived query - LIKE + case-insensitive
List<Coffee> findByNameContainingIgnoreCase(String name);
// AOT generates: SELECT * FROM coffee WHERE UPPER(name) LIKE UPPER(?)

// 2. Multi-property query - AND conditions
List<Coffee> findBySizeAndPriceGreaterThan(Size size, BigDecimal price);
// AOT generates: SELECT * FROM coffee WHERE size = ? AND price > ?

// 3. Custom SQL - compile-time validation
@Query("SELECT * FROM coffee WHERE size = :size AND price <= :maxPrice ORDER BY price DESC")
List<Coffee> findAffordableCoffeesBySize(String size, BigDecimal maxPrice);
// AOT validates SQL syntax at build time
```

**OrderRepository** - 3 methods showcasing temporal queries and JOINs:

```java
// 1. Simple derived query
List<Order> findByCustomerName(String customerName);

// 2. Multi-property with date comparison
List<Order> findByStatusAndOrderDateAfter(OrderStatus status, LocalDateTime date);

// 3. Complex JOIN across 3 tables - validated at build time
@Query("SELECT DISTINCT o.* FROM orders o INNER JOIN order_items oi ON o.id = oi.order_id ...")
List<Order> findOrdersByCoffeeName(String coffeeName);
```

**OrderItemRepository** - 2 methods showcasing relationships:

```java
// 1. Foreign key relationship
List<OrderItem> findByOrderId(Long orderId);

// 2. JOIN with sorting - validated at compile time
@Query("SELECT oi.* FROM order_items oi INNER JOIN coffee c ON oi.coffee_id = c.id ...")
List<OrderItem> findOrderItemsWithCoffeeDetails(Long orderId);
```

### Try the API

**Using IntelliJ HTTP Client:**

Open `client.http` in IntelliJ to run pre-configured requests with comments explaining each AOT feature being demonstrated.

**Or use curl:**

**Search for coffees (complex LIKE query):**
```bash
curl "http://localhost:8080/api/coffees/search?pattern=latte"
```

**Filter by size and price (multi-property query):**
```bash
curl "http://localhost:8080/api/coffees/filter?size=MEDIUM&minPrice=5.00"
```

**Find affordable coffees (custom SQL query):**
```bash
curl "http://localhost:8080/api/coffees/affordable?size=LARGE&maxPrice=6.00"
```

**Find orders by customer (simple derived query):**
```bash
curl "http://localhost:8080/api/orders/customer/Alice%20Johnson"
```

**Find recent orders by status (temporal query):**
```bash
curl "http://localhost:8080/api/orders/recent?status=PENDING&since=2024-01-01T00:00:00"
```

**Find orders containing a specific coffee (complex JOIN):**
```bash
curl "http://localhost:8080/api/orders/by-coffee?coffeeName=Cappuccino"
```

## How AOT Works

### 1. Build-Time Analysis

When you run `./mvnw clean package`, the AOT processor:
- Scans all repository interfaces
- Parses method names like `findByNameContainingIgnoreCase`
- Generates optimized SQL
- Validates custom `@Query` annotations
- Creates repository implementations

### 2. Configuration

**pom.xml** - The `process-aot` goal is **required** for repository AOT:
```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <executions>
    <execution>
      <id>process-aot</id>
      <goals>
        <goal>process-aot</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

**Important:** This goal is not optional. It triggers the AOT engine that:
- Generates repository implementations (`CoffeeRepositoryImpl__AotRepository.java`)
- Creates JSON metadata for validation
- Pre-compiles all query logic

Without `process-aot`, repositories fall back to runtime reflection—no faster startup, no pre-compiled queries, and no metadata for validation.

**application.yaml** - H2 in-memory database:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:coffee
    username: sa
    password:
    driver-class-name: org.h2.Driver
```

### 3. Runtime Benefits

At runtime, Spring uses the pre-generated repository implementations:
- No reflection
- No query parsing
- No method name analysis
- Just direct SQL execution

## AOT Validation Strategy

This project demonstrates a practical approach to catching repository method errors at build time.

### The Problem

Spring Data AOT doesn't fail the build when it encounters invalid repository methods. Instead, it:
1. Logs an error (e.g., "No property 'naame' found for type 'Coffee'")
2. Skips the method in AOT processing
3. Falls back to runtime reflection for that method
4. Continues the build successfully

This means typos can slip into production despite using AOT.

### The Solution: Test-Based Validation

`AotRepositoryValidationTest` provides build-time validation by:

**1. Comparing signatures, not just names:**
```java
// Declared in repository:
List<Coffee> findByNaame(String name);

// Test extracts: "findByNaame(String)"
// AOT metadata contains: "findByNameContainingIgnoreCase(String)", "findBySizeAndPriceGreaterThan(Size,BigDecimal)"
// Missing: "findByNaame(String)" ❌ → Test fails
```

**2. Handling overloaded methods correctly:**
```java
// Both signatures are validated separately:
deleteAll()           // No parameters
deleteAll(Iterable)   // With Iterable parameter
```

**3. Providing clear error messages:**
```
AOT skipped methods in CoffeeRepository: [findByNaame(String)]
```

### When Spring Adds Built-in Validation

The Spring Data team is exploring "full AOT repository mode" that would require ALL methods to be AOT-representable or fail the build. When that's available, this test-based approach can be removed. Until then, it provides essential build-time protection.

## Sample Data

The application comes pre-loaded with:
- **15 coffee products** (Espresso, Latte, Cappuccino, Cold Brew, etc.)
- **6 sample orders** from various customers
- **Order items** demonstrating relationships

## Technology Stack

- **Spring Boot 4.0.0-RC2** - Latest with enhanced AOT support
- **Spring Data JDBC** - Simpler than JPA, better AOT compatibility
- **H2 Database** - In-memory, zero setup required
- **Java 25** - Latest Java features
- **Jackson 3** - JSON processing for metadata validation
- **JUnit 5** - Test framework for AOT validation
- **Maven** - Build tool with AOT plugin

## Testing

This project demonstrates two modern approaches to controller testing in Spring Boot 4.

### MockMvcTester (AssertJ Style)

See `CoffeeControllerTest` for this approach. MockMvcTester integrates with AssertJ for expressive assertions:

```java
@WebMvcTest(CoffeeController.class)
class CoffeeControllerTest {

    @Autowired
    MockMvcTester mockMvcTester;

    @MockitoBean
    CoffeeRepository coffeeRepository;

    @Test
    void shouldReturnAllCoffees() {
        when(coffeeRepository.findAll()).thenReturn(List.of(...));

        assertThat(mockMvcTester.get().uri("/api/coffee"))
            .hasStatusOk()
            .bodyJson()
            .extractingPath("$")
            .asArray()
            .hasSize(2);
    }
}
```

**Best for:** Teams already using AssertJ, preference for assertion-style testing.

### RestTestClient (Unified API)

See `OrderControllerTest` for this approach. RestTestClient is Spring Framework 7's unified REST testing API:

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    OrderRepository orderRepository;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindTo(mockMvc).build();
    }

    @Test
    void shouldReturnAllOrders() {
        when(orderRepository.findAll()).thenReturn(List.of(...));

        client.get().uri("/api/orders")
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$").isArray()
            .jsonPath("$.length()").isEqualTo(1);
    }
}
```

**Best for:** Consistency across different testing scenarios. RestTestClient adapts to multiple contexts:

| Binding Method | Use Case | Speed |
|----------------|----------|-------|
| `bindToController(controller)` | Unit test without Spring context | Fastest |
| `bindTo(mockMvc)` | MVC slice test with validation/security | Fast |
| `bindToApplicationContext(context)` | Full integration with real services | Slower |
| `bindToServer()` | Real HTTP server for CORS/compression | Slowest |

### Which Should You Choose?

| Criteria | MockMvcTester | RestTestClient |
|----------|---------------|----------------|
| API style | AssertJ assertions | WebTestClient-style fluent |
| Learning curve | Familiar to AssertJ users | New unified API |
| Test type flexibility | MVC slice tests only | All test types |
| Future direction | Stable | Spring's recommended path forward |

For more details on RestTestClient, see [Spring Framework 7 RestTestClient](https://www.danvega.dev/blog/spring-framework-7-rest-test-client).

## The Value Proposition (Summary)

| Feature | Traditional Spring Data | Spring Data AOT | AOT + Validation Test |
|---------|------------------------|-----------------|----------------------|
| Query parsing | Runtime (slow) | Compile-time (fast) | Compile-time (fast) |
| Error detection | Runtime (production crash) | Logged but ignored | Build failure |
| Startup time | Seconds | Milliseconds | Milliseconds |
| Memory usage | Higher (reflection) | Lower (pre-generated code) | Lower (pre-generated code) |
| Native image support | Requires manual hints | Works out-of-box | Works out-of-box |
| Typo protection | None | None (falls back to reflection) | Build fails on typos |
| Generated code | No | Yes (inspectable) | Yes (validated) |

## Try Breaking It! 🔨

Want to see the validation test in action? Try these experiments:

**1. Introduce a typo in a method name:**

Edit `CoffeeRepository.java` and add:
```java
List<Coffee> findByNammeContaining(String name);  // "Namme" instead of "Name"
```

Run `./mvnw clean package` - the test will fail with:
```
AOT skipped methods in CoffeeRepository: [findByNammeContaining(String)]
```

**How it works:**
- AOT silently skips the method (logs an error but continues)
- The validation test detects the method is missing from AOT metadata
- Build fails with a clear message showing the exact signature

**2. Check what happens without the validation test:**

Comment out the test methods in `AotRepositoryValidationTest.java` and run `./mvnw clean package`.
The build will succeed! This shows why the test is important - AOT doesn't fail the build on its own.

**3. See the AOT error logs:**

With the typo still present, run:
```bash
./mvnw clean package -DskipTests
```

Look for this in the output:
```
ERROR: Failed to contribute Repository method [CoffeeRepository.findByNammeContaining]
PropertyReferenceException: No property 'namme' found for type 'Coffee'
```

AOT detects the error but doesn't fail the build - it just skips the method and continues.

## H2 Console (Optional)

View your database in the browser at http://localhost:8080/h2-console

- **JDBC URL:** `jdbc:h2:mem:coffee`
- **Username:** `sa`
- **Password:** (leave blank)

## Learning More

- [Spring Data Ahead of Time Repositories - Part 2](https://spring.io/blog/2025/11/25/spring-data-ahead-of-time-repositories-part-2) - Official Spring blog post
- [Spring Data AOT Documentation](https://docs.spring.io/spring-data/commons/reference/4.0/aot.html)
- [GraalVM Native Image Guide](https://www.graalvm.org/latest/reference-manual/native-image/)
- [Spring Boot 4.0 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Release-Notes)