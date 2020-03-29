# Spring Boot: MySQL Container Integration
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
Here we will be utilizing MySQL module from TestContainers to perform integration tests.

#### Own container class
This is optional, however it will make it easier if in future we want to switch to another container without involving
too many changes.

We will create [DatasourceContainer][8] which `extends` `MySQLContainer`.

```java
public class DatasourceContainer extends MySQLContainer<DatasourceContainer> {

    private static final String IMAGE = "mysql";
    private static final String DEFAULT_TAG = "8";

    private static final String IMAGE_NAME = String.format("%s:%s", IMAGE, DEFAULT_TAG);

    public DatasourceContainer() {
        super(IMAGE_NAME);
    }

}
```

Our container will be running MySQL version 8.

#### Enable TestContainers
`org.testcontainers:junit-jupiter` dependency simplifies our implementation whereby the dependency will handle the  
start and stop of the container.

```java
@Testcontainers
@SpringBootTest(properties = "spring.jpa.generate-ddl=true")
public class BookRepositoryRestResourceTests {

    @Container
    private static final DatasourceContainer datasource = new DatasourceContainer().withDatabaseName("demo");
}
```

Now that we have a MySQL container setup, we will proceed to assigning its property via
[Spring's DynamicPropertySource][9].

```java
@Testcontainers
@SpringBootTest(properties = "spring.jpa.generate-ddl=true")
public class BookRepositoryRestResourceTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    private static final DatasourceContainer datasource = new DatasourceContainer().withDatabaseName("demo");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", datasource::getJdbcUrl);
        registry.add("spring.datasource.username", datasource::getUsername);
        registry.add("spring.datasource.password", datasource::getPassword);
    }
}
```

With `@DynamicPropertySource` we will be able to avoid hard-coding the database properties. Now that we have setup all
database related modules, let's write our integration tests.

We will trigger a REST call to create a Book and given that there is a database running, the book should be created.

```java
@Testcontainers
@SpringBootTest(properties = "spring.jpa.generate-ddl=true", webEnvironment = RANDOM_PORT)
public class BookRepositoryRestResourceTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Container
    private static final DatasourceContainer datasource = new DatasourceContainer().withDatabaseName("demo");

    @DynamicPropertySource
    static void datasourceProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", datasource::getJdbcUrl);
        registry.add("spring.datasource.username", datasource::getUsername);
        registry.add("spring.datasource.password", datasource::getPassword);
    }

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
[8]: src/test/java/scratches/tc/configuration/DatasourceContainer.java
[9]: https://docs.spring.io/spring-framework/docs/5.2.5.RELEASE/spring-framework-reference/testing.html#testcontext-ctx-management-dynamic-property-sources
