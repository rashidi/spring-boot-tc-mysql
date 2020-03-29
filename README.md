# Spring Boot: MySQL Container Integration [![Dependabot Status](https://api.dependabot.com/badges/status?host=github&repo=rashidi/spring-boot-tc-mysql)](https://dependabot.com)

Avoid running different databases between integration tests and production.

## Background
In general, we tend to use [H2][2] to perform integration tests within the application. However there are scenarios 
where H2 may not give the same outcome as our actual database, such as MySQL. Such scenario is when you have a table 
column called _rank_ or _order_.

Both names are allowed with H2 database but not with MySQL as those are reserved keywords. Therefore it is best to 
use the same database, in production environment, for our integration tests.

In this guide, we will implement [MySQL Container][3], from [TestContainers][1], with [Spring Boot][4].

## Dependencies
Full dependencies can be found in [pom.xml][5].

### Database
- `spring-boot-starter-data-jpa`
- `spring-boot-starter-data-rest`
- `mysql-connector-java`

### Integration tests
- `junit-jupiter` from TestContainers
- `mysql` from TestContainers

## Implementation

### Entity Class
Given we have a class called [Book][6] along with its repository class, [BookRepository][7].

```java
@Data
@Entity
public class Book {

    @Id
    @GeneratedValue
    private Long id;

    @Embedded
    private Author author;

    private String title;

}
```

```java
public interface BookRepository extends JpaRepository<Book, Long> {
}
```

### Test Implementation
Here we will be utilizing MySQL module from TestContainers to perform integration tests. The following implementation 
can be found in [BookRepositoryRestResourceTests][10]

#### Enable TestContainers
`org.testcontainers:junit-jupiter` dependency simplifies our implementation whereby the dependency will handle the  
start and stop of the container.

We will start by declaring our container of choice, i.e. MySQL version 8, with database name `demo`. 

```java
@Testcontainers
@SpringBootTest(properties = "spring.jpa.generate-ddl=true")
public class BookRepositoryRestResourceTests {

    @Container
    private static final MySQLContainer<?> datasource = new MySQLContainer<>(IMAGE + ":8").withDatabaseName("demo");
}
```

Next is to inform `@SpringBootTest` that we will be using `ContainerDatabaseDriver` as our driver class  
along with our JDBC URL

```java
@Testcontainers
@Testcontainers
@SpringBootTest(
        properties = {
                "spring.jpa.generate-ddl=true",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "spring.datasource.url=jdbc:tc:mysql:8:///demo"
        }
)
public class BookRepositoryRestResourceTests {

    @Container
    private static final MySQLContainer<?> datasource = new MySQLContainer<>(IMAGE + ":8").withDatabaseName("demo");
}
```

We will trigger a REST call to create a Book and given that there is a database running, the book should be created.

```java
@Testcontainers
@SpringBootTest(
        properties = {
                "spring.jpa.generate-ddl=true",
                "spring.datasource.driver-class-name=org.testcontainers.jdbc.ContainerDatabaseDriver",
                "spring.datasource.url=jdbc:tc:mysql:8:///demo"
        },
        webEnvironment = RANDOM_PORT
)
public class BookRepositoryRestResourceTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    private static final MySQLContainer<?> datasource = new MySQLContainer<>(IMAGE + ":8").withDatabaseName("demo");

    @Test
    @DisplayName("Entity will be created if datasource is available")
    void create() {
        var author = author();

        var book = book(author);

        ResponseEntity<Book> response = restTemplate.postForEntity("/books", book, Book.class);

        assertThat(response.getStatusCode()).isEqualTo(CREATED);
    }

    private Author author() {
        var author = new Author();

        author.setName("Rudyard Kipling");

        return author;
    }

    private Book book(final Author author) {
        var book = new Book();

        book.setAuthor(author);
        book.setTitle("The Jungle Book");

        return book;
    }

}
```

Execute the test and you will get HTTP `200` or `CREATED` returned. To be certain that our test did run with 
MySQL Container, we should see the following content in the logs:

```shell script
DEBUG 🐳 [mysql:8] - Starting container: mysql:8
...
org.hibernate.dialect.Dialect            : HHH000400: Using dialect: org.hibernate.dialect.MySQL8Dialect
```

This is how the application informing us that it is using MySQL Container which lead to Spring Boot automatically 
configure our dialect to `MySQL8Dialect`.

### Verify MySQL availability
Another option is to verify that our application will connect to MySQL by triggering a check against 
[Spring Boot Actuator Health][11] endpoint.

```java
public class DatasourceHealthTests {

    @Test
    @DisplayName("Database status will be UP and Database name should be MySQL")
    void databaseIsAvailable() throws JsonProcessingException {
        var response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getBody()).isNotNull();

        JsonNode root = new ObjectMapper().readTree(response.getBody());
        JsonNode dbComponentNode = root.get("components").get("db");

        String dbStatus = dbComponentNode.get("status").asText();
        String dbName = dbComponentNode.get("details").get("database").asText();

        assertThat(dbStatus).isEqualTo("UP");
        assertThat(dbName).isEqualTo("MySQL");
    }

}
```

Test above verifies that there's a running MySQL database connected to the application. Full implementation can be 
found in [DatasourceHealthTests][12].

### Conclusion
Now that we are running the same database as production environment, we can expect more accurate results from our
integration tests.

[1]: https://www.testcontainers.org/
[2]: https://www.h2database.com/html/main.html
[3]: https://www.testcontainers.org/modules/databases/mysql/
[4]: https://spring.io/projects/spring-boot
[5]: pom.xml
[6]: src/main/java/scratches/tc/domain/Book.java
[7]: src/main/java/scratches/tc/domain/BookRepository.java
[9]: https://docs.spring.io/spring-framework/docs/5.2.5.RELEASE/spring-framework-reference/testing.html#testcontext-ctx-management-dynamic-property-sources
[10]: src/test/java/scratches/tc/domain/BookRepositoryRestResourceTests.java
[11]: https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-health
[12]: src/test/java/scratches/tc/health/DatasourceHealthTests.java
